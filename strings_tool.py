#!/usr/bin/env python3
"""Android strings.xml helper.

Commands:
    clean   Remove entries marked translatable="false" (in default locale)
            from every values-*/strings.xml.
    export  Export keys that exist in default locale but are missing in
            any values-*/strings.xml to a CSV.
    all     clean then export.

Usage:
    python strings_tool.py clean  path/to/app/src/main/res
    python strings_tool.py export path/to/app/src/main/res --out missing.csv
    python strings_tool.py all    path/to/app/src/main/res
"""
from __future__ import annotations

import argparse
import csv
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

    args = ap.parse_args()
    if args.cmd == "clean":
        cmd_clean(args.res)
    elif args.cmd == "export":
        cmd_export(args.res, args.out)
    else:
        cmd_clean(args.res)
        cmd_export(args.res, args.out)


if __name__ == "__main__":
    main()
