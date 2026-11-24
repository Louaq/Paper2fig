"""arXiv helpers: resolve a missing PDF by title, fetch by id."""

from __future__ import annotations

import re
import xml.etree.ElementTree as ET

from ..util import log, normalize_arxiv
from .base import PaperRecord

API = "http://export.arxiv.org/api/query"
ATOM = "{http://www.w3.org/2005/Atom}"


def _query(http, settings, **params) -> list[dict]:
    params.setdefault("max_results", 3)
    try:
        resp = http.get(API, params=params, timeout=settings.timeout)
        resp.raise_for_status()
    except Exception as exc:
        log(f"arxiv: query failed: {exc}")
        return []
    try:
        root = ET.fromstring(resp.text)
    except ET.ParseError:
        return []
    out = []
    for entry in root.findall(f"{ATOM}entry"):
        title = " ".join((entry.findtext(f"{ATOM}title") or "").split())
        authors = [a.findtext(f"{ATOM}name") or "" for a in entry.findall(f"{ATOM}author")]
        link = entry.findtext(f"{ATOM}id") or ""
        pdf = ""
        for ln in entry.findall(f"{ATOM}link"):
            if ln.get("title") == "pdf":
                pdf = ln.get("href") or ""
        out.append({"title": title, "authors": authors, "id": link, "pdf": pdf})
    return out


def resolve_pdf(http, settings, title: str) -> tuple[str, str] | None:
    """Return (arxiv_id, pdf_url) for an exact-enough title match."""
    cleaned = re.sub(r"[^0-9A-Za-z ]+", " ", title)[:180].strip()
    if not cleaned:
        return None
    for entry in _query(http, settings, search_query=f'ti:"{cleaned}"', max_results=3):
        if _similar(entry["title"], title):
            arxiv_id = entry["id"].rstrip("/").rsplit("/", 1)[-1]
            pdf = entry["pdf"] or f"https://arxiv.org/pdf/{arxiv_id}"
            return normalize_arxiv(arxiv_id) or arxiv_id, pdf
    return None


def by_id(http, settings, arxiv_id: str) -> dict | None:
    rows = _query(http, settings, id_list=arxiv_id, max_results=1)
    return rows[0] if rows else None


def _similar(a: str, b: str) -> bool:
    def norm(x: str) -> str:
        return re.sub(r"[^0-9a-z]+", " ", x.lower()).strip()

    na, nb = norm(a), norm(b)
    return na == nb or (len(na) > 20 and (na in nb or nb in na))
