"""OpenReview adapter (ICLR and any conference hosted on OpenReview).

OpenReview has two APIs: api2 (2023+) and api1 (older). The adapter tries
api2 first and falls back to api1 when the group has no notes there.

Accepted / withdrawn / rejected are distinguished through ``content.venue``
and ``content.venueid`` (v2) or ``content.venue`` (v1).
"""

from __future__ import annotations

from typing import Any, Iterator

from ..util import log
from .base import Adapter, PaperRecord

V2 = "https://api2.openreview.net/notes"
V1 = "https://api.openreview.net/notes"
PAGE = 1000


def _val(content: dict[str, Any], key: str, default=None):
    """OpenReview v2 wraps every value in {"value": ...}."""
    value = content.get(key, default)
    if isinstance(value, dict) and "value" in value:
        return value["value"]
    return value


class OpenReviewAdapter(Adapter):
    source = "openreview"

    def iter_papers(self, venue: str, year: int, limit: int | None = None) -> Iterator[PaperRecord]:
        group = f"{venue.upper()}.cc/{year}/Conference"
        notes, api = self._fetch_notes(group)
        if not notes:
            log(f"openreview: no notes for {group} (api={api})")
            return
        count = 0
        skipped = 0
        for note in notes:
            content = note.get("content", {})
            venue_label = _val(content, "venue") or ""
            venue_id = _val(content, "venueid") or ""
            decision = self._decision(venue_label, venue_id, year)
            if decision in {None, "withdrawn", "reject"}:
                skipped += 1
                continue
            title = self.clean(_val(content, "title"))
            if not title:
                skipped += 1
                continue
            forum = note.get("forum") or note.get("id")
            pdf = _val(content, "pdf") or (f"/pdf?id={forum}" if forum else "")
            if pdf and pdf.startswith("/"):
                pdf = "https://openreview.net" + pdf
            authors = _val(content, "authors") or []
            authors = [self.clean(a) for a in authors if a]
            yield PaperRecord(
                venue=venue.lower(),
                year=int(year),
                source=self.source,
                source_id=str(note.get("id")),
                title=title,
                authors=authors,
                abstract=self.clean(_val(content, "abstract")),
                paper_url=f"https://openreview.net/forum?id={forum}",
                pdf_url=pdf,
                decision=decision,
                track=venue_label or None,
                extra={"number": note.get("number"), "api": api, "venue": venue_label},
            )
            count += 1
            if limit and count >= limit:
                return
        log(f"openreview: {group} -> {count} accepted papers ({skipped} skipped)")

    # ------------------------------------------------------------------
    def _fetch_notes(self, group: str) -> tuple[list[dict[str, Any]], str]:
        # Ask for every submission, then filter on the decision. Querying
        # content.venueid=<group> would only return plain posters and silently
        # drop orals / spotlights / withdrawals.
        notes = self._paged(V2, {"invitation": f"{group}/-/Submission"}, v2=True)
        if notes:
            return notes, "v2-submission"
        notes = self._paged(V2, {"content.venueid": group}, v2=True)
        if notes:
            return notes, "v2-group"
        return self._paged(V1, {"invitation": f"{group}/-/Blind_Submission"}), "v1"

    def _paged(self, url: str, params: dict[str, Any], v2: bool = False) -> list[dict[str, Any]]:
        out: list[dict[str, Any]] = []
        offset = 0
        while True:
            query = dict(params, limit=PAGE, offset=offset)
            try:
                resp = self.get(url, params=query)
                data = resp.json()
            except Exception as exc:  # network / parse
                body = ""
                resp = getattr(exc, "response", None)
                if resp is not None:
                    body = (resp.text or "")[:200]
                log(f"openreview: request failed {url} {exc} {body}")
                if resp is not None and resp.status_code == 403:
                    log("openreview: 403 ChallengeRequired —— 机房 IP 被要求人机校验；"
                        "可在管线设置里配置 proxy（或改用 PMLR/CVF/ACL/NeurIPS 来源）")
                break
            notes = data.get("notes") or []
            out.extend(notes)
            if len(notes) < PAGE:
                break
            offset += PAGE
            if offset > 30000:  # safety
                break
        return out

    @staticmethod
    def _decision(venue_label: str, venue_id: str, year: int) -> str | None:
        text = f"{venue_label} {venue_id}".lower()
        if not text.strip():
            return None
        if "withdraw" in text:
            return "withdrawn"
        if "reject" in text or "desk" in text:
            return "reject"
        if "submitted" in text or "under review" in text:
            return None  # not decided yet
        if "oral" in text:
            return "oral"
        if "spotlight" in text or "highlight" in text:
            return "spotlight"
        if "poster" in text:
            return "poster"
        if str(year) in text:
            return "poster"  # main-conference acceptance without a poster label
        return None
