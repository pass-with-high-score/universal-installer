#!/usr/bin/env python3
"""Android strings.xml helper.

Commands:
    clean   Remove entries marked translatable="false" (in default locale)
            from every values-*/strings.xml.
    export  Export keys that exist in default locale but are missing in
            any values-*/strings.xml to a CSV.
    prune   Find <string> keys in default locale that are not referenced
            anywhere in source (R.string.X / @string/X). Dry-run by
            default (writes a CSV of candidates); pass --apply to delete
            them from values/ and every values-*/strings.xml.
    all     clean then export.

Usage:
    python strings_tool.py clean  path/to/app/src/main/res
    python strings_tool.py export path/to/app/src/main/res --out missing.csv
    python strings_tool.py prune  path/to/app/src/main/res
    python strings_tool.py prune  path/to/app/src/main/res --apply
    python strings_tool.py all    path/to/app/src/main/res
"""
from __future__ import annotations

import argparse
import csv
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

TAGS = ("string", "string-array", "plurals")


def parse(path: Path) -> ET.ElementTree:
    parser = ET.XMLParser(target=ET.TreeBuilder(insert_comments=True))
    return ET.parse(path, parser)


def write(tree: ET.ElementTree, path: Path) -> None:
    ET.indent(tree, space="    ")
    tree.write(path, encoding="utf-8", xml_declaration=True)


def non_translatable_names(default_xml: Path) -> set[str]:
    tree = parse(default_xml)
    out: set[str] = set()
    for el in tree.getroot():
        if not isinstance(el.tag, str):
            continue
        if el.tag in TAGS and el.get("translatable", "true").lower() == "false":
            name = el.get("name")
            if name:
                out.add(name)
    return out


def names_in(xml: Path) -> set[str]:
    tree = parse(xml)
    out: set[str] = set()
    for el in tree.getroot():
        if isinstance(el.tag, str) and el.tag in TAGS:
            name = el.get("name")
            if name:
                out.add(name)
    return out


def default_entries(default_xml: Path) -> dict[str, str]:
    tree = parse(default_xml)
    out: dict[str, str] = {}
    for el in tree.getroot():
        if not isinstance(el.tag, str) or el.tag != "string":
            continue
        if el.get("translatable", "true").lower() == "false":
            continue
        name = el.get("name")
        if name:
            out[name] = (el.text or "").strip()
    return out


def clean_locale(xml: Path, remove: set[str]) -> int:
    tree = parse(xml)
    root = tree.getroot()
    removed = 0
    for el in list(root):
        if not isinstance(el.tag, str):
            continue
        if el.tag in TAGS and el.get("name") in remove:
            root.remove(el)
            removed += 1
    if removed:
        write(tree, xml)
    return removed


def find_default(res: Path) -> Path:
    p = res / "values" / "strings.xml"
    if not p.exists():
        sys.exit(f"default strings.xml not found: {p}")
    return p


def locale_files(res: Path) -> list[Path]:
    return sorted(
        d / "strings.xml"
        for d in res.glob("values-*")
        if (d / "strings.xml").exists()
    )


def cmd_clean(res: Path) -> None:
    remove = non_translatable_names(find_default(res))
    print(f"translatable=false entries in default: {len(remove)}")
    if not remove:
        return
    total = 0
    for f in locale_files(res):
        n = clean_locale(f, remove)
        if n:
            print(f"  {f}: removed {n}")
            total += n
    print(f"total removed across locales: {total}")


CODE_EXTS = {".kt", ".java"}
XML_EXTS = {".xml"}
R_STRING_RE = re.compile(r"\bR\.string\.([A-Za-z0-9_]+)")
AT_STRING_RE = re.compile(r"@string/([A-Za-z0-9_]+)")
TOOLS_KEEP_RE = re.compile(r"@string/([A-Za-z0-9_]+)")


def default_string_names(default_xml: Path) -> dict[str, str]:
    tree = parse(default_xml)
    out: dict[str, str] = {}
    for el in tree.getroot():
        if isinstance(el.tag, str) and el.tag == "string":
            name = el.get("name")
            if name:
                out[name] = (el.text or "").strip()
    return out


def iter_source_files(roots: list[Path]):
    skip_dirs = {"build", ".gradle", ".idea", "generated", ".git", "node_modules"}
    for root in roots:
        if not root.exists():
            continue
        for p in root.rglob("*"):
            if not p.is_file():
                continue
            if any(part in skip_dirs for part in p.parts):
                continue
            if p.suffix in CODE_EXTS or p.suffix in XML_EXTS:
                yield p


def collect_used_names(roots: list[Path], res: Path) -> set[str]:
    used: set[str] = set()
    default_xml = res / "values" / "strings.xml"
    for f in iter_source_files(roots):
        try:
            text = f.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        if f.suffix in CODE_EXTS:
            used.update(R_STRING_RE.findall(text))
        else:
            # XML: @string/X references; skip the default strings.xml itself
            if f.resolve() == default_xml.resolve():
                continue
            used.update(AT_STRING_RE.findall(text))
            # tools:keep="@string/a,@string/b"
            for m in re.finditer(r'tools:keep\s*=\s*"([^"]*)"', text):
                used.update(TOOLS_KEEP_RE.findall(m.group(1)))
    return used


def remove_names_from(xml: Path, remove: set[str]) -> int:
    tree = parse(xml)
    root = tree.getroot()
    removed = 0
    for el in list(root):
        if not isinstance(el.tag, str):
            continue
        if el.tag == "string" and el.get("name") in remove:
            root.remove(el)
            removed += 1
    if removed:
        write(tree, xml)
    return removed


def cmd_prune(
    res: Path,
    src_roots: list[Path],
    out: Path,
    apply: bool,
    keep: set[str],
) -> None:
    default_xml = find_default(res)
    entries = default_string_names(default_xml)
    roots = src_roots or [res.parent]
    used = collect_used_names(roots, res)
    unused = sorted(n for n in entries if n not in used and n not in keep)

    print(f"default <string> count: {len(entries)}")
    print(f"referenced names found: {len(used)}")
    print(f"unused candidates: {len(unused)}")

    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(("name", "default_value"))
        for name in unused:
            w.writerow((name, entries[name]))
    print(f"report -> {out}")

    if not apply or not unused:
        if not apply:
            print("dry-run: pass --apply to delete these from strings.xml files")
        return

    remove_set = set(unused)
    total = remove_names_from(default_xml, remove_set)
    print(f"  {default_xml}: removed {total}")
    for f in locale_files(res):
        n = remove_names_from(f, remove_set)
        if n:
            print(f"  {f}: removed {n}")
            total += n
    print(f"total removed across files: {total}")


def cmd_export(res: Path, out: Path) -> None:
    entries = default_entries(find_default(res))
    rows: list[tuple[str, str, str]] = []
    for f in locale_files(res):
        locale = f.parent.name.removeprefix("values-")
        have = names_in(f)
        for name, value in entries.items():
            if name not in have:
                rows.append((locale, name, value))
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(("locale", "name", "default_value"))
        w.writerows(rows)
    print(f"missing entries: {len(rows)} -> {out}")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    for name in ("clean", "export", "all"):
        p = sub.add_parser(name)
        p.add_argument("res", type=Path, help="path to res/ directory")
        if name != "clean":
            p.add_argument("--out", type=Path, default=Path("missing_translations.csv"))

    pp = sub.add_parser("prune", help="remove <string> keys not referenced in source")
    pp.add_argument("res", type=Path, help="path to res/ directory")
    pp.add_argument(
        "--src",
        type=Path,
        action="append",
        default=None,
        help="source root(s) to scan (repeatable). Default: parent of res/",
    )
    pp.add_argument("--out", type=Path, default=Path("unused_strings.csv"))
    pp.add_argument("--apply", action="store_true", help="actually delete (default: dry-run)")
    pp.add_argument("--keep", default="", help="comma-separated names to always keep")

    args = ap.parse_args()
    if args.cmd == "clean":
        cmd_clean(args.res)
    elif args.cmd == "export":
        cmd_export(args.res, args.out)
    elif args.cmd == "prune":
        keep = {s.strip() for s in args.keep.split(",") if s.strip()}
        cmd_prune(args.res, args.src or [], args.out, args.apply, keep)
    else:
        cmd_clean(args.res)
        cmd_export(args.res, args.out)


if __name__ == "__main__":
    main()
