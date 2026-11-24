"""ICME adapter via OpenAlex.

ICME 的正式论文集由 IEEE Xplore 出版，PDF 不开放；OpenAlex 会把其中相当一部分
论文关联到 arXiv 等开放版本。这里按 OpenAlex 的会议 source id 拉取当年、
有开放获取 PDF 的论文，用于后续裁剪。数量会少于正式录用数，属于预期行为。
"""

from __future__ import annotations

from typing import Iterator

from ..util import log
from .base import Adapter, PaperRecord

API = "https://api.openalex.org/works"
FIELDS = ("id,doi,title,publication_year,authorships,open_access,"
          "best_oa_location,primary_location")


class IcmeAdapter(Adapter):
    source = "icme"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        source_id = self.cfg.get("openalex_source") or "S4363607799"
        cursor = "*"
        count = 0
        while True:
            params = {
                "filter": f"primary_location.source.id:{source_id},"
                          f"publication_year:{year},is_oa:true",
                "per-page": 200,
                "cursor": cursor,
                "select": FIELDS,
                "mailto": self.settings.values.get("openalex_mailto", "gallery@visualmedical.org"),
            }
            try:
                data = self.get(API, params=params).json()
            except Exception as exc:
                log(f"icme: openalex failed: {exc}")
                return
            results = data.get("results") or []
            if not results:
                log(f"icme: OpenAlex source {source_id} has no OA works for {year}")
                return
            for work in results:
                title = self.clean(work.get("title"))
                if not title:
                    continue
                best = work.get("best_oa_location") or {}
                pdf_url = best.get("pdf_url") or (work.get("open_access") or {}).get("oa_url") or ""
                if not pdf_url or "pdf" not in pdf_url.lower():
                    continue
                authors = [
                    (authorship.get("author") or {}).get("display_name") or ""
                    for authorship in work.get("authorships") or []
                ]
                landing = (work.get("primary_location") or {}).get("landing_page_url") or ""
                doi = (work.get("doi") or "").replace("https://doi.org/", "")
                pid = doi or str(work.get("id", "")).rsplit("/", 1)[-1]
                yield PaperRecord(
                    venue=venue.lower(),
                    year=int(year),
                    source=self.source,
                    source_id=pid,
                    title=title,
                    authors=[name for name in authors if name],
                    paper_url=landing or (f"https://doi.org/{doi}" if doi else pdf_url),
                    pdf_url=pdf_url,
                    doi=doi or None,
                    decision="poster",
                    track="main",
                )
                count += 1
                if limit and count >= limit:
                    return
            cursor = (data.get("meta") or {}).get("next_cursor")
            if not cursor:
                return
