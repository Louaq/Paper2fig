"""AAAI adapter.

AAAI publishes on OJS and registers every paper with Crossref. Crossref gives
the DOI (``10.1609/aaai.v37i1.25090``) and OJS article id (``25090``); the PDF
galley is resolved from the article page's ``citation_pdf_url`` meta tag, which
the crop stage does lazily (only for papers we actually crop).
"""

from __future__ import annotations

from typing import Iterator

from ..util import log
from .base import Adapter, PaperRecord

CROSSREF = "https://api.crossref.org/works"
VENUE_TITLE = "Proceedings of the AAAI Conference on Artificial Intelligence"


class AaaiAdapter(Adapter):
    source = "aaai"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        cursor = "*"
        count = 0
        while True:
            # 10.1609 是 AAAI 的 DOI 前缀，比 container-title 模糊查询精确得多
            params = {
                "filter": f"prefix:10.1609,"
                          f"from-pub-date:{year}-01-01,until-pub-date:{year}-12-31",
                "rows": 200,
                "cursor": cursor,
                "select": "DOI,title,author,abstract,link,container-title",
            }
            try:
                data = self.get(CROSSREF, params=params).json()
            except Exception as exc:
                log(f"aaai: crossref failed: {exc}")
                return
            message = data.get("message", {})
            items = message.get("items") or []
            if not items:
                return
            for item in items:
                container = " ".join(item.get("container-title") or [])
                if VENUE_TITLE not in container:
                    continue
                doi = item.get("DOI") or ""
                if not doi.startswith("10.1609/aaai."):
                    continue
                title = self.clean((item.get("title") or [""])[0])
                if not title:
                    continue
                authors = []
                for a in item.get("author") or []:
                    name = " ".join(x for x in (a.get("given"), a.get("family")) if x)
                    if name:
                        authors.append(name)
                article_id = doi.rsplit(".", 1)[-1]
                pdf = ""
                for link in item.get("link") or []:
                    if (link.get("content-type") or "").startswith("application/pdf"):
                        pdf = link.get("URL") or ""
                        break
                yield PaperRecord(
                    venue=venue.lower(),
                    year=int(year),
                    source=self.source,
                    source_id=article_id,
                    title=title,
                    authors=authors,
                    abstract=self.clean(item.get("abstract")),
                    paper_url=f"https://ojs.aaai.org/index.php/AAAI/article/view/{article_id}",
                    pdf_url=pdf,
                    doi=doi,
                    decision="poster",
                    track=container,
                    extra={"article_id": article_id},
                )
                count += 1
                if limit and count >= limit:
                    return
            cursor = message.get("next-cursor")
            if not cursor:
                return
