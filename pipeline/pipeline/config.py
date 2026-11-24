"""Paths, venue registry and tunables.

Everything is overridable by a JSON file (default: <pipeline>/config.json) or
by environment variables, so the admin can change behaviour without a rebuild.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

ROOT = Path(os.environ.get("GALLERY_PIPELINE_HOME", Path(__file__).resolve().parent.parent))

DEFAULTS: dict[str, Any] = {
    # workspace directories (created on demand)
    "workspace": str(ROOT / "workspace"),
    # static site root that receives publish output
    "site_dir": "/opt/1panel/www/sites/louaq.net/index",
    # 静态站备份根目录（必须在网站根目录之外；Java 后台发布也使用同一目录）
    "backup_dir": "/opt/backups/fig-gallery",
    # crop / qc
    "dpi": 200,
    "max_pages": 6,
    "include_caption": False,
    "min_width": 640,
    "min_height": 160,
    "min_ink_ratio": 0.02,
    "max_ink_ratio": 0.85,
    "max_whitespace": 0.6,
    "max_edge_ink": 0.03,
    "dup_hamming": 6,
    "jpeg_quality": 88,
    # http
    "timeout": 60,
    # 可选 HTTP 代理：部分来源（OpenReview / DBLP）会拦截机房 IP
    "proxy": "",
    "retries": 3,
    "user_agent": (
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 gallery-pipeline/1.0"
    ),
    # python used by the admin to spawn this pipeline
    "python": str(ROOT / ".venv" / "bin" / "python"),
    # MinerU precision-parse API (online crop provider)
    "mineru_api_base": "https://mineru.net",
    "mineru_api_token": "",
    "mineru_model": "pipeline",
    "mineru_batch_size": 50,
}

# venue -> adapter
VENUES: dict[str, dict[str, Any]] = {
    "iclr": {
        "name": "ICLR",
        "adapter": "openreview",
        "group": "ICLR.cc/{year}/Conference",
        "site": "https://openreview.net",
    },
    "icml": {
        "name": "ICML",
        "adapter": "pmlr",
        # PMLR volume per year; extend when a new proceedings volume exists.
        "volumes": {"2023": "v202", "2024": "v235", "2025": "v267", "2026": "v300"},
    },
    "neurips": {
        "name": "NeurIPS",
        "adapter": "neurips",
        "years": [2023, 2024, 2025, 2026],
    },
    "cvpr": {
        "name": "CVPR",
        "adapter": "cvf",
    },
    "acl": {
        "name": "ACL",
        "adapter": "acl",
    },
    "aaai": {
        "name": "AAAI",
        "adapter": "aaai",
        "volumes": {"2023": "37", "2024": "38", "2025": "39", "2026": "40"},
    },
    "eccv": {
        "name": "ECCV",
        "adapter": "eccv",
        "site": "https://www.ecva.net",
    },
    "miccai": {
        "name": "MICCAI",
        "adapter": "miccai",
        "site": "https://papers.miccai.org",
    },
    "ijcai": {
        "name": "IJCAI",
        "adapter": "ijcai",
        "site": "https://www.ijcai.org",
    },
    "icme": {
        "name": "ICME",
        "adapter": "icme",
        # OpenAlex source id：IEEE International Conference on Multimedia and Expo (ICME)
        "openalex_source": "S4363607799",
    },
    "mm": {
        "name": "ACM MM",
        "adapter": "openalex",
        # OpenAlex source id：ACM International Conference on Multimedia (ACM MM)
        "openalex_source": "S4363608757",
    },
}


@dataclass
class Settings:
    values: dict[str, Any] = field(default_factory=dict)

    def __getattr__(self, item: str) -> Any:
        try:
            return self.values[item]
        except KeyError as exc:  # pragma: no cover - programmer error
            raise AttributeError(item) from exc

    @property
    def workspace(self) -> Path:
        p = Path(self.values["workspace"])
        p.mkdir(parents=True, exist_ok=True)
        return p

    def dir(self, *parts: str) -> Path:
        p = self.workspace.joinpath(*parts)
        p.mkdir(parents=True, exist_ok=True)
        return p

    @property
    def pdf_dir(self) -> Path:
        return self.dir("pdfs")

    @property
    def crop_dir(self) -> Path:
        return self.dir("crops")

    @property
    def job_dir(self) -> Path:
        return self.dir("jobs")

    @property
    def site(self) -> Path:
        return Path(self.values["site_dir"])


def load_settings(path: str | os.PathLike[str] | None = None) -> Settings:
    values = dict(DEFAULTS)
    candidates = [Path(path)] if path else [ROOT / "config.json", Path("/etc/gallery-pipeline.json")]
    for candidate in candidates:
        if candidate and candidate.is_file():
            values.update(json.loads(candidate.read_text(encoding="utf-8")))
            break
    # environment overrides (GALLERY_SITE_DIR=..., GALLERY_WORKSPACE=...)
    for key in list(values):
        env_key = "GALLERY_" + key.upper()
        if env_key in os.environ:
            raw = os.environ[env_key]
            try:
                values[key] = json.loads(raw)
            except json.JSONDecodeError:
                values[key] = raw
    return Settings(values)


def venue_config(venue: str, year: int) -> dict[str, Any]:
    venue = venue.lower()
    if venue not in VENUES:
        raise SystemExit(f"unknown venue: {venue} (known: {', '.join(VENUES)})")
    cfg = dict(VENUES[venue])
    cfg["year"] = int(year)
    if "group" in cfg:
        cfg["group"] = cfg["group"].format(year=year)
    return cfg
