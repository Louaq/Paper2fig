"""PMLR (ICML) adapter — parses the proceedings volume index."""

from __future__ import annotations

import re
from typing import Iterator

from ..util import log
from .base import Adapter, PaperRecord

PAPER_BLOCK = re.compile(r'<div class="paper">(.*?)</div>', re.S)
TITLE = re.compile(r'<p class="title">(.*?)</p>', re.S)
DETAILS = re.compile(r'<p class="details">(.*?)</p>', re.S)
PDF = re.compile(r'href="([^"]+\.pdf)"')
TAGS = re.compile(r"<[^>]+>")


class PmlrAdapter(Adapter):
    source = "pmlr"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        volume = (self.cfg.get("volumes") or {}).get(str(year))
        if not volume:
            log(f"pmlr: no volume configured for ICML {year}")
            return
        url = f"https://proceedings.mlr.press/{volume}/"
        html = self.get_text(url)
        blocks = PAPER_BLOCK.findall(html)
        log(f"pmlr: {url} -> {len(blocks)} entries")
        count = 0
        for block in blocks:
            title_m = TITLE.search(block)
            if not title_m:
                continue
            title = self.clean(TAGS.sub("", title_m.group(1)))
            details = DETAILS.search(block)
            authors: list[str] = []
            if details:
                for chunk in re.split(r";", TAGS.sub("\n", details.group(1))):
                    name = self.clean(chunk)
                    if name:
                        authors.append(name)
            pdf_m = PDF.search(block)
            pdf_url = pdf_m.group(1) if pdf_m else ""
            if pdf_url.startswith("/"):
                pdf_url = "https://proceedings.mlr.press" + pdf_url
            abs_m = re.search(r'href="([^"]+\.html)"', block)
            paper_url = abs_m.group(1) if abs_m else url
            if paper_url.startswith("/"):
                paper_url = "https://proceedings.mlr.press" + paper_url
            pid = pdf_url.rsplit("/", 1)[-1].replace(".pdf", "") or title
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=authors,
                paper_url=paper_url,
                pdf_url=pdf_url,
                decision="poster",
                track="ICML",
                extra={"volume": volume},
            )
            count += 1
            if limit and count >= limit:
                return
