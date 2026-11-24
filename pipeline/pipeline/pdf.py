"""PDF download with cache, retries and resumable state."""

from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import pymupdf

from .util import log, safe_name, sha256_file

OJS_PDF_META = re.compile(
    r'<meta\s+name=["\']citation_pdf_url["\']\s+content=["\']([^"\']+)["\']', re.I
)
# Anubis (proof-of-work bot protection, seen on ojs.aaai.org) challenge payload.
ANUBIS_CHALLENGE = re.compile(
    r'<script id="anubis_challenge" type="application/json">(.*?)</script>', re.S
)


class PdfError(RuntimeError):
    pass


def _anubis_solve(http, url: str, html: str) -> bool:
    """Solve an Anubis proof-of-work challenge and store the auth cookie.

    Cloudflare-fronted OJS (AAAI) answers the PDF URL with a small HTML page
    instead of the PDF. The challenge is bound to the client IP + User-Agent,
    so it must be solved on the same session that later downloads the file.
    """
    m = ANUBIS_CHALLENGE.search(html)
    if not m:
        return False
    try:
        payload = json.loads(m.group(1))
        challenge = payload["challenge"]
        difficulty = int(payload["rules"].get("difficulty", 0))
        data = challenge.get("randomData") or ""
        challenge_id = challenge["id"]
    except (ValueError, KeyError, TypeError) as exc:
        log(f"pdf: bad anubis challenge: {exc}")
        return False

    prefix = "0" * difficulty
    started = time.time()
    nonce = 0
    digest = ""
    while True:
        digest = hashlib.sha256((data + str(nonce)).encode()).hexdigest()
        if digest.startswith(prefix):
            break
        nonce += 1
    elapsed_ms = int((time.time() - started) * 1000)

    from urllib.parse import urlencode

    query = urlencode({
        "id": challenge_id,
        "response": digest,
        "nonce": nonce,
        "redir": "/",
        "elapsedTime": elapsed_ms,
    })
    # The challenge page lives on the same host as the protected URL.
    scheme = urlparse(url).scheme or "https"
    base = urlparse(url).netloc or "ojs.aaai.org"
    https = f"{scheme}://{base}"
    try:
        resp = http.get(
            f"{https}/.within.website/x/cmd/anubis/api/pass-challenge?{query}",
            timeout=30,
            allow_redirects=False,
        )
        if resp.status_code not in (200, 302, 303):
            log(f"pdf: anubis pass-challenge failed: HTTP {resp.status_code}")
            return False
    except Exception as exc:
        log(f"pdf: anubis pass-challenge error: {exc}")
        return False
    return True


def cache_path(settings, venue: str, year: int, paper_id: str) -> Path:
    folder = settings.pdf_dir / venue.lower() / str(year)
    folder.mkdir(parents=True, exist_ok=True)
    return folder / f"{safe_name(paper_id)}.pdf"


def resolve_pdf_url(settings, http, record: dict[str, Any]) -> str:
    """Best-effort PDF URL for sources that do not expose one directly."""
    pdf_url = record.get("pdf_url") or ""
    if pdf_url:
        # 出版社（ACM / IEEE 等）的链接直接下载通常会被拦截，
        # 按 DOI 回查 OpenAlex，优先拿 arXiv 等开放副本
        from .sources.openalex import is_paywalled, resolve_pdf_by_doi

        if not is_paywalled(pdf_url):
            return pdf_url
        found = resolve_pdf_by_doi(http, settings, record.get("doi") or "")
        if found:
            log(f"pdf: openalex fallback for {record.get('id')}: {found}")
            return found

    paper_url = record.get("paper_url") or ""
    if "ojs.aaai.org" in paper_url:
        try:
            html = http.get(paper_url, timeout=settings.timeout).text
            if "anubis_challenge" in html and _anubis_solve(http, paper_url, html):
                html = http.get(paper_url, timeout=settings.timeout).text
            m = OJS_PDF_META.search(html)
            if m:
                return m.group(1)
        except Exception as exc:
            log(f"pdf: cannot resolve AAAI galley for {paper_url}: {exc}")

    if "openreview.net" in paper_url:
        forum = paper_url.rsplit("id=", 1)[-1]
        return f"https://openreview.net/pdf?id={forum}"

    # last resort: arXiv by title
    from .sources import arxiv

    found = arxiv.resolve_pdf(http, settings, record.get("title", ""))
    if found:
        log(f"pdf: arXiv fallback for {record.get('id')}: {found[1]}")
        return found[1]
    return ""


def download(settings, http, url: str, dest: Path, resume: bool = True) -> dict[str, Any]:
    """Download ``url`` to ``dest``; safe to call again after an interruption."""
    if dest.exists() and dest.stat().st_size > 1024:
        info = inspect(settings, dest)
        if info["ok"]:
            return {**info, "cached": True, **download_meta(dest)}

    tmp = dest.with_suffix(".part")
    headers = {}
    if resume and tmp.exists() and tmp.stat().st_size > 0:
        headers["Range"] = f"bytes={tmp.stat().st_size}-"
    retried = False
    while True:
        try:
            with http.get(url, stream=True, timeout=settings.timeout, headers=headers, allow_redirects=True) as resp:
                if resp.status_code == 416:
                    tmp.replace(dest)
                else:
                    resp.raise_for_status()
                    ctype = (resp.headers.get("Content-Type") or "").lower()
                    if "text/html" in ctype:
                        if not retried and _anubis_solve(http, url, resp.text):
                            log(f"pdf: solved bot challenge for {host_of(url)}")
                            retried = True
                            headers = {}
                            continue
                        raise PdfError(f"expected PDF, got {ctype}")
                    mode = "ab" if headers.get("Range") and resp.status_code == 206 else "wb"
                    if mode == "wb":
                        tmp.unlink(missing_ok=True)
                    with tmp.open(mode) as fh:
                        for chunk in resp.iter_content(1 << 16):
                            if chunk:
                                fh.write(chunk)
                    tmp.replace(dest)
        except PdfError:
            raise
        except Exception as exc:
            raise PdfError(f"download failed: {exc}") from exc
        break

    info = inspect(settings, dest)
    if not info["ok"]:
        dest.unlink(missing_ok=True)
        raise PdfError(info["error"])
    return {**info, "cached": False, **download_meta(dest)}


def download_meta(dest: Path) -> dict[str, Any]:
    return {"path": str(dest), "size": dest.stat().st_size, "sha256": sha256_file(dest)}


def inspect(settings, path: Path) -> dict[str, Any]:
    """Validate that a file really is a usable PDF."""
    try:
        with path.open("rb") as fh:
            head = fh.read(5)
        if head != b"%PDF-":
            return {"ok": False, "error": "not a PDF (bad magic)"}
        doc = pymupdf.open(path)
        pages = doc.page_count
        doc.close()
        if pages < 1:
            return {"ok": False, "error": "PDF has no pages"}
        return {"ok": True, "pages": pages, "error": ""}
    except Exception as exc:
        return {"ok": False, "error": f"unreadable PDF: {exc}"}


def host_of(url: str) -> str:
    try:
        return urlparse(url).netloc
    except Exception:
        return ""
