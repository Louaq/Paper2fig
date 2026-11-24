"""Command line interface used by the admin (and by humans).

    python -m pipeline.cli doctor
    python -m pipeline.cli collect --venue iclr --year 2026 --limit 50 --out x.jsonl
    python -m pipeline.cli crop --input items.json --out results.jsonl --limit 20
    python -m pipeline.cli publish --input export.json --site /path --apply

Machine readable lines:
    @@PROGRESS {"stage": "crop", "done": 3, "total": 20}
    @@RESULT   {...}            (last line, exit code 0)
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import traceback
from pathlib import Path
from typing import Any

from . import __version__, mineru as mineru_mod, pdf as pdf_mod, publish as publish_mod, qc as qc_mod
from .config import load_settings, venue_config
from .crop import crop_from_layout, crop_pdf
from .sources import build_adapter
from .util import die, log, normalize_title, progress, read_jsonl, result, session, write_jsonl


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="pipeline", description="Top-Conf Figure Gallery pipeline")
    parser.add_argument("--config", help="path to config.json")
    parser.add_argument("--verbose", action="store_true")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("doctor", help="check dependencies and paths")

    p = sub.add_parser("collect", help="discover papers from a venue adapter")
    p.add_argument("--venue", required=True)
    p.add_argument("--year", required=True, type=int)
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--out", required=True)
    p.add_argument("--quiet", action="store_true")

    p = sub.add_parser("fetch", help="download and cache PDFs")
    p.add_argument("--input", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--force", action="store_true")

    p = sub.add_parser("crop", help="locate Figure 1, render and quality check")
    p.add_argument("--input", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--out-dir", help="crop image directory (default: workspace/crops)")
    p.add_argument("--limit", type=int, default=0)
    p.add_argument("--dpi", type=int, default=0)
    p.add_argument("--include-caption", action="store_true")
    p.add_argument("--force", action="store_true")
    p.add_argument("--no-dedup", action="store_true")
    p.add_argument("--no-fetch", action="store_true")
    p.add_argument("--provider", choices=["local", "mineru"], default="local")

    p = sub.add_parser("publish", help="merge approved crops into the static site")
    p.add_argument("--input", required=True)
    p.add_argument("--site")
    p.add_argument("--apply", action="store_true")
    p.add_argument("--force", action="store_true")
    p.add_argument("--update-sitemap", action="store_true")

    p = sub.add_parser("selftest", help="crop a bundled/remote sample PDF")
    p.add_argument("--url", default="https://arxiv.org/pdf/2209.14988")
    p.add_argument("--out-dir")

    args = parser.parse_args(argv)
    settings = load_settings(args.config)

    try:
        if args.command == "doctor":
            return cmd_doctor(settings)
        if args.command == "collect":
            return cmd_collect(settings, args)
        if args.command == "fetch":
            return cmd_fetch(settings, args)
        if args.command == "crop":
            return cmd_crop(settings, args)
        if args.command == "publish":
            return cmd_publish(settings, args)
        if args.command == "selftest":
            return cmd_selftest(settings, args)
    except SystemExit:
        raise
    except Exception as exc:
        log(f"fatal: {exc}")
        if args.verbose:
            traceback.print_exc()
        result(ok=False, error=str(exc))
        return 1
    return 0


# ---------------------------------------------------------------------------

def cmd_doctor(settings) -> int:
    import PIL
    import pymupdf
    import requests

    info = {
        "pipeline": __version__,
        "python": sys.version.split()[0],
        "pymupdf": getattr(pymupdf, "__version__", "?"),
        "pillow": PIL.__version__,
        "requests": requests.__version__,
        "workspace": str(settings.workspace),
        "site": str(settings.site),
        "site_ok": (settings.site / "data" / "figures.json").is_file(),
        "fonts": (settings.site / "assets" / "figures.js").is_file(),
        "dpi": settings.dpi,
    }
    print(json.dumps(info, ensure_ascii=False, indent=2))
    result(**info, ok=True)
    return 0


def cmd_collect(settings, args) -> int:
    venue_cfg = venue_config(args.venue, args.year)
    http = session(settings)
    adapter = build_adapter(settings, http, args.venue, args.year)
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    seen: set[str] = set()
    started = time.time()
    tmp = out.with_suffix(out.suffix + ".part")
    with tmp.open("w", encoding="utf-8") as fh:
        for record in adapter.iter_papers(args.venue, args.year, limit=args.limit or None):
            keys = record.dedupe_keys()
            if any(k in seen for k in keys):
                continue
            seen.update(keys)
            fh.write(json.dumps(record.to_dict(), ensure_ascii=False) + "\n")
            count += 1
            if count % 25 == 0:
                progress(stage="collect", done=count, total=args.limit or 0)
                fh.flush()
    tmp.replace(out)
    payload = {
        "ok": count > 0,
        "venue": args.venue,
        "year": args.year,
        "adapter": venue_cfg["adapter"],
        "count": count,
        "file": str(out),
        "seconds": round(time.time() - started, 1),
    }
    if count == 0:
        payload["error"] = f"no papers found for {args.venue} {args.year}"
        log(f"collect: no papers for {args.venue} {args.year}")
        result(**payload)
        return 1
    log(f"collect: {count} papers -> {out}")
    result(**payload)
    return 0


def read_items(path: Path) -> list[dict[str, Any]]:
    """同时兼容 {"items": [...]} 与一行一条 JSON（JSONL）两种输入格式。"""
    text = Path(path).read_text(encoding="utf-8").strip()
    if not text:
        return []
    if text.startswith("{"):
        payload = json.loads(text)
        if isinstance(payload, dict):
            return list(payload.get("items") or [])
        return list(payload)
    return list(read_jsonl(Path(path)))


def cmd_fetch(settings, args) -> int:
    items = read_items(Path(args.input))
    total = len(items)
    http = session(settings)
    out = Path(args.out)
    done, failed = 0, 0
    with out.open("w", encoding="utf-8") as fh:
        for i, item in enumerate(items, 1):
            if args.limit and done >= args.limit:
                break
            dest = pdf_mod.cache_path(settings, item["venue"], int(item["year"]), item["id"])
            row: dict[str, Any] = {"id": item["id"], "venue": item["venue"], "year": item["year"]}
            try:
                if args.force:
                    dest.unlink(missing_ok=True)
                url = pdf_mod.resolve_pdf_url(settings, http, item)
                if not url:
                    raise pdf_mod.PdfError("no pdf url")
                info = pdf_mod.download(settings, http, url, dest)
                row.update(ok=True, pdf=info, pdf_url=url)
                done += 1
            except Exception as exc:
                row.update(ok=False, error=str(exc), pdf={"path": str(dest)})
                failed += 1
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
            fh.flush()
            progress(stage="fetch", done=i, total=total, ok=done, failed=failed)
    result(ok=True, fetched=done, failed=failed, file=str(out))
    return 0 if failed == 0 else 0


def cmd_crop(settings, args) -> int:
    payload = json.loads(Path(args.input).read_text(encoding="utf-8"))
    items = payload["items"] if isinstance(payload, dict) else payload
    total = len(items)
    dpi = args.dpi or int(settings.dpi)
    out = Path(args.out)
    crop_dir = Path(args.out_dir) if args.out_dir else settings.crop_dir
    crop_dir.mkdir(parents=True, exist_ok=True)
    http = session(settings)

    previous: dict[str, dict] = {}
    if out.is_file() and not args.force:
        for row in read_jsonl(out):
            previous[row["id"]] = row

    known: list[dict] = []
    if not args.no_dedup:
        known = _known_hashes(settings, args)

    layouts: dict[str, dict] = {}
    if args.provider == "mineru":
        tasks: list[tuple[str, Path]] = []
        planned = 0
        for item in items:
            pid = str(item["id"])
            if pid in previous and previous[pid].get("ok"):
                continue
            if args.limit and planned >= args.limit:
                break
            try:
                tasks.append((pid, _ensure_pdf(settings, http, item, allow_fetch=not args.no_fetch)))
            except pdf_mod.PdfError as exc:
                log(f"crop: {pid} pdf unavailable: {exc}")
            planned += 1
        if tasks:
            layouts = mineru_mod.prepare(
                settings,
                tasks,
                max_pages=int(settings.max_pages),
                batch_size=int(settings.values.get("mineru_batch_size", 50)),
                force=args.force,
            )

    done = failed = skipped = 0
    tmp = out.with_suffix(out.suffix + ".part")
    with tmp.open("w", encoding="utf-8") as fh:
        for i, item in enumerate(items, 1):
            pid = str(item["id"])
            if pid in previous and previous[pid].get("ok"):
                fh.write(json.dumps(previous[pid], ensure_ascii=False) + "\n")
                skipped += 1
                continue
            if args.limit and done + failed >= args.limit:
                fh.write(json.dumps(previous.get(pid, {"id": pid, "ok": False, "error": "not processed"}), ensure_ascii=False) + "\n")
                continue
            row = _crop_one(settings, http, item, crop_dir, dpi, args, known, layouts.get(pid))
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")
            fh.flush()
            if row.get("ok"):
                done += 1
                known.append({"id": pid, "h": row["qc"]["dhash"], "sig": row.get("qc", {}).get("sig", []), "a": row["qc"].get("aspect", 0)})
            else:
                failed += 1
            progress(stage="crop", done=done, failed=failed, total=total)
    tmp.replace(out)
    result(ok=True, cropped=done, failed=failed, skipped=skipped, dpi=dpi, file=str(out))
    return 0


def _ensure_pdf(settings, http, item: dict, allow_fetch: bool = True) -> Path:
    pdf_path = Path(item["pdf_path"]) if item.get("pdf_path") else None
    if pdf_path is not None and pdf_path.is_file():
        return pdf_path
    pdf_path = pdf_mod.cache_path(settings, item["venue"], int(item["year"]), str(item["id"]))
    if not pdf_path.is_file():
        if not allow_fetch:
            raise pdf_mod.PdfError(f"PDF not downloaded: {pdf_path}")
        url = pdf_mod.resolve_pdf_url(settings, http, item)
        if not url:
            raise pdf_mod.PdfError("no pdf url")
        pdf_mod.download(settings, http, url, pdf_path)
    return pdf_path


def _crop_one(settings, http, item: dict, crop_dir: Path, dpi: int, args, known, layout: dict | None = None) -> dict[str, Any]:
    pid = str(item["id"])
    row: dict[str, Any] = {"id": pid, "venue": item.get("venue"), "year": item.get("year"),
                           "provider": args.provider}
    started = time.time()
    try:
        pdf_path = _ensure_pdf(settings, http, item, allow_fetch=not args.no_fetch)
        row["pdf"] = {**pdf_mod.inspect(settings, pdf_path), "path": str(pdf_path)}

        out_img = crop_dir / pid / "figure1.jpg"
        if args.provider == "mineru":
            if layout is None:
                raise RuntimeError("mineru: no image block in the first pages")
            crop = crop_from_layout(
                pdf_path,
                layout,
                out_img,
                dpi=dpi,
                include_caption=args.include_caption or bool(settings.include_caption),
                jpeg_quality=int(settings.jpeg_quality),
            )
        else:
            crop = crop_pdf(
                pdf_path,
                out_img,
                dpi=dpi,
                max_pages=int(settings.max_pages),
                include_caption=args.include_caption or bool(settings.include_caption),
                jpeg_quality=int(settings.jpeg_quality),
            )
        if crop is None:
            row.update(ok=False, error="no figure found")
            return row

        qc = qc_mod.analyze(out_img, settings)
        flags = list(dict.fromkeys(crop.flags + qc.issues))
        confidence = crop.confidence
        if not qc.ok:
            confidence *= 0.5
        if not args.no_dedup:
            known_local = [k for k in known if k["id"] != pid]
            dup = qc_mod.find_duplicate(
                qc.dhash, qc.sig, qc.aspect, known_local, int(settings.dup_hamming)
            )
            if dup:
                flags.append("duplicate")
                row["duplicate_of"] = dup[0]
                row["duplicate_distance"] = dup[1]
                confidence *= 0.45

        row.update(
            ok=True,
            pdf_path=str(pdf_path),
            crop={**crop.to_dict(), "crop_path": str(out_img), "flags": flags},
            qc={**qc.to_dict(), "sig": qc.sig},
            confidence=round(max(0.02, min(0.99, confidence)), 3),
            seconds=round(time.time() - started, 2),
        )
        return row
    except Exception as exc:
        row.update(ok=False, error=str(exc), seconds=round(time.time() - started, 2))
        log(f"crop: {pid} failed: {exc}")
        return row


def _known_hashes(settings, args) -> list[tuple[str, int]]:
    """Hashes of already published images, cached in the workspace."""
    cache = settings.workspace / "published_hashes.json"
    site_json = settings.site / "data" / "figures.json"
    try:
        mtime = site_json.stat().st_mtime if site_json.is_file() else 0
    except OSError:
        mtime = 0
    if cache.is_file():
        try:
            data = json.loads(cache.read_text(encoding="utf-8"))
            if data.get("v") == 3 and abs(data.get("mtime", 0) - mtime) < 1:
                return list(data.get("hashes", {}).values())
        except Exception:
            pass
    hashes: dict[str, dict] = {}
    if site_json.is_file():
        try:
            figures = json.loads(site_json.read_text(encoding="utf-8"))
        except Exception:
            figures = []
        for fig in figures:
            path = settings.site / fig.get("image", "")
            if not path.is_file():
                continue
            try:
                entry = qc_mod.phash_of(path)
                entry["id"] = fig["id"]
                hashes[fig["id"]] = entry
            except Exception:
                continue
    cache.write_text(json.dumps({"mtime": mtime, "v": 3, "hashes": hashes}, separators=(",", ":")), encoding="utf-8")
    log(f"dedup: loaded {len(hashes)} published hashes")
    return list(hashes.values())


def cmd_publish(settings, args) -> int:
    payload = json.loads(Path(args.input).read_text(encoding="utf-8"))
    items = payload.get("items", []) if isinstance(payload, dict) else payload
    if not items:
        result(ok=False, errors=["empty batch: nothing to publish"])
        return 2
    site = Path(args.site) if args.site else settings.site
    plan = publish_mod.plan(site, items, allow_removals=10**9 if args.force else 0)
    if plan.errors:
        for err in plan.errors[:20]:
            log(f"publish: {err}")
        result(**plan.summary())
        return 2
    if not args.apply:
        summary = plan.summary()
        log(f"dry-run: {summary['added']} new, {summary['updated']} updated, total {len(plan.merged)}")
        result(dry_run=True, **summary)
        return 0
    backup_root = Path(settings.values["backup_dir"]) if settings.values.get("backup_dir") else None
    summary = publish_mod.apply(plan, backup_root=backup_root)
    if args.update_sitemap:
        n = publish_mod.update_sitemap(site)
        summary["sitemap_entries"] = n
    log(f"published: total {summary['total']} (+{summary['added']} new), backup {summary['backup']}")
    result(**summary)
    return 0


def cmd_selftest(settings, args) -> int:
    out_dir = Path(args.out_dir) if args.out_dir else settings.workspace / "selftest"
    out_dir.mkdir(parents=True, exist_ok=True)
    http = session(settings)
    dest = out_dir / "sample.pdf"
    info = pdf_mod.download(settings, http, args.url, dest)
    log(f"selftest: PDF {info['pages']} pages, {info['size']} bytes")
    img = out_dir / "figure1.jpg"
    crop = crop_pdf(dest, img, dpi=int(settings.dpi), max_pages=int(settings.max_pages))
    if crop is None:
        result(ok=False, error="no figure found")
        return 1
    qc = qc_mod.analyze(img, settings)
    result(ok=True, pdf=str(dest), image=str(img), crop=crop.to_dict(), qc=qc.to_dict())
    return 0


if __name__ == "__main__":
    sys.exit(main())
