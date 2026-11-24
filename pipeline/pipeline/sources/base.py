"""Adapter contract: every source yields the same PaperRecord."""

from __future__ import annotations

import re
from dataclasses import asdict, dataclass, field
from typing import Any, Iterator

from ..util import normalize_arxiv, normalize_doi, normalize_title


@dataclass
class PaperRecord:
    venue: str
    year: int
    source: str
    source_id: str
    title: str
    authors: list[str] = field(default_factory=list)
    paper_url: str = ""
    pdf_url: str = ""
    abstract: str = ""
    doi: str | None = None
    arxiv_id: str | None = None
    track: str | None = None
    decision: str | None = None  # oral / spotlight / poster / best / withdrawn / reject
    extra: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.title = re.sub(r"\s+", " ", (self.title or "")).strip()
        self.authors = [a.strip() for a in self.authors if a and a.strip()]
        self.doi = normalize_doi(self.doi)
        self.arxiv_id = normalize_arxiv(self.arxiv_id or self.pdf_url or "")

    @property
    def title_key(self) -> str:
        return normalize_title(self.title)

    def dedupe_keys(self) -> list[str]:
        keys = []
        if self.source_id:
            keys.append(f"{self.source}:{self.source_id}")
        if self.doi:
            keys.append(f"doi:{self.doi}")
        if self.arxiv_id:
            keys.append(f"arxiv:{self.arxiv_id}")
        if self.title_key:
            keys.append(f"title:{self.title_key}")
        return keys

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


class Adapter:
    """Base class: subclasses implement ``iter_papers``."""

    source = "base"

    def __init__(self, settings, session, cfg: dict | None = None):
        self.settings = settings
        self.http = session
        self.cfg = cfg or {}

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        raise NotImplementedError

    # helpers -------------------------------------------------------------
    def get(self, url: str, **kwargs):
        kwargs.setdefault("timeout", self.settings.timeout)
        resp = self.http.get(url, **kwargs)
        resp.raise_for_status()
        return resp

    def get_text(self, url: str, **kwargs) -> str:
        return self.get(url, **kwargs).text

    @staticmethod
    def clean(text: str | None) -> str:
        if not text:
            return ""
        return re.sub(r"\s+", " ", text).strip()
