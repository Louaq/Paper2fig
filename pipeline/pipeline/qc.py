"""Image quality control: two independent gates + perceptual duplicate check.

Gate 1 - crop quality: is the picture technically usable (not blank, not cut,
not a wall of text, resolution high enough)?
Gate 2 - inclusion quality: does it look like a Figure 1 / framework / pipeline
we want in the gallery? That gate is mostly human; here we only produce a weak
prior from layout evidence (score) and let the reviewer decide.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
from PIL import Image

INK_THRESHOLD = 245
DARK_THRESHOLD = 160
BLOCKING = {
    "blank",
    "low-resolution",
    "too-much-whitespace",
    "text-wall",
    "body-text",
    "label-cut",
}


@dataclass
class QcResult:
    width: int
    height: int
    ink_ratio: float
    std: float
    border_ink: float
    blank_margin: float
    aspect: float
    dhash: int
    sig: list[float] = field(default_factory=list)
    issues: list[str] = field(default_factory=list)
    quality: float = 0.0
    ok: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "width": self.width,
            "height": self.height,
            "ink_ratio": round(self.ink_ratio, 4),
            "std": round(self.std, 2),
            "border_ink": round(self.border_ink, 4),
            "blank_margin": round(self.blank_margin, 4),
            "aspect": round(self.aspect, 3),
            "dhash": format(self.dhash, "064x"),
            "issues": self.issues,
            "quality": round(self.quality, 3),
            "ok": self.ok,
        }


def dhash(img: Image.Image, size: int = 16) -> int:
    small = img.convert("L").resize((size + 1, size), Image.Resampling.LANCZOS)
    arr = np.asarray(small, dtype=np.int16)
    diff = arr[:, 1:] > arr[:, :-1]
    value = 0
    for bit in diff.flatten():
        value = (value << 1) | int(bool(bit))
    return value


def hamming(a: int, b: int) -> int:
    return int(a ^ b).bit_count()


def signature(img: Image.Image, bins: int = 4) -> list[float]:
    """Coarse RGB histogram (bins^3), L1-normalised.

    A layout hash alone cannot tell two wide two-panel teasers apart, so a
    duplicate also has to look the same colour-wise.
    """
    small = img.convert("RGB").resize((96, 96), Image.Resampling.BILINEAR)
    arr = np.asarray(small, dtype=np.int32) * bins // 256
    idx = (arr[:, :, 0] * bins + arr[:, :, 1]) * bins + arr[:, :, 2]
    hist = np.bincount(idx.flatten(), minlength=bins ** 3).astype(np.float32)
    hist /= max(hist.sum(), 1)
    return [round(float(v), 4) for v in hist]


def cosine(a: list[float], b: list[float]) -> float:
    import math

    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    if not na or not nb:
        return 0.0
    return dot / (na * nb)


def analyze(path: Path, settings) -> QcResult:
    with Image.open(path) as img:
        img.load()
        width, height = img.size
        rgb = img.convert("RGB")
    gray = np.asarray(rgb.convert("L"), dtype=np.uint8)
    ink = gray < INK_THRESHOLD      # any non-white pixel (colored backgrounds count)
    dark = gray < DARK_THRESHOLD    # text / dark artwork only
    ink_ratio = float(ink.mean())
    dark_ratio = float(dark.mean())
    std = float(gray.std())
    aspect = width / max(height, 1)

    # blank margin: fraction of the four 3% borders without ink
    h, w = gray.shape
    band = max(1, int(round(min(h, w) * 0.0025)))
    borders = [ink[:band, :], ink[-band:, :], ink[:, :band], ink[:, -band:]]
    border_ink = max(float(b.mean()) for b in borders)

    # how much of the image is empty margin (proxy for over-padding)
    rows = np.where(ink.any(axis=1))[0]
    cols = np.where(ink.any(axis=0))[0]
    if len(rows) and len(cols):
        used = (rows[-1] - rows[0] + 1) * (cols[-1] - cols[0] + 1)
        blank_margin = 1.0 - used / float(h * w)
    else:
        blank_margin = 1.0

    issues: list[str] = []
    if width < settings.min_width or height < settings.min_height:
        issues.append("low-resolution")
    if ink_ratio < settings.min_ink_ratio or std < 6:
        issues.append("blank")
    if dark_ratio > settings.max_ink_ratio:
        issues.append("text-wall")
    if blank_margin > settings.max_whitespace:
        issues.append("too-much-whitespace")
    if aspect > 5.5 or aspect < 0.2:
        issues.append("extreme-aspect")

    # soft quality score, independent from the reviewer's decision
    q = 0.0
    q += 0.30 * _band_score(ink_ratio, 0.05, 0.35, 0.02, 0.85)
    q += 0.20 * _band_score(std / 64.0, 0.15, 0.9, 0.05, 1.0)
    q += 0.15 * _band_score(aspect, 0.6, 2.6, 0.2, 5.5)
    q += 0.15 * _band_score(blank_margin, 0.0, 0.25, 0.0, 0.55, invert=True)
    q += 0.20 * _band_score(min(width, height), 700, 1400, 320, 2000)
    q = max(0.0, min(1.0, q))

    ok = not any(i in BLOCKING for i in issues)
    return QcResult(
        width=width,
        height=height,
        ink_ratio=ink_ratio,
        std=std,
        border_ink=border_ink,
        blank_margin=blank_margin,
        aspect=aspect,
        dhash=dhash(rgb, 8),
        sig=signature(rgb),
        issues=issues,
        quality=q,
        ok=ok,
    )


def _band_score(value: float, lo: float, hi: float, floor: float, ceil: float, invert: bool = False) -> float:
    if invert:
        if value <= lo:
            return 1.0
        if value >= hi:
            return 0.0
        return 1.0 - (value - lo) / (hi - lo)
    if value <= lo:
        return max(0.0, min(1.0, (value - floor) / max(lo - floor, 1e-6)))
    if value >= hi:
        return max(0.0, min(1.0, (ceil - value) / max(ceil - hi, 1e-6)))
    return 1.0


def find_duplicate(
    phash: int,
    sig: list[float],
    aspect: float,
    known: list[dict],
    threshold: int,
    min_cosine: float = 0.90,
) -> tuple[str, int] | None:
    """A duplicate needs BOTH a close layout hash and a close colour signature."""
    best: tuple[str, int] | None = None
    for item in known:
        if not item.get("sig"):
            continue
        dist = hamming(phash, int(item["h"], 16))
        if dist > threshold:
            continue
        if item.get("a") and abs(aspect - float(item["a"])) / max(aspect, 1e-6) > 0.2:
            continue
        if cosine(sig, item["sig"]) < min_cosine:
            continue
        if best is None or dist < best[1]:
            best = (item["id"], dist)
    return best


def phash_of(path: Path) -> dict:
    with Image.open(path) as img:
        rgb = img.convert("RGB")
        return {
            "h": format(dhash(rgb, 8), "016x"),
            "sig": signature(rgb),
            "a": round(rgb.width / max(rgb.height, 1), 3),
        }
