#!/usr/bin/env python3
"""
hotpath_audit.py

Heuristic scanner for Kotlin hot path functions:
  update, render, onDraw, renderAudioBlock, raster loops, glyph loops.

Usage:
  python tools/hotpath_audit.py .
  python tools/hotpath_audit.py . --json
  python tools/hotpath_audit.py . --strict
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable


SKIP_DIRS = {".git", ".gradle", ".idea", "build", ".kotlin", ".konan", "node_modules", "out"}
HOT_NAMES = {
    "update", "render", "onDraw", "renderAudioBlock",
    "drawGlyph", "drawText", "rasterize", "rasterizeLine", "fillRect",
    "drawRect", "mix", "processAudio",
}

@dataclass
class Issue:
    severity: str
    file: str
    line: int
    function: str
    rule: str
    message: str
    snippet: str = ""


def is_skipped(path: Path) -> bool:
    return any(part in SKIP_DIRS for part in path.parts)


def iter_kt(root: Path) -> Iterable[Path]:
    for p in root.rglob("*.kt"):
        if p.is_file() and not is_skipped(p):
            yield p


def find_functions(text: str):
    # Lightweight brace matcher. Good enough for audit hints.
    pattern = re.compile(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(")
    for m in pattern.finditer(text):
        name = m.group(1)
        if name not in HOT_NAMES and "HOT_PATH_BEGIN" not in text[max(0, m.start()-300):m.start()]:
            continue
        brace = text.find("{", m.end())
        if brace == -1:
            continue
        depth = 0
        end = brace
        for i in range(brace, len(text)):
            ch = text[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        start_line = text.count("\n", 0, m.start()) + 1
        yield name, start_line, text[brace:end]


def line_no_from_offset(body: str, start_line: int, offset: int) -> int:
    return start_line + body.count("\n", 0, offset)


def add(issues, severity, path, line, fn, rule, msg, snippet):
    issues.append(Issue(severity, str(path), line, fn, rule, msg, snippet.strip()))


def audit_body(path: Path, fn: str, start_line: int, body: str, issues: list[Issue]) -> None:
    checks = [
        ("ERROR", "NO COLLECTION OPERATORS", re.compile(r"\.(forEach|map|filter|flatMap|sorted|groupBy)\s*\{"), "Collection operator/lambda in hot path."),
        ("ERROR", "NO COLLECTION ALLOCATION", re.compile(r"\b(listOf|mutableListOf|mapOf|setOf|arrayListOf|hashMapOf)\s*\("), "Collection allocation in hot path."),
        ("WARN", "GENERIC COLLECTION TYPE", re.compile(r"\b(List|MutableList|Map|Set|Sequence)<"), "Generic collection type in hot path. Verify no allocation/iterator."),
        ("ERROR", "NO COROUTINES", re.compile(r"\b(launch|async|withContext|Flow|StateFlow|LiveData)\b"), "Coroutine/reactive primitive in hot path."),
        ("WARN", "TEMP ARRAY ALLOCATION", re.compile(r"\b(IntArray|FloatArray|ShortArray|ByteArray|Array)\s*\("), "Array allocation in hot path. Preallocate outside."),
        ("WARN", "OBJECT CONSTRUCTION REVIEW", re.compile(r"\b[A-Z][A-Za-z0-9_]*\s*\("), "Constructor-like call in hot path. Verify value class/no allocation."),
        ("WARN", "STRING BUILDING REVIEW", re.compile(r'".*\$[A-Za-z_{]|\+.*"'), "String interpolation/concatenation in hot path."),
        ("ERROR", "NO JVM TIME/THREAD", re.compile(r"\b(System\.nanoTime|System\.currentTimeMillis|Thread|Runtime)\b"), "JVM timing/threading in hot path."),
        ("WARN", "FOR LOOP REVIEW", re.compile(r"\bfor\s*\("), "for-loop may allocate depending on iterable. Prefer while in hot paths."),
    ]

    lines = body.splitlines()
    for idx, line in enumerate(lines, start_line):
        stripped = line.strip()
        if not stripped or stripped.startswith("//"):
            continue
        for severity, rule, rx, msg in checks:
            if rx.search(line):
                # Reduce false positives for common primitive/math calls.
                if rule == "OBJECT CONSTRUCTION REVIEW":
                    if re.search(r"\b(max|min|abs|sin|cos|tan|floor|ceil|round)\s*\(", line):
                        continue
                    if re.search(r"\b(IntArray|FloatArray|ShortArray|ByteArray|Array)\s*\(", line):
                        continue
                add(issues, severity, path, idx, fn, rule, msg, line)


def run(root: Path) -> list[Issue]:
    issues: list[Issue] = []
    for path in iter_kt(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        for fn, start_line, body in find_functions(text):
            audit_body(path, fn, start_line, body, issues)
    return issues


def print_text(issues):
    if not issues:
        print("HOT LOOP AUDIT: PASS")
        return
    errors = sum(1 for i in issues if i.severity == "ERROR")
    warns = sum(1 for i in issues if i.severity == "WARN")
    print(f"HOT LOOP AUDIT: REVIEW  errors={errors} warnings={warns}")
    for i in issues:
        print()
        print(f"{i.severity}: {i.rule} in {i.function}()")
        print(f"  {i.file}:{i.line}")
        print(f"  {i.message}")
        if i.snippet:
            print(f"  > {i.snippet}")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Audit Kotlin hot paths for allocation-prone patterns.")
    parser.add_argument("root", nargs="?", default=".", type=Path)
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--strict", action="store_true", help="Treat warnings as failure.")
    args = parser.parse_args(argv)

    issues = run(args.root.resolve())
    if args.json:
        print(json.dumps([asdict(i) for i in issues], indent=2))
    else:
        print_text(issues)

    has_errors = any(i.severity == "ERROR" for i in issues)
    has_warns = any(i.severity == "WARN" for i in issues)
    return 1 if has_errors or (args.strict and has_warns) else 0


if __name__ == "__main__":
    raise SystemExit(main())
