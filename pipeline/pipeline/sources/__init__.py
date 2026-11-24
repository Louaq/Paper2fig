"""Adapter registry."""

from __future__ import annotations

from ..config import venue_config
from .acl import AclAdapter
from .aaai import AaaiAdapter
from .base import Adapter, PaperRecord
from .cvf import CvfAdapter
from .eccv import EccvAdapter
from .icme import IcmeAdapter
from .ijcai import IjcaiAdapter
from .miccai import MiccaiAdapter
from .neurips import NeuripsAdapter
from .openreview import OpenReviewAdapter
from .openalex import OpenAlexAdapter
from .pmlr import PmlrAdapter

ADAPTERS = {
    "openreview": OpenReviewAdapter,
    "openalex": OpenAlexAdapter,
    "pmlr": PmlrAdapter,
    "neurips": NeuripsAdapter,
    "cvf": CvfAdapter,
    "acl": AclAdapter,
    "aaai": AaaiAdapter,
    "eccv": EccvAdapter,
    "miccai": MiccaiAdapter,
    "ijcai": IjcaiAdapter,
    "icme": IcmeAdapter,
}

__all__ = ["ADAPTERS", "Adapter", "PaperRecord", "build_adapter"]


def build_adapter(settings, http, venue: str, year: int) -> Adapter:
    cfg = venue_config(venue, year)
    adapter_cls = ADAPTERS.get(cfg["adapter"])
    if not adapter_cls:
        raise SystemExit(f"no adapter {cfg['adapter']} for venue {venue}")
    return adapter_cls(settings, http, cfg)
