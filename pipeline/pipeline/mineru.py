"""MinerU precision-parse API: submit PDFs, collect figure blocks.

Only the first ``max_pages`` pages are uploaded.  The API result is reduced to
one small JSON per paper (page, bbox, caption) and cached under
``workspace/mineru``; images are never kept, they are rendered locally from the
original PDF.
"""

from __future__ import annotations

import io
import json
import os
import re
import time
import zipfile
from pathlib import Path
from typing import Any, Iterable

import pymupdf
import requests

from .config import Settings
from .util import log, progress

DEFAULT_BASE_URL = "https://mineru.net"
MAX_API_PAGES = 200
LAYOUT_VERSION = "2"
MIN_FIGURE_AREA = 0.02
FIGURE1 = re.compile(r"^\s*(?:figure|fig\.?)\s*1\s*[.:]?", re.I)


class MineruError(RuntimeError):
    pass


class MineruClient:
    def __init__(self, settings: Settings) -> None:
        self.base = str(
            os.environ.get("MINERU_BASE_URL")
            or settings.values.get("mineru_api_base")
            or DEFAULT_BASE_URL
        ).rstrip("/")
        self.token = str(
            os.environ.get("MINERU_TOKEN") or settings.values.get("mineru_api_token") or ""
        )
        if not self.token:
            raise MineruError("MINERU_TOKEN is not configured")
        self.model = str(
            os.environ.get("MINERU_MODEL") or settings.values.get("mineru_model") or "pipeline"
        )
        self.http = requests.Session()
        self.http.headers.update({
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/json",
        })

    def submit(self, files: list[Path], data_ids: list[str], page_ranges: list[str]) -> str:
        payload = {
            "files": [
                {"name": path.name, "data_id": data_id, "page_ranges": ranges, "is_ocr": False}
                for path, data_id, ranges in zip(files, data_ids, page_ranges)
            ],
            "model_version": self.model,
            "enable_formula": False,
            "enable_table": False,
        }
        response = self.http.post(
            f"{self.base}/api/v4/file-urls/batch", json=payload, timeout=120
        )
        body = response.json()
        if body.get("code") != 0:
            raise MineruError(f"submit failed: {body.get('msg')}")
        batch_id = body["data"]["batch_id"]
        for path, url in zip(files, body["data"]["file_urls"]):
            with path.open("rb") as fh:
                upload = self.http.put(url, data=fh, timeout=600)
            if upload.status_code != 200:
                raise MineruError(f"upload {path.name} failed: HTTP {upload.status_code}")
        return batch_id

    def wait(self, batch_id: str, expected: set[str]) -> dict[str, dict[str, Any]]:
        pending = set(expected)
        done: dict[str, dict[str, Any]] = {}
        deadline = time.time() + 3 * 3600
        while pending:
            response = self.http.get(
                f"{self.base}/api/v4/extract-results/batch/{batch_id}", timeout=120
            )
            body = response.json()
            if body.get("code") != 0:
                raise MineruError(f"poll failed: {body.get('msg')}")
            for item in body["data"]["extract_result"]:
                pid = Path(item["file_name"]).stem
                if pid not in pending:
                    continue
                state = item["state"]
                if state == "failed":
                    raise MineruError(f"{pid}: {item.get('err_msg')}")
                if state == "done":
                    done[pid] = item
                    pending.remove(pid)
            if pending:
                time.sleep(5)
            if time.time() > deadline:
                raise MineruError(f"timeout waiting for batch {batch_id}")
        return done


def _slice_pdf(source: Path, target: Path, max_pages: int) -> int:
    doc = pymupdf.open(source)
    end = doc.page_count if max_pages <= 0 else min(doc.page_count, max_pages)
    end = min(end, MAX_API_PAGES)
    out = pymupdf.open()
    out.insert_pdf(doc, from_page=0, to_page=end - 1)
    target.parent.mkdir(parents=True, exist_ok=True)
    out.save(target)
    out.close()
    doc.close()
    return end


def _download_layout(zip_url: str) -> dict[str, Any]:
    response = requests.get(zip_url, timeout=600)
    response.raise_for_status()
    with zipfile.ZipFile(io.BytesIO(response.content)) as archive:
        name = next(n for n in archive.namelist() if n.endswith("layout.json"))
        return _parse_layout(json.loads(archive.read(name)))


def _parse_layout(data: dict[str, Any]) -> dict[str, Any]:
    pages = data.get("pdf_info") or data.get("pages") or []
    sizes = {
        int(page.get("source_page_idx", page.get("page_idx"))): [page["width_pt"], page["height_pt"]]
        for page in (data.get("extensions") or {}).get("docvortex_layout", {}).get("pages", [])
    }
    candidates: list[dict[str, Any]] = []
    text_captions: list[tuple[int, list[float], str]] = []
    for index, page in enumerate(pages):
        page_idx = int(page.get("page_idx", index))
        size = page.get("page_size")
        if not size and "width" in page:
            size = [page["width"], page["height"]]
        if not size:
            size = sizes[page_idx]
        blocks = page.get("para_blocks") or page.get("blocks") or []
        for block in _walk(blocks):
            if block.get("type") in ("text", "title") and block.get("bbox"):
                text = _text(block)
                if FIGURE1.match(text):
                    text_captions.append((page_idx, [float(v) for v in block["bbox"]], text))
                continue
            if block.get("type") != "image":
                continue
            bbox = _image_bbox(block)
            if not bbox:
                continue
            caption, caption_bbox = _caption(block)
            candidates.append({
                "page": page_idx,
                "bbox": [float(v) for v in bbox],
                "caption_bbox": [float(v) for v in caption_bbox] if caption_bbox else None,
                "caption": caption,
                "page_size": [float(size[0]), float(size[1])],
            })
    for candidate in candidates:
        if candidate["caption"]:
            continue
        bbox = candidate["bbox"]
        for cap_page, cap_bbox, cap_text in text_captions:
            if cap_page != candidate["page"]:
                continue
            gap = cap_bbox[1] - bbox[3]
            if -2 <= gap <= 120 and cap_bbox[2] >= bbox[0] and cap_bbox[0] <= bbox[2]:
                candidate["caption"] = cap_text
                candidate["caption_bbox"] = cap_bbox
                break
    if not candidates:
        raise MineruError("layout contains no image block")
    best = _choose_figure1(candidates)
    best["figure1"] = bool(FIGURE1.match(best["caption"]))
    return best


def _area(candidate: dict[str, Any]) -> float:
    x0, y0, x1, y1 = candidate["bbox"]
    return max(0.0, x1 - x0) * max(0.0, y1 - y0)


def _choose_figure1(candidates: list[dict[str, Any]]) -> dict[str, Any]:
    explicit = [c for c in candidates if FIGURE1.match(c["caption"])]
    if explicit:
        return max(explicit, key=_area)
    for page in sorted({c["page"] for c in candidates}):
        page_candidates = [c for c in candidates if c["page"] == page]
        width, height = page_candidates[0]["page_size"]
        large = [c for c in page_candidates if _area(c) >= MIN_FIGURE_AREA * width * height]
        if large:
            return max(large, key=_area)
    return max(candidates, key=_area)


def _children(block: dict[str, Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for key in ("blocks", "content"):
        value = block.get(key)
        if isinstance(value, list):
            out.extend(child for child in value if isinstance(child, dict))
    return out


def _walk(blocks: Iterable[dict[str, Any]]) -> Iterable[dict[str, Any]]:
    for block in blocks:
        yield block
        yield from _walk(_children(block))


def _image_bbox(block: dict[str, Any]) -> list[float] | None:
    for child in _children(block):
        if child.get("type") == "image_body" and child.get("bbox"):
            return child["bbox"]
    return block.get("bbox")


def _caption(block: dict[str, Any]) -> tuple[str, list[float] | None]:
    for child in _children(block):
        if child.get("type") == "image_caption":
            return _text(child), child.get("bbox")
    value = block.get("image_caption") or block.get("caption") or ""
    if isinstance(value, str):
        return value.strip(), None
    return " ".join(str(v) for v in value).strip(), None


def _text(node: dict[str, Any]) -> str:
    parts: list[str] = []
    for line in node.get("lines") or []:
        for span in line.get("spans") or []:
            parts.append(span.get("content", ""))
    content = node.get("content")
    if isinstance(content, str):
        parts.append(content)
    elif isinstance(content, list):
        parts.extend(_text(v) if isinstance(v, dict) else str(v) for v in content)
    for child in _children(node):
        parts.append(_text(child))
    return " ".join(p for p in parts if p).strip()


def prepare(
    settings: Settings,
    tasks: list[tuple[str, Path]],
    max_pages: int,
    batch_size: int,
    force: bool = False,
) -> dict[str, dict[str, Any]]:
    batch_size = min(batch_size, 50)
    model = str(
        os.environ.get("MINERU_MODEL") or settings.values.get("mineru_model") or "pipeline"
    )
    root = settings.workspace / "mineru"
    root.mkdir(parents=True, exist_ok=True)
    layouts: dict[str, dict[str, Any]] = {}
    pending: list[tuple[str, Path, Path]] = []
    for pid, pdf_path in tasks:
        cache = root / f"{pid}.{model}.{max_pages}.v{LAYOUT_VERSION}.json"
        if cache.is_file() and not force:
            layouts[pid] = json.loads(cache.read_text(encoding="utf-8"))
            continue
        pending.append((pid, pdf_path, cache))

    total = len(tasks)
    completed = len(layouts)
    if pending:
        client = MineruClient(settings)
    for start in range(0, len(pending), batch_size):
        chunk = pending[start:start + batch_size]
        sliced: list[Path] = []
        ranges: list[str] = []
        for pid, pdf_path, _ in chunk:
            target = root / f"{pid}.pdf"
            pages = _slice_pdf(pdf_path, target, max_pages)
            sliced.append(target)
            ranges.append(f"1-{pages}")
        batch_id = client.submit(sliced, [pid for pid, _, _ in chunk], ranges)
        done = client.wait(batch_id, {pid for pid, _, _ in chunk})
        failed = 0
        for pid, _, cache in chunk:
            (root / f"{pid}.pdf").unlink(missing_ok=True)
            try:
                layout = _download_layout(done[pid]["full_zip_url"])
            except MineruError as exc:
                failed += 1
                completed += 1
                progress(stage="mineru", done=completed, failed=failed, total=total)
                log(f"mineru: {pid} skipped: {exc}")
                continue
            cache.write_text(json.dumps(layout, ensure_ascii=False), encoding="utf-8")
            layouts[pid] = layout
            completed += 1
            progress(stage="mineru", done=completed, failed=failed, total=total)
            log(f"mineru: {pid} -> page {layout['page']} {layout['bbox']}")
    return layouts
