"""IJCAI proceedings adapter (ijcai.org).

每个年份一个列表页 ``/proceedings/<year>/``，论文详情页为 ``/proceedings/<year>/<n>``，
PDF 为 ``/proceedings/<year>/<000n>.pdf``。
"""

from __future__ import annotations

import html as html_lib
import re
from typing import Iterator
from urllib.parse import urljoin

from ..util import log
from .base import Adapter, PaperRecord

BLOCK = '<div class="paper_wrapper'
TITLE = re.compile(
    r'<a class="title" href="(?P<href>/proceedings/\d+/\d+)">(?P<title>.*?)</a>', re.S
)
AUTHORS = re.compile(r'<div class="authors">(?P<authors>.*?)</div>', re.S)
PDF = re.compile(r'href="(?P<pdf>/proceedings/\d+/[^"]+\.pdf)"')
DOI = re.compile(r'href="https://doi\.org/(?P<doi>10\.24963/ijcai\.\d+/\d+)"')
TAGS = re.compile(r"<[^>]+>")
BASE = "https://www.ijcai.org"


class IjcaiAdapter(Adapter):
    source = "ijcai"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        url = f"{BASE}/proceedings/{year}/"
        try:
            page = self.get_text(url)
        except Exception as exc:
            log(f"ijcai: cannot list {url}: {exc}")
            return
        chunks = page.split(BLOCK)[1:]
        log(f"ijcai: {url} -> {len(chunks)} entries")
        count = 0
        for chunk in chunks:
            title_match = TITLE.search(chunk)
            pdf_match = PDF.search(chunk)
            if not title_match or not pdf_match:
                continue
            title = self.clean(html_lib.unescape(TAGS.sub("", title_match.group("title"))))
            if not title:
                continue
            authors_match = AUTHORS.search(chunk)
            authors = []
            if authors_match:
                text = html_lib.unescape(TAGS.sub(" ", authors_match.group("authors")))
                authors = [self.clean(name) for name in re.split(r",", text) if self.clean(name)]
            pdf_url = urljoin(BASE, pdf_match.group("pdf"))
            pid = pdf_url.rsplit("/", 1)[-1].replace(".pdf", "")
            doi_match = DOI.search(chunk)
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=authors,
                paper_url=urljoin(BASE, title_match.group("href")),
                pdf_url=pdf_url,
                doi=doi_match.group("doi") if doi_match else None,
                decision="poster",
                track="main",
            )
            count += 1
            if limit and count >= limit:
                return
