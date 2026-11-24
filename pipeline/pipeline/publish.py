"""Publish reviewed crops into the static gallery.

The gallery stays a static site: this module writes

    <site>/images/<venue>/final/<id>.jpg
    <site>/data/figures.json          (authoritative list)
    <site>/assets/figures.js          (generated from figures.json)

as one atomic batch, after validating the whole batch. A failed or empty batch
never replaces the live list.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from PIL import Image

from .util import log

ID_RE = re.compile(r"^[a-z]+[0-9]{4}-[A-Za-z0-9._-]+$")
REQUIRED = ("id", "venue", "year", "title", "authors", "paper", "image_path")
VALID_PATTERNS = {
    "teaser",
    "conceptual",
    "framework",
    "pipeline",
    "architecture",
    "taxonomy",
    "results",
    "comparison",
}


@dataclass
class PublishPlan:
    site: Path
    items: list[dict[str, Any]]
    existing: list[dict[str, Any]]
    merged: list[dict[str, Any]] = field(default_factory=list)
    added: list[str] = field(default_factory=list)
    updated: list[str] = field(default_factory=list)
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def summary(self) -> dict[str, Any]:
        return {
            "ok": not self.errors,
            "site": str(self.site),
            "incoming": len(self.items),
            "existing": len(self.existing),
            "total_after": len(self.merged) or len(self.existing),
            "added": len(self.added),
            "updated": len(self.updated),
            "errors": self.errors,
            "warnings": self.warnings[:20],
        }


def load_figures(site: Path) -> list[dict[str, Any]]:
    path = site / "data" / "figures.json"
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(data, dict) and "figures" in data:
        data = data["figures"]
    if not isinstance(data, list):
        raise ValueError(f"{path} is not a list")
    return data


def plan(site: Path, incoming: list[dict[str, Any]], allow_removals: int = 0) -> PublishPlan:
    existing = load_figures(site)
    p = PublishPlan(site=site, items=incoming, existing=existing)

    seen: set[str] = set()
    prepared: list[dict[str, Any]] = []
    for raw in incoming:
        missing = [k for k in REQUIRED if raw.get(k) in (None, "", [])]
        if missing:
            p.errors.append(f"{raw.get('id', '?')}: missing {','.join(missing)}")
            continue
        pid = str(raw["id"])
        if not ID_RE.match(pid):
            p.errors.append(f"{pid}: invalid id format")
            continue
        if pid in seen:
            p.errors.append(f"{pid}: duplicate id in batch")
            continue
        seen.add(pid)
        venue = str(raw["venue"]).lower()
        if venue not in {"iclr", "icml", "neurips", "cvpr", "acl", "aaai",
                         "eccv", "miccai", "ijcai", "icme", "mm"}:
            p.errors.append(f"{pid}: unknown venue {venue}")
            continue
        src = Path(raw["image_path"])
        if not src.is_file():
            p.errors.append(f"{pid}: image not found {src}")
            continue
        try:
            with Image.open(src) as img:
                img.verify()
            with Image.open(src) as img:
                w, h = img.size
        except Exception as exc:
            p.errors.append(f"{pid}: unreadable image ({exc})")
            continue
        if w < 200 or h < 100:
            p.errors.append(f"{pid}: image too small {w}x{h}")
            continue
        pattern = raw.get("pattern") or "teaser"
        if pattern not in VALID_PATTERNS:
            p.warnings.append(f"{pid}: pattern {pattern} reset to teaser")
            pattern = "teaser"
        record = {
            "id": pid,
            "venue": venue,
            "year": int(raw["year"]),
            "title": str(raw["title"]).strip(),
            "authors": [str(a).strip() for a in raw["authors"] if str(a).strip()],
            "pattern": pattern,
            "image": f"images/{venue}/final/{pid}.jpg",
            "paper": str(raw["paper"]),
            "pdf_source": str(raw.get("pdf_source") or raw["paper"]),
            "score": float(raw.get("score") or 0),
            "w": w,
            "h": h,
        }
        if raw.get("tier") in {"oral", "spotlight"}:
            record["tier"] = raw["tier"]
        if raw.get("award") in {"best", "honorable"}:
            record["award"] = raw["award"]
        prepared.append(record)

    if p.errors:
        return p

    merged = list(existing)
    index = {item.get("id"): i for i, item in enumerate(merged)}
    for record in prepared:
        if record["id"] in index:
            merged[index[record["id"]]] = record
            p.updated.append(record["id"])
        else:
            merged.append(record)
            p.added.append(record["id"])

    if len(merged) < len(existing) - allow_removals:
        p.errors.append(
            f"refusing to shrink the gallery: {len(existing)} -> {len(merged)}"
        )
        return p
    if not merged:
        p.errors.append("refusing to publish an empty gallery")
        return p
    p.merged = merged
    return p


def apply(p: PublishPlan, backup_root: Path | None = None) -> dict[str, Any]:
    if p.errors:
        raise SystemExit("; ".join(p.errors))
    site = p.site
    # 自动备份已关闭（2026-11-24）：不再创建发布快照。
    backup = None

    # 1. copy images (temp file + rename, so the site never references a
    #    half-written image)
    copied = []
    for record in p.merged:
        if record["id"] not in set(p.added) | set(p.updated):
            continue
        item = next(i for i in p.items if i["id"] == record["id"])
        src = Path(item["image_path"])
        dst = site / record["image"]
        dst.parent.mkdir(parents=True, exist_ok=True)
        tmp = dst.with_suffix(".jpg.tmp")
        shutil.copy2(src, tmp)
        os.replace(tmp, dst)
        copied.append(record["image"])

    # 3. atomic JSON + JS
    figures_json = site / "data" / "figures.json"
    figures_json.parent.mkdir(parents=True, exist_ok=True)
    _atomic_write(figures_json, json.dumps(p.merged, ensure_ascii=False, indent=1) + "\n")
    _atomic_write(site / "assets" / "figures.js", _figures_js(p.merged))

    return {
        **p.summary(),
        "copied": len(copied),
        "backup": None,
        "total": len(p.merged),
        "generated_at": time.strftime("%Y-%m-%d %H:%M:%S"),
    }


def _figures_js(items: list[dict[str, Any]]) -> str:
    body = json.dumps(items, ensure_ascii=False, separators=(", ", ": "))
    return f"window.FIGURES = {body};\n"


def _atomic_write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def update_sitemap(site: Path, base_url: str | None = None) -> int:
    """Rewrite the figure fragment entries of sitemap.xml (optional step)."""
    if base_url is None:
        base_url = os.environ.get("PUBLIC_BASE_URL", "http://localhost:8080")
        base_url = base_url if base_url.endswith("/") else base_url + "/"
    path = site / "sitemap.xml"
    items = load_figures(site)
    if not path.is_file() or not items:
        return 0
    today = time.strftime("%Y-%m-%d")
    entries = [
        f"  <url>\n    <loc>{base_url}</loc>\n    <lastmod>{today}</lastmod>\n"
        f"    <changefreq>daily</changefreq>\n    <priority>1.0</priority>\n  </url>"
    ]
    for item in items:
        entries.append(
            f"  <url>\n    <loc>{base_url}#f={item['id']}</loc>\n"
            f"    <lastmod>{today}</lastmod>\n    <changefreq>monthly</changefreq>\n"
            f"    <priority>0.6</priority>\n  </url>"
        )
    support = site / "support.html"
    if support.is_file():
        entries.append(
            f"  <url>\n    <loc>{base_url}support.html</loc>\n"
            f"    <lastmod>{today}</lastmod>\n    <changefreq>monthly</changefreq>\n"
            f"    <priority>0.3</priority>\n  </url>"
        )
    xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        + "\n".join(entries)
        + "\n</urlset>\n"
    )
    _atomic_write(path, xml)
    return len(items)
