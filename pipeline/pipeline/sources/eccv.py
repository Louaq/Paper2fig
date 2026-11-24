"""ECCV Open Access adapter (ecva.net).

ECVA 把历届 ECCV 论文放在同一个 ``papers.php`` 页面里，按 ``papers/eccv_<year>/``
目录区分年份。这里按目标年份过滤条目。
"""

from __future__ import annotations

import html as html_lib
import re
from typing import Iterator
from urllib.parse import urljoin

from ..util import log
from .base import Adapter, PaperRecord

DT = re.compile(r'<dt class="ptitle">', re.I)
LINK = re.compile(
    r'href=["\']?(?P<href>papers/eccv_(?P<year>\d{4})/papers_ECCV/html/[^"\'>\s]+)["\']?[^>]*>'
    r'(?P<title>.*?)</a>',
    re.S,
)
PDF = re.compile(
    r'href=["\']?(?P<pdf>papers/eccv_\d{4}/papers_ECCV/papers/(?P<pid>\d+)\.pdf)["\']?',
    re.I,
)
AUTHORS = re.compile(r"<dd>\s*(?P<authors>.*?)</dd>", re.S)
TAGS = re.compile(r"<[^>]+>")
BASE = "https://www.ecva.net/"
# ECCV 2025 起，官方虚拟会站提供完整 JSON（含标题、作者、PDF 直链）
VIRTUAL_JSON = "https://eccv.ecva.net/static/virtual/data/eccv-{year}-orals-posters.json"
VIRTUAL_BASE = "https://eccv.ecva.net"


class EccvAdapter(Adapter):
    source = "eccv"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        # ECCV 2026 起，开放论文集 papers.php 往往滞后；优先用官方虚拟会站 JSON
        if year >= 2025:
            virtual = list(self._iter_virtual(venue, year, limit))
            if virtual:
                yield from virtual
                return
            log(f"eccv: virtual site has no {year} papers, fallback papers.php")
        try:
            page = self.get_text(urljoin(BASE, "papers.php"))
        except Exception as exc:
            log(f"eccv: cannot list papers.php: {exc}")
            return
        starts = [m.start() for m in DT.finditer(page)]
        log(f"eccv: papers.php -> {len(starts)} entries (filter year={year})")
        count = 0
        for index, pos in enumerate(starts):
            end = starts[index + 1] if index + 1 < len(starts) else len(page)
            chunk = page[pos:end]
            link = LINK.search(chunk)
            if not link or int(link.group("year")) != int(year):
                continue
            title = self.clean(html_lib.unescape(TAGS.sub("", link.group("title"))))
            pdf = PDF.search(chunk)
            if not title or not pdf:
                continue
            authors_match = AUTHORS.search(chunk)
            authors = []
            if authors_match:
                text = html_lib.unescape(TAGS.sub(" ", authors_match.group("authors")))
                authors = [self.clean(name).rstrip("*") for name in re.split(r",", text) if self.clean(name)]
            href = urljoin(BASE, link.group("href"))
            pdf_url = urljoin(BASE, pdf.group("pdf"))
            pid = pdf.group("pid")
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=pid,
                title=title,
                authors=authors,
                paper_url=href,
                pdf_url=pdf_url,
                decision="poster",
                track="main",
                extra={"html": link.group("href")},
            )
            count += 1
            if limit and count >= limit:
                return

    def _iter_virtual(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        url = VIRTUAL_JSON.format(year=year)
        count = 0
        while url:
            try:
                data = self.get(url).json()
            except Exception as exc:
                log(f"eccv: cannot load virtual JSON {url}: {exc}")
                return
            results = data.get("results") or []
            log(f"eccv: virtual {year} -> {len(results)} entries")
            for item in results:
                title = self.clean(item.get("name"))
                pdf_url = item.get("paper_pdf_url") or ""
                if not title or not pdf_url:
                    continue
                authors = [self.clean(a.get("fullname")) for a in item.get("authors") or []]
                source_id = str(item.get("sourceid") or item.get("id") or "")
                # 官方虚拟会站用负数 sourceid 表示部分论文，PDF 文件名仍是绝对值
                if source_id.startswith("-") and source_id[1:].isdigit():
                    source_id = source_id[1:]
                # 优先用该论文的虚拟会站详情页，其次官方 sourceurl，最后退回 PDF
                virtual_url = item.get("virtualsite_url") or ""
                if virtual_url:
                    paper_url = urljoin(VIRTUAL_BASE, virtual_url)
                else:
                    paper_url = item.get("sourceurl") or ""
                if not paper_url.startswith("http"):
                    paper_url = pdf_url
                decision = (item.get("eventtype") or "poster").lower()
                yield PaperRecord(
                    venue=venue.lower(),
                    year=int(year),
                    source=self.source,
                    source_id=source_id,
                    title=title,
                    authors=[name for name in authors if name],
                    paper_url=paper_url,
                    pdf_url=pdf_url,
                    decision=decision,
                    track="main",
                    extra={"virtual": item.get("virtualsite_url"), "eventtype": item.get("eventtype")},
                )
                count += 1
                if limit and count >= limit:
                    return
            url = data.get("next")
            if url and not str(url).startswith("http"):
                url = urljoin(VIRTUAL_BASE, url)
