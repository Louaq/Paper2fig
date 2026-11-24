"""ACL Anthology adapter (aclanthology.org)."""

from __future__ import annotations

import re
from typing import Iterator

from ..util import log
from .base import Adapter, PaperRecord

TITLE_LINK = re.compile(
    r'<strong[^>]*>\s*<a[^>]*href=["\']?(?P<href>(?:https?://aclanthology\.org)?/[^"\'>\s]+)["\']?[^>]*>(?P<title>.*?)</a>',
    re.S,
)
PDF_LINK = re.compile(r'href=["\']?(?P<pdf>(?:https?://aclanthology\.org)?/[^"\'>\s]+\.pdf)["\']?')
PERSON = re.compile(r'<a[^>]*href=["\']?/people/[^"\'>]*["\']?[^>]*>(?P<name>[^<]+)</a>')
TAGS = re.compile(r"<[^>]+>")


class AclAdapter(Adapter):
    source = "acl"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        url = f"https://aclanthology.org/events/{venue.lower()}-{year}/"
        try:
            html = self.get_text(url)
        except Exception as exc:
            log(f"acl: cannot list {url}: {exc}")
            return
        starts = [m.start() for m in TITLE_LINK.finditer(html)]
        log(f"acl: {url} -> {len(starts)} entries")
        count = 0
        for idx, pos in enumerate(starts):
            end = starts[idx + 1] if idx + 1 < len(starts) else len(html)
            chunk = html[pos:end]
            m = TITLE_LINK.match(chunk)
            if not m:
                continue
            title = self.clean(TAGS.sub("", m.group("title")))
            if re.match(r"^(proceedings|front matter|preface|introduction to the|findings of the|system demonstrations)", title, re.I):
                continue
            pdf_m = PDF_LINK.search(chunk)
            pdf_url = pdf_m.group("pdf") if pdf_m else ""
            if pdf_url.startswith("/"):
                pdf_url = "https://aclanthology.org" + pdf_url
            href = m.group("href")
            if href.startswith("/"):
                href = "https://aclanthology.org" + href
            authors = [self.clean(x) for x in PERSON.findall(chunk)]
            # de-duplicate consecutive duplicates that appear in the header link
            authors = list(dict.fromkeys(authors))
            pid = href.rstrip("/").rsplit("/", 1)[-1]
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
                track=pid.split(".")[1] if "." in pid else "main",
                extra={"anthology_id": pid},
            )
            count += 1
            if limit and count >= limit:
                return
