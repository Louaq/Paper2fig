"""NeurIPS proceedings adapter (proceedings.neurips.cc)."""

from __future__ import annotations

import re
from typing import Iterator
from urllib.parse import urljoin

from ..util import log
from .base import Adapter, PaperRecord

# 2023+ 版式：<div class="paper-content"><a href=...>Title</a><span class="paper-authors">A</span>
ENTRY_MODERN = re.compile(
    r'<a[^>]*href="(?P<href>/paper_files/paper/\d+/hash/[^"]+-Abstract-[^"]+)"[^>]*>(?P<title>.*?)</a>\s*'
    r'<span class="paper-authors">(?P<authors>[^<]*)</span>',
    re.S,
)
# 更早的版式：<li><a title="Title" href="...">Title</a>, <i>Authors</i></li>
ENTRY_LEGACY = re.compile(
    r'<a[^>]*title="(?P<title>[^"]*)"[^>]*href="(?P<href>/paper_files/paper/\d+/hash/[^"]+)"[^>]*>.*?</a>,?\s*<i>(?P<authors>[^<]*)</i>',
    re.S,
)


class NeuripsAdapter(Adapter):
    source = "neurips"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        base = f"https://proceedings.neurips.cc/paper_files/paper/{year}"
        try:
            html = self.get_text(base)
        except Exception as exc:
            log(f"neurips: cannot list {year}: {exc}")
            return
        rows = [m.groupdict() for m in ENTRY_MODERN.finditer(html)] or [
            m.groupdict() for m in ENTRY_LEGACY.finditer(html)
        ]
        log(f"neurips: {base} -> {len(rows)} entries")
        count = 0
        for row in rows:
            href = row.get("href") or ""
            if "-Abstract-" not in href:
                continue
            title = self.clean(re.sub(r"<[^>]+>", "", row.get("title") or ""))
            if not title:
                continue
            track = "Conference"
            if "Datasets_and_Benchmarks" in href:
                track = "Datasets and Benchmarks"
            pdf = urljoin(base, href.replace("-Abstract-", "-Paper-").replace(".html", ".pdf"))
            pid = href.rsplit("/", 1)[-1].split("-")[0]
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=[a.strip() for a in (row.get("authors") or "").split(",") if a.strip()],
                paper_url=urljoin(base, href),
                pdf_url=pdf,
                decision="poster",
                track=track,
                extra={"href": href},
            )
            count += 1
            if limit and count >= limit:
                return
