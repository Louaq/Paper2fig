"""Figure 1 / teaser detection and region rendering.

Why not just extract embedded images? A paper's main figure is usually a
composite of vector lines, arrows, text labels and several raster sub-images.
Extracting the image XObjects alone loses the arrows and labels, so we work on
the *page layout* and render the region with PyMuPDF instead.

Pipeline inside this module:

    locate(page)  -> candidate regions (rect + score + diagnostics)
    choose(...)   -> best region, pdf-aware quality flags
    render(...)   -> RGB JPEG at the configured DPI
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

import pymupdf
from PIL import Image

from . import ALGO_VERSION
from .util import log

CAPTION_RE = re.compile(r"^\s*(?:figure|fig\.?)\s*([0-9]{1,3})\s*[.:]?", re.I)
BODY_MIN_CHARS = 60
INK_THRESHOLD = 245


@dataclass
class Element:
    kind: str  # text | drawing | image
    rect: pymupdf.Rect
    text: str = ""
    font_size: float = 0.0
    is_body: bool = False
    is_caption: bool = False

    @property
    def area(self) -> float:
        return max(0.0, self.rect.width) * max(0.0, self.rect.height)


@dataclass
class Candidate:
    page: int
    rect: pymupdf.Rect
    score: float
    caption_rect: pymupdf.Rect | None
    evidence: dict[str, Any] = field(default_factory=dict)


@dataclass
class CropResult:
    page: int
    rect: tuple[float, float, float, float]
    dpi: int
    width: int
    height: int
    confidence: float
    scores: dict[str, float]
    flags: list[str]
    crop_path: Path | None = None
    algorithm: str = ALGO_VERSION
    diagnostics: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "page": self.page,
            "rect": list(self.rect),
            "dpi": self.dpi,
            "width": self.width,
            "height": self.height,
            "confidence": round(self.confidence, 3),
            "scores": {k: round(v, 3) for k, v in self.scores.items()},
            "flags": self.flags,
            "algorithm": self.algorithm,
            "crop_path": str(self.crop_path) if self.crop_path else None,
            "diagnostics": self.diagnostics,
        }


# ---------------------------------------------------------------------------
# page model
# ---------------------------------------------------------------------------

def _page_elements(page: pymupdf.Page) -> list[Element]:
    elements: list[Element] = []
    try:
        text_dict = page.get_text("dict")
    except Exception:  # pragma: no cover - malformed PDF
        text_dict = {"blocks": []}
    for block in text_dict.get("blocks", []):
        if block.get("type") != 0:
            continue
        lines = block.get("lines") or []
        text = " ".join(
            "".join(span.get("text", "") for span in line.get("spans", []))
            for line in lines
        )
        text = re.sub(r"\s+", " ", text).strip()
        if not text:
            continue
        sizes = [span.get("size", 0) for line in lines for span in line.get("spans", [])]
        rect = pymupdf.Rect(block["bbox"])
        elements.append(
            Element("text", rect, text=text, font_size=max(sizes) if sizes else 0.0)
        )
    try:
        for info in page.get_image_info():
            rect = pymupdf.Rect(info.get("bbox", (0, 0, 0, 0)))
            if rect.width > 2 and rect.height > 2:
                elements.append(Element("image", rect))
    except Exception:
        pass
    try:
        for drawing in page.get_drawings():
            rect = pymupdf.Rect(drawing.get("rect") or (0, 0, 0, 0))
            # skip hairline rules and degenerate paths
            if rect.width > 1.5 and rect.height > 1.5:
                elements.append(Element("drawing", rect))
    except Exception:
        pass
    return elements


def _body_font_size(text_elements: list[Element]) -> float:
    sizes: list[float] = []
    for el in text_elements:
        if len(el.text) >= BODY_MIN_CHARS:
            sizes.extend([el.font_size] * max(1, len(el.text) // 60))
    if not sizes:
        sizes = [el.font_size for el in text_elements if el.font_size]
    if not sizes:
        return 10.0
    sizes.sort()
    return sizes[len(sizes) // 2]


def _mark_body(elements: list[Element], page: pymupdf.Page, body_size: float) -> None:
    """A paragraph block: wide, long, body-sized, and not a caption."""
    for el in elements:
        if el.kind != "text":
            continue
        el.is_caption = bool(CAPTION_RE.match(el.text))
        wide = el.rect.width > 0.30 * page.rect.width
        long = len(el.text) >= BODY_MIN_CHARS
        # 图注/标题之外的长段文字一律视为正文：宁可少裁一点，也不要把摘要裁进图里
        bodyish = abs(el.font_size - body_size) <= max(1.6, body_size * 0.28)
        el.is_body = bool(wide and long and not el.is_caption and (bodyish or len(el.text) >= 180))


def _columns(elements: list[Element], page: pymupdf.Page) -> dict[str, float]:
    """Estimate content margins and the two-column gutter (if any)."""
    text = [el for el in elements if el.kind == "text" and el.is_body]
    if not text:
        text = [el for el in elements if el.kind == "text"]
    if not text:
        return {"left": 0, "right": page.rect.width, "gutter": page.rect.width / 2, "two_col": 0}
    left = min(el.rect.x0 for el in text)
    right = max(el.rect.x1 for el in text)
    widths = sorted(el.rect.width for el in text)
    median = widths[len(widths) // 2]
    two_col = 1 if (right - left) > 0 and median < 0.62 * (right - left) else 0
    mid = (left + right) / 2
    gutter = mid
    left_blocks = [el for el in text if el.rect.x1 <= mid + 2]
    right_blocks = [el for el in text if el.rect.x0 >= mid - 2]
    if left_blocks and right_blocks:
        gutter = (max(el.rect.x1 for el in left_blocks) + min(el.rect.x0 for el in right_blocks)) / 2
    return {"left": left, "right": right, "gutter": gutter, "two_col": two_col}


def _find_captions(elements: list[Element], page: pymupdf.Page) -> list[Element]:
    caps = [el for el in elements if el.kind == "text" and el.is_caption]
    # keep only caption "1" (the main figure), and merge a bare "Figure 1:" label
    out = []
    for cap in caps:
        m = CAPTION_RE.match(cap.text)
        if not m or m.group(1) not in {None, "1"}:
            continue
        if len(cap.text) < 18:
            # caption split into a label block + text block: merge downward
            below = [
                el
                for el in elements
                if el.kind == "text"
                and el is not cap
                and 0 <= el.rect.y0 - cap.rect.y1 < 26
                and abs(el.rect.x0 - cap.rect.x0) < 30
            ]
            below.sort(key=lambda e: e.rect.y0)
            if below:
                merged = pymupdf.Rect(cap.rect)
                for el in below[:1]:
                    merged |= el.rect
                cap.rect = merged
        out.append(cap)
    return out


def _in_window(el: Element, x0: float, x1: float, tolerance: float = 4.0) -> bool:
    return el.rect.x1 >= x0 - tolerance and el.rect.x0 <= x1 + tolerance


def _band(
    elements: list[Element],
    page: pymupdf.Page,
    caption: Element,
    columns: dict[str, float],
    above: bool,
) -> tuple[pymupdf.Rect | None, list[Element], int]:
    """Walk away from the caption until body text stops us.

    Returns (union rect, elements used, number of body blocks that stopped us).
    """
    graphic = [el for el in elements if el.kind in {"drawing", "image"}]
    full_width = _spans_columns(graphic, page, caption, columns)
    if full_width:
        x0, x1 = columns["left"], columns["right"]
    else:
        mid = (columns["left"] + columns["right"]) / 2
        if caption.rect.x1 <= columns["gutter"] + 2:
            x0, x1 = columns["left"], columns["gutter"]
        elif caption.rect.x0 >= columns["gutter"] - 2:
            x0, x1 = columns["gutter"], columns["right"]
        else:
            x0, x1 = columns["left"], columns["right"]

    lower = caption.rect.y1 if above else page.rect.y0
    upper = page.rect.y0 if above else caption.rect.y1
    # candidate elements between caption and the page edge
    pool = []
    for el in elements:
        if el is caption or not _in_window(el, x0, x1):
            continue
        if above:
            if el.rect.y1 <= caption.rect.y0 + 2 and el.rect.y0 >= page.rect.y0 + 2:
                pool.append(el)
        else:
            if el.rect.y0 >= caption.rect.y1 - 2 and el.rect.y1 <= page.rect.y1 - 2:
                pool.append(el)

    pool.sort(key=lambda e: e.rect.y1 if above else e.rect.y0, reverse=above)
    used: list[Element] = []
    stops = 0
    limit_y = caption.rect.y0 if above else caption.rect.y1
    for el in pool:
        gap = (limit_y - el.rect.y1) if above else (el.rect.y0 - limit_y)
        if el.is_body:
            # a paragraph always ends the figure, even when it touches it
            stops += 1
            break
        if el.is_caption:
            # next figure's caption: never merge two figures into one crop
            break
        if el.kind == "text" and not el.is_body and not el.is_caption:
            # 只收短标签（轴刻度、模块名、图例）。长句多半是摘要/正文，
            # 一旦把它算进外接矩形，整段文字就会被裁进图里。
            if len(el.text) <= 60:
                used.append(el)
        elif el.kind in {"drawing", "image"}:
            used.append(el)
        if gap > (page.rect.height * 0.85):
            break
        limit_y = el.rect.y0 if above else el.rect.y1

    if not used:
        return None, [], stops
    union = pymupdf.Rect(used[0].rect)
    for el in used[1:]:
        union |= el.rect
    return union, used, stops


def _spans_columns(graphic: Iterable[Element], page: pymupdf.Page, caption: Element, columns: dict[str, float]) -> bool:
    if caption.rect.width > 0.6 * (columns["right"] - columns["left"]):
        return True
    for el in graphic:
        if el.rect.width > 0.55 * (columns["right"] - columns["left"]):
            return True
        if el.rect.x0 < columns["gutter"] < el.rect.x1:
            return True
    return False


# ---------------------------------------------------------------------------
# locating candidates
# ---------------------------------------------------------------------------

def locate(page: pymupdf.Page, page_index: int, include_caption: bool = False) -> list[Candidate]:
    elements = _page_elements(page)
    body_size = _body_font_size([el for el in elements if el.kind == "text"])
    _mark_body(elements, page, body_size)
    columns = _columns(elements, page)
    captions = _find_captions(elements, page)
    candidates: list[Candidate] = []

    for caption in captions:
        for above in (True, False):
            rect, used, stops = _band(elements, page, caption, columns, above)
            if rect is None:
                continue
            if include_caption:
                rect |= caption.rect
            rect = _clamp(rect, page.rect)
            if rect.width < 60 or rect.height < 40:
                continue
            n_draw = sum(1 for el in used if el.kind == "drawing")
            n_img = sum(1 for el in used if el.kind == "image")
            n_label = sum(1 for el in used if el.kind == "text")
            body_chars = sum(len(el.text) for el in elements if el.is_body and rect.contains(el.rect))
            score = _score(rect, page, n_draw, n_img, n_label, stops, caption, above, body_chars)
            candidates.append(
                Candidate(
                    page=page_index,
                    rect=rect,
                    score=score,
                    caption_rect=caption.rect,
                    evidence={
                        "side": "above" if above else "below",
                        "drawings": n_draw,
                        "images": n_img,
                        "labels": n_label,
                        "body_stops": stops,
                        "caption": caption.text[:160],
                    },
                )
            )

    if not candidates:
        # no caption: fall back to the largest graphic cluster on the page
        graphic = [el for el in elements if el.kind in {"drawing", "image"}]
        if graphic:
            union = pymupdf.Rect(graphic[0].rect)
            for el in graphic[1:]:
                union |= el.rect
            union = _clamp(union, page.rect)
            if union.width > 80 and union.height > 50:
                candidates.append(
                    Candidate(
                        page=page_index,
                        rect=union,
                        score=0.25,
                        caption_rect=None,
                        evidence={"side": "fallback", "drawings": len(graphic), "images": 0, "labels": 0},
                    )
                )
    return candidates


def _score(rect, page, n_draw, n_img, n_label, stops, caption, above, body_chars: int = 0) -> float:
    pw, ph = page.rect.width, page.rect.height
    area_frac = (rect.width * rect.height) / (pw * ph)
    aspect = rect.width / max(rect.height, 1)
    s = 0.34  # caption found
    s += 0.14 * min(1.0, math.log1p(n_draw) / 3.0)
    s += 0.10 * min(1.0, math.log1p(n_img) / 2.0)
    s += 0.12 * min(1.0, n_label / 6.0)
    s += 0.10 if 0.10 <= area_frac <= 0.75 else (0.04 if area_frac <= 0.9 else 0.0)
    s += 0.08 if 0.45 <= aspect <= 2.8 else (0.03 if aspect <= 3.6 else 0.0)
    s -= 0.12 * min(stops, 2)
    if n_draw + n_img == 0:
        s -= 0.30  # prose only: almost never a Figure 1
    if (n_draw + n_img) > 0 and n_label > 12 * max(1, n_draw + n_img):
        s -= 0.10  # label soup
    # 区域里混进整段正文（摘要/引言）是裁图最常见的失败模式
    s -= min(0.55, body_chars / 1200.0)
    s += 0.04 if (caption.rect.y0 > ph * 0.35 if above else caption.rect.y1 < ph * 0.4) else 0.0
    return max(0.0, min(1.0, s))


def _clamp(rect: pymupdf.Rect, page_rect: pymupdf.Rect) -> pymupdf.Rect:
    r = pymupdf.Rect(rect)
    r.x0 = max(r.x0, page_rect.x0)
    r.y0 = max(r.y0, page_rect.y0)
    r.x1 = min(r.x1, page_rect.x1)
    r.y1 = min(r.y1, page_rect.y1)
    return r


# ---------------------------------------------------------------------------
# ink trimming + rendering
# ---------------------------------------------------------------------------

def _gray(page: pymupdf.Page, rect: pymupdf.Rect, dpi: int = 72):
    pix = page.get_pixmap(clip=rect, dpi=dpi, colorspace=pymupdf.csGRAY, alpha=False)
    img = Image.frombytes("L", (pix.width, pix.height), pix.samples)
    return img


def _ink_bbox(img: Image.Image, threshold: int = INK_THRESHOLD) -> tuple[int, int, int, int] | None:
    import numpy as np

    arr = np.asarray(img)
    mask = arr < threshold
    if not mask.any():
        return None
    rows = mask.any(axis=1)
    cols = mask.any(axis=0)
    y0, y1 = int(rows.argmax()), int(len(rows) - rows[::-1].argmax())
    x0, x1 = int(cols.argmax()), int(len(cols) - cols[::-1].argmax())
    return x0, y0, x1, y1


def _trim(page: pymupdf.Page, rect: pymupdf.Rect, pad: float = 4.0) -> tuple[pymupdf.Rect, dict[str, float]]:
    img = _gray(page, rect)
    box = _ink_bbox(img)
    if not box:
        return rect, {"ink": 0.0, "edge": 0.0}
    import numpy as np

    arr = np.asarray(img)
    ink_ratio = float((arr < INK_THRESHOLD).mean())
    dark_ratio = float((arr < 160).mean())
    edge = _edge_ink(arr)
    sx = rect.width / img.width
    sy = rect.height / img.height
    x0 = rect.x0 + box[0] * sx - pad
    y0 = rect.y0 + box[1] * sy - pad
    x1 = rect.x0 + box[2] * sx + pad
    y1 = rect.y0 + box[3] * sy + pad
    return _clamp(pymupdf.Rect(x0, y0, x1, y1), rect), {"ink": ink_ratio, "dark": dark_ratio, "edge": edge}


def _edge_ink(arr) -> float:
    import numpy as np

    h, w = arr.shape
    band = 3  # only the outermost pixels matter: ink there means we cut content
    edges = [
        arr[:band, :],
        arr[-band:, :],
        arr[:, :band],
        arr[:, -band:],
    ]
    vals = [float((e < INK_THRESHOLD).mean()) for e in edges]
    return max(vals)


def _flags(rect: pymupdf.Rect, page: pymupdf.Page, elements: list[Element], metrics: dict[str, float]) -> list[str]:
    flags: list[str] = []
    if metrics.get("ink", 0) < 0.008:
        flags.append("blank")
    if metrics.get("dark", 0) > 0.9:
        flags.append("too-dark")
    # body paragraphs swallowed by the crop
    for el in elements:
        if el.kind == "text" and el.is_body:
            inter = el.rect & rect
            if inter.get_area() > 0.6 * el.area:
                flags.append("body-text")
                break
    # graphics (arrows, sub-images) sliced by the crop boundary
    for el in elements:
        if el.kind not in {"drawing", "image"}:
            continue
        inter = el.rect & rect
        if inter.get_area() > 0:
            covered = inter.get_area() / max(el.area, 1e-6)
            if 0.08 < covered < 0.92 and el.rect.get_area() > 400:
                flags.append("graphic-cut")
                break
    # labels cut by the crop boundary
    for el in elements:
        if el.kind != "text" or el.is_body or el.is_caption:
            continue
        inter = el.rect & rect
        if inter.get_area() > 0:
            covered = inter.get_area() / max(el.area, 1e-6)
            if 0.15 < covered < 0.85:
                flags.append("label-cut")
                break
    if rect.width < 200 or rect.height < 60:
        flags.append("tiny")
    aspect = rect.width / max(rect.height, 1)
    if aspect > 5.5 or aspect < 0.2:
        flags.append("extreme-aspect")
    return flags


def render(page: pymupdf.Page, rect: pymupdf.Rect, out_path: Path, dpi: int, quality: int = 88) -> tuple[int, int]:
    pix = page.get_pixmap(clip=rect, dpi=dpi, colorspace=pymupdf.csRGB, alpha=False)
    img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path, "JPEG", quality=quality, optimize=True, progressive=True)
    return img.width, img.height


def crop_from_layout(
    pdf_path: Path,
    layout: dict[str, Any],
    out_path: Path,
    dpi: int = 200,
    include_caption: bool = False,
    jpeg_quality: int = 88,
) -> CropResult:
    """Render a Figure 1 region selected by the MinerU layout API."""
    doc = pymupdf.open(pdf_path)
    try:
        page = doc[int(layout["page"])]
        page_width, page_height = layout["page_size"]
        sx = page.rect.width / page_width
        sy = page.rect.height / page_height
        x0, y0, x1, y1 = layout["bbox"]
        rect = pymupdf.Rect(x0 * sx, y0 * sy, x1 * sx, y1 * sy)
        if include_caption and layout.get("caption_bbox"):
            cx0, cy0, cx1, cy1 = layout["caption_bbox"]
            rect |= pymupdf.Rect(cx0 * sx, cy0 * sy, cx1 * sx, cy1 * sy)
        rect = _clamp(rect, page.rect)

        elements = _page_elements(page)
        body_size = _body_font_size([el for el in elements if el.kind == "text"])
        _mark_body(elements, page, body_size)
        trimmed, metrics = _trim(page, rect)
        if trimmed.width < 40 or trimmed.height < 30:
            trimmed = rect
        flags = _flags(trimmed, page, elements, metrics)
        width, height = render(page, trimmed, out_path, dpi, jpeg_quality)
        confidence = 0.95 if layout.get("figure1") else 0.72
        return CropResult(
            page=int(layout["page"]),
            rect=tuple(round(v, 2) for v in trimmed),
            dpi=dpi,
            width=width,
            height=height,
            confidence=confidence,
            scores={"mineru": confidence, "ink": min(1.0, metrics.get("ink", 0) / 0.25)},
            flags=flags,
            crop_path=out_path,
            algorithm=ALGO_VERSION + "+mineru",
            diagnostics={
                "mineru": {
                    "page": layout.get("page"),
                    "bbox": layout.get("bbox"),
                    "caption": layout.get("caption", "")[:160],
                    "caption_bbox": layout.get("caption_bbox"),
                    "figure1": bool(layout.get("figure1")),
                },
                "metrics": {k: round(v, 4) for k, v in metrics.items()},
            },
        )
    finally:
        doc.close()


def crop_pdf(
    pdf_path: Path,
    out_path: Path,
    dpi: int = 200,
    max_pages: int = 6,
    include_caption: bool = False,
    jpeg_quality: int = 88,
) -> CropResult | None:
    """Locate the best Figure 1 region across the first pages and render it."""
    try:
        doc = pymupdf.open(pdf_path)
    except Exception as exc:
        log(f"crop: cannot open {pdf_path}: {exc}")
        return None

    best: tuple[Candidate, pymupdf.Page] | None = None
    all_candidates: list[dict[str, Any]] = []
    try:
        pages = len(doc) if max_pages <= 0 else min(len(doc), max_pages)
        for index in range(pages):
            page = doc[index]
            for cand in locate(page, index, include_caption):
                all_candidates.append({"page": index, "score": round(cand.score, 3), "rect": [round(v, 1) for v in cand.rect], "evidence": cand.evidence})
                if best is None or cand.score > best[0].score:
                    best = (cand, page)
        if best is None:
            log(f"crop: no figure candidate in {pdf_path.name}")
            return None

        cand, page = best
        elements = _page_elements(page)
        body_size = _body_font_size([el for el in elements if el.kind == "text"])
        _mark_body(elements, page, body_size)

        # 侧边栏正文与图并排时（CVPR 常见），外接矩形会把摘要也框进来。
        # 若正文覆盖了候选区域的相当一部分，就退回“图形元素的外接矩形”。
        body_area = sum((el.rect & cand.rect).get_area()
                        for el in elements if el.is_body and el.rect.intersects(cand.rect))
        if cand.rect.get_area() > 0 and body_area > 0.10 * cand.rect.get_area():
            graphics = [el.rect for el in elements
                        if el.kind in {"drawing", "image"} and cand.rect.intersects(el.rect)]
            if graphics:
                g = pymupdf.Rect(graphics[0])
                for r in graphics[1:]:
                    g |= r
                g = _clamp(pymupdf.Rect(g.x0 - 8, g.y0 - 8, g.x1 + 8, g.y1 + 8), cand.rect)
                if g.width >= 60 and g.height >= 40:
                    cand.rect = g

        trimmed, metrics = _trim(page, cand.rect)
        if trimmed.width < 40 or trimmed.height < 30:
            trimmed = cand.rect
        flags = _flags(trimmed, page, elements, metrics)
        width, height = render(page, trimmed, out_path, dpi, jpeg_quality)

        scores = {
            "caption": 1.0 if cand.caption_rect is not None else 0.3,
            "layout": cand.score,
            "ink": min(1.0, metrics.get("ink", 0) / 0.25),
            "evidence": min(1.0, (cand.evidence.get("drawings", 0) + 2 * cand.evidence.get("images", 0)) / 12),
        }
        confidence = (
            0.40 * scores["layout"]
            + 0.25 * scores["caption"]
            + 0.20 * scores["evidence"]
            + 0.15 * scores["ink"]
        )
        for flag in flags:
            if flag in {"body-text", "label-cut", "blank", "tiny"}:
                confidence *= 0.55
            elif flag in {"edge-ink", "too-dark", "extreme-aspect"}:
                confidence *= 0.8
        confidence = max(0.05, min(0.99, confidence))

        return CropResult(
            page=cand.page,
            rect=tuple(round(v, 2) for v in trimmed),
            dpi=dpi,
            width=width,
            height=height,
            confidence=confidence,
            scores=scores,
            flags=flags,
            crop_path=out_path,
            diagnostics={
                "candidate": cand.evidence,
                "metrics": {k: round(v, 4) for k, v in metrics.items()},
                "candidates": all_candidates[:12],
                "pages_scanned": pages,
            },
        )
    finally:
        doc.close()
