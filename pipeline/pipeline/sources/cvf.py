"""CVF Open Access adapter (CVPR / ICCV / WACV)."""

from __future__ import annotations

import re
from typing import Iterator
from urllib.parse import urljoin

from ..util import log
from .base import Adapter, PaperRecord

ENTRY = re.compile(
    r'<dt class="ptitle"><br>\s*<a href="(?P<href>[^"]+)">(?P<title>.*?)</a></dt>\s*'
    r'<dd>(?P<authors>.*?)</dd>.*?'
    r'<a href="(?P<pdf>[^"]+paper\.pdf)">pdf</a>',
    re.S,
)


class CvfAdapter(Adapter):
    source = "cvf"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        url = f"https://openaccess.thecvf.com/{venue.upper()}{year}?day=all"
        try:
            html = self.get_text(url)
        except Exception as exc:
            log(f"cvf: cannot list {url}: {exc}")
            return
        rows = list(ENTRY.finditer(html))
        log(f"cvf: {url} -> {len(rows)} entries")
        count = 0
        for m in rows:
            title = self.clean(re.sub(r"<[^>]+>", "", m.group("title")))
            if not title:
                continue
            href = urljoin("https://openaccess.thecvf.com", m.group("href"))
            pdf = urljoin("https://openaccess.thecvf.com", m.group("pdf"))
            authors_text = re.sub(r"<[^>]+>", ",", m.group("authors"))
            authors = [a.strip() for a in re.split(r",", authors_text) if a.strip()]
            pid = m.group("href").rsplit("/", 1)[-1].replace("_paper.html", "")
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=authors,
                paper_url=href,
                pdf_url=pdf,
                decision="poster",
                track="main",
                extra={"html": m.group("href")},
            )
            count += 1
            if limit and count >= limit:
                return
