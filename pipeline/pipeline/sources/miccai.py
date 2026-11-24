"""MICCAI adapter (papers.miccai.org).

MICCAI 官方为每届会议提供开放的论文列表与 PDF：
``https://papers.miccai.org/miccai-<year>/``，PDF 位于 ``/paper/<id>_paper.pdf``。
"""

from __future__ import annotations

import html as html_lib
import re
from typing import Iterator
from urllib.parse import urljoin

from ..util import log
from .base import Adapter, PaperRecord

BLOCK = '<div class="posts-list-item">'
TITLE = re.compile(r"<b>(?P<title>.*?)</b>", re.S)
PDF = re.compile(r'href="(?P<pdf>[^"]*_paper\.pdf)"')
PAPER = re.compile(r'href="(?P<href>/miccai-\d{4}/[^"]+\.html)"')
TAGS = re.compile(r"<[^>]+>")
AUTHOR = re.compile(r'href="/miccai-\d{4}/tags#[^"]*"[^>]*>(?P<name>[^<]+)</a>')
BASE = "https://papers.miccai.org"


class MiccaiAdapter(Adapter):
    source = "miccai"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        url = f"{BASE}/miccai-{year}/"
        try:
            page = self.get_text(url)
        except Exception as exc:
            log(f"miccai: cannot list {url}: {exc}")
            return
        chunks = page.split(BLOCK)[1:]
        log(f"miccai: {url} -> {len(chunks)} entries")
        count = 0
        for chunk in chunks:
            title_match = TITLE.search(chunk)
            pdf_match = PDF.search(chunk)
            if not title_match or not pdf_match:
                continue
            title = self.clean(html_lib.unescape(TAGS.sub("", title_match.group("title"))))
            if not title:
                continue
            pdf_url = urljoin(url, pdf_match.group("pdf"))
            pid = pdf_url.rsplit("/", 1)[-1].replace("_paper.pdf", "")
            paper_match = PAPER.search(chunk)
            paper_url = urljoin(url, paper_match.group("href")) if paper_match else pdf_url
            authors = list(dict.fromkeys(
                self.clean(html_lib.unescape(name)) for name in AUTHOR.findall(chunk)
            ))
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=[name for name in authors if name],
                paper_url=paper_url,
                pdf_url=pdf_url,
                decision="poster",
                track="main",
            )
            count += 1
            if limit and count >= limit:
                return
