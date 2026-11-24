"""OpenAlex adapter for conferences whose proceedings are not openly hosted.

ACM MM（ACM International Conference on Multimedia）的正式论文集在 ACM Digital
Library，PDF 不开放；OpenAlex 会把其中相当一部分论文关联到 arXiv 等开放版本。
这里按 OpenAlex 的会议 source id 拉取当年、有开放获取 PDF 的论文，用于后续裁剪。
数量会少于正式录用数，属于预期行为（与 ICME 相同策略）。

OpenAlex 的 best_oa_location 有时会指向出版社（如 dl.acm.org）的“开放”PDF，
实际下载会被拦截；因此优先选择 arXiv / OpenReview 等可以直接抓取的副本。
"""

from __future__ import annotations

from typing import Any, Iterator

from ..util import log
from .base import Adapter, PaperRecord

API = "https://api.openalex.org/works"
FIELDS = ("id,doi,title,publication_year,authorships,open_access,"
          "best_oa_location,primary_location,locations")

# 出版社站点：即使 OpenAlex 标记为 OA，脚本直接下载通常也会被拦截
PAYWALLED_HOSTS = (
    "dl.acm.org",
    "ieeexplore.ieee.org",
    "link.springer.com",
    "sciencedirect.com",
    "onlinelibrary.wiley.com",
    "tandfonline.com",
)
# 可以直接下载的开放副本
OPEN_HOSTS = (
    "arxiv.org",
    "openreview.net",
    "proceedings.",
    "aclanthology.org",
    "openaccess.thecvf.com",
    "papers.miccai.org",
    "ijcai.org",
    "ecva.net",
)


def is_paywalled(url: str | None) -> bool:
    value = (url or "").lower()
    return any(host in value for host in PAYWALLED_HOSTS)


def pick_pdf_url(work: dict[str, Any]) -> str:
    """从 work 的 locations 中挑一个可下载的 PDF（优先 arXiv 等开放副本）。"""
    locations = work.get("locations") or []

    def usable(url: str | None) -> bool:
        return bool(url) and "pdf" in url.lower() and not is_paywalled(url)

    for location in locations:
        url = location.get("pdf_url") or ""
        if usable(url) and any(host in url.lower() for host in OPEN_HOSTS):
            return url
    best = (work.get("best_oa_location") or {}).get("pdf_url") or ""
    if usable(best):
        return best
    for location in locations:
        url = location.get("pdf_url") or ""
        if usable(url):
            return url
    oa_url = (work.get("open_access") or {}).get("oa_url") or ""
    return oa_url if usable(oa_url) else ""


def resolve_pdf_by_doi(http, settings, doi: str | None) -> str:
    """按 DOI 查 OpenAlex，返回可下载的开放 PDF；找不到返回空串。"""
    if not doi:
        return ""
    params = {
        "filter": f"doi:{doi}",
        "per-page": 1,
        "select": "id,doi,best_oa_location,locations,open_access",
        "mailto": settings.values.get("openalex_mailto", "gallery@visualmedical.org"),
    }
    try:
        data = http.get(API, params=params, timeout=settings.timeout).json()
    except Exception as exc:
        log(f"openalex: resolve pdf for {doi} failed: {exc}")
        return ""
    for work in data.get("results") or []:
        url = pick_pdf_url(work)
        if url:
            return url
    return ""


class OpenAlexAdapter(Adapter):
    source = "openalex"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        source_id = self.cfg.get("openalex_source")
        if not source_id:
            raise SystemExit(f"openalex_source missing for venue {venue}")
        tag = self.cfg.get("name") or venue
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
                log(f"{tag}: openalex failed: {exc}")
                return
            results = data.get("results") or []
            if not results:
                log(f"{tag}: OpenAlex source {source_id} has no OA works for {year}")
                return
            for work in results:
                title = self.clean(work.get("title"))
                if not title:
                    continue
                pdf_url = pick_pdf_url(work)
                if not pdf_url:
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
                    source=venue.lower(),
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
