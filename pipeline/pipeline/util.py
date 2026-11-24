"""Small shared helpers: logging, http, text normalisation, hashing."""

from __future__ import annotations

import hashlib
import json
import re
import sys
import threading
import time
import unicodedata
from pathlib import Path
from typing import Any, Iterable, Iterator

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from .config import Settings

_print_lock = threading.Lock()


def log(msg: str) -> None:
    with _print_lock:
        print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def progress(**payload: Any) -> None:
    """Machine readable progress line consumed by the admin."""
    with _print_lock:
        print("@@PROGRESS " + json.dumps(payload, ensure_ascii=False), flush=True)


def result(**payload: Any) -> None:
    with _print_lock:
        print("@@RESULT " + json.dumps(payload, ensure_ascii=False), flush=True)


def session(settings: Settings) -> requests.Session:
    s = requests.Session()
    s.headers.update({"User-Agent": settings.user_agent, "Accept-Encoding": "gzip, deflate"})
    proxy = settings.values.get("proxy") or ""
    if proxy:
        s.proxies.update({"http": proxy, "https": proxy})
    retry = Retry(
        total=int(settings.retries),
        backoff_factor=1.2,
        status_forcelist=(429, 500, 502, 503, 504),
        allowed_methods=frozenset({"GET", "HEAD"}),
        raise_on_status=False,
    )
    adapter = HTTPAdapter(max_retries=retry, pool_connections=8, pool_maxsize=8)
    s.mount("https://", adapter)
    s.mount("http://", adapter)
    return s


_WS = re.compile(r"\s+")
_NON_ALNUM = re.compile(r"[^0-9a-z\u4e00-\u9fff ]+")


def normalize_title(title: str) -> str:
    """Stable dedupe key for titles.

    Unicode NFKC, lower case, punctuation stripped, whitespace collapsed.
    Used only as a *fallback* key; source ids / DOI / arXiv id win.
    """
    text = unicodedata.normalize("NFKC", title or "")
    text = text.lower().replace("&", " and ")
    text = _NON_ALNUM.sub(" ", text)
    return _WS.sub(" ", text).strip()


def normalize_doi(doi: str | None) -> str | None:
    if not doi:
        return None
    doi = doi.strip().lower()
    doi = re.sub(r"^https?://(dx\.)?doi\.org/", "", doi)
    return doi or None


_ARXIV = re.compile(r"(?:arxiv[:/\s]*|abs/|pdf/)(\d{4}\.\d{4,5})(v\d+)?")


def normalize_arxiv(value: str | None) -> str | None:
    if not value:
        return None
    m = _ARXIV.search(value.lower())
    if not m:
        return None
    return m.group(1) + (m.group(2) or "")


def sha256_file(path: Path, chunk: int = 1 << 20) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for block in iter(lambda: fh.read(chunk), b""):
            h.update(block)
    return h.hexdigest()


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> int:
    path.parent.mkdir(parents=True, exist_ok=True)
    n = 0
    with path.open("w", encoding="utf-8") as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
            n += 1
    return n


def read_jsonl(path: Path) -> Iterator[dict[str, Any]]:
    with Path(path).open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                yield json.loads(line)


def safe_name(value: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "-", value or "").strip("-")
    return value[:120] or "item"


def human_size(num: int | float) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if abs(num) < 1024:
            return f"{num:.0f}{unit}" if unit == "B" else f"{num:.1f}{unit}"
        num /= 1024
    return f"{num:.1f}TB"


def die(msg: str, code: int = 2) -> None:
    print(f"ERROR {msg}", file=sys.stderr, flush=True)
    raise SystemExit(code)
