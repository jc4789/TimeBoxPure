#!/usr/bin/env python3
"""
law_check.py

Heuristic repo audit for the Carmack-but-ZUN Kotlin Multiplatform engine.

This is intentionally a fast "goblin trap", not a Kotlin compiler plugin.
It finds likely law violations and prints actionable reports.

Usage:
  python tools/law_check.py .
  python tools/law_check.py . --json
  python tools/law_check.py . --strict
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable


FORBIDDEN_ASSET_EXTS = {
    ".png", ".jpg", ".jpeg", ".svg", ".ttf", ".otf",
    ".wav", ".ogg", ".mp3", ".json", ".xml", ".yaml", ".yml",
}

SOURCE_EXTS = {".kt", ".kts", ".gradle", ".md", ".txt", ".properties"}

SKIP_DIRS = {
    ".git", ".gradle", ".idea", "build", ".kotlin", ".konan",
    "node_modules", ".m2", ".cache", "out",
}

COMMONMAIN_PARTS = {"commonMain"}
PLATFORM_PARTS = {"androidMain", "iosMain", "mingwX64Main", "jvmMain"}

OWNERSHIP_MARKERS = {
    "SCOPED", "BORROWED", "NATIVE_OWNED", "PINNED_SYNC", "STABLE_REF",
}

@dataclass
class Issue:
    severity: str
    file: str
    line: int
    rule: str
    message: str
    snippet: str = ""


def is_skipped(path: Path) -> bool:
    return any(part in SKIP_DIRS for part in path.parts)


def iter_files(root: Path) -> Iterable[Path]:
    for p in root.rglob("*"):
        if p.is_file() and not is_skipped(p):
            yield p


def is_common_main(path: Path) -> bool:
    return any(part in COMMONMAIN_PARTS for part in path.parts)


def looks_core_path(path: Path) -> bool:
    text = str(path).replace("\\", "/").lower()
    return (
        "commonmain" in text or
        "/core" in text or
        "core-engine" in text or
        "/engine/" in text
    )


def line_iter(path: Path):
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return
    for i, line in enumerate(text.splitlines(), 1):
        yield i, line


def add(issues, severity, path, line, rule, msg, snippet=""):
    issues.append(Issue(severity, str(path), line, rule, msg, snippet.strip()))


def check_assets(root: Path, issues: list[Issue]) -> None:
    for p in iter_files(root):
        if p.suffix.lower() in FORBIDDEN_ASSET_EXTS:
            add(
                issues, "ERROR", p, 1, "ASSET LAW",
                f"Forbidden runtime/content asset extension {p.suffix!r}. Use procedural or compact mathematical representation.",
            )


def check_source_patterns(root: Path, issues: list[Issue]) -> None:
    compose = re.compile(r"androidx\.compose|@Composable\b|Jetpack\s+Compose", re.I)
    java = re.compile(r"\bjava\.|\bSystem\.currentTimeMillis\b|\bSystem\.nanoTime\b|\bRuntime\b|\bThread\b")
    framework_ui = re.compile(r"\bSwiftUI\b|\bHTML\b|\bDOM\b|XML UI", re.I)
    asset_string = re.compile(r"""["'][^"']+\.(png|jpe?g|svg|ttf|otf|wav|ogg|mp3|json|xml|ya?ml)["']""", re.I)
    loaders = re.compile(r"\b(loadTexture|loadImage|loadFont|loadSound|readText|readBytes|File\()", re.I)
    native_color = re.compile(r"\b(ARGB|RGBA|android\.graphics\.Color|Color\(|0x[0-9A-Fa-f]{8}\b)")
    collections = re.compile(r"\b(listOf|mutableListOf|mapOf|setOf|List<|MutableList<|Map<|Set<|Sequence<)|\.(forEach|map|filter|flatMap|sorted|groupBy)\s*\{")
    coroutine = re.compile(r"\b(launch|async|Flow|StateFlow|LiveData)\b")

    for p in iter_files(root):
        if p.suffix.lower() not in SOURCE_EXTS:
            continue
        for line_no, line in line_iter(p) or []:
            if compose.search(line):
                add(issues, "ERROR", p, line_no, "NO UI FRAMEWORKS", "Compose / @Composable detected. Core UI must be software-rendered IMGUI math.", line)
            if framework_ui.search(line) and looks_core_path(p):
                add(issues, "ERROR", p, line_no, "NO UI FRAMEWORKS", "Framework UI term detected in likely core path.", line)
            if is_common_main(p) and java.search(line):
                add(issues, "ERROR", p, line_no, "NO JAVA IN COMMONMAIN", "JVM/Java assumption detected in commonMain.", line)
            if asset_string.search(line) or loaders.search(line):
                add(issues, "ERROR", p, line_no, "ASSET LAW", "Asset path or loading API detected.", line)
            if is_common_main(p) and native_color.search(line):
                add(issues, "WARN", p, line_no, "COLOR LAW", "Native color / ARGB / RGBA appears in commonMain. Core should use palette indices 0..15.", line)
            if looks_core_path(p) and coroutine.search(line):
                add(issues, "ERROR", p, line_no, "NO COROUTINES IN CORE", "Coroutine/reactive scheduling primitive detected in core-like path.", line)
            if looks_core_path(p) and collections.search(line):
                add(issues, "WARN", p, line_no, "COLLECTIONS REVIEW", "Collection or collection operator detected in core-like path. Verify this is not a hot path.", line)


def check_magic_numbers(root: Path, issues: list[Issue]) -> None:
    layout_file = re.compile(r"(layout|imgui|ui|button|menu|hud|render|renderer|draw|glyph|text)", re.I)
    numeric = re.compile(r"(?<![A-Za-z0-9_])(-?\d+(?:\.\d+)?f?)(?![A-Za-z0-9_])")
    harmless = {"0", "1", "2", "3", "4", "8", "16", "0f", "1f", "2f", "3f", "4f", "8f", "16f"}
    allow_context = re.compile(r"\bconst\s+val\b|\bval\s+U\b|\bGLYPH\b|\bPALETTE\b|\bMIN_SAFE_LOGICAL_WIDTH\b|\bMAX_SAFE_LOGICAL_WIDTH\b|\bLABEL_COLUMN_RATIO\b")
    suspicious_assign = re.compile(r"\b(padding|margin|height|width|x|y|left|right|top|bottom|hit|touch|scale|radius|border|spacing)\b", re.I)

    for p in iter_files(root):
        if p.suffix.lower() != ".kt":
            continue
        if not layout_file.search(p.name) and not layout_file.search(str(p.parent)):
            continue
        for line_no, line in line_iter(p) or []:
            stripped = line.strip()
            if stripped.startswith("//") or allow_context.search(line):
                continue
            if not suspicious_assign.search(line):
                continue
            nums = [m.group(1) for m in numeric.finditer(line)]
            bad_nums = [n for n in nums if n not in harmless]
            if bad_nums:
                add(
                    issues, "WARN", p, line_no, "NO UNEXPLAINED CONSTANTS",
                    f"Suspicious layout/render literal(s) {bad_nums}. Derive from U, display variables, or named constants.",
                    line,
                )


def run(root: Path) -> list[Issue]:
    issues: list[Issue] = []
    check_assets(root, issues)
    check_source_patterns(root, issues)
    check_magic_numbers(root, issues)
    return issues


def print_text(issues: list[Issue]) -> None:
    if not issues:
        print("LAW CHECK: PASS")
        return
    errors = sum(1 for i in issues if i.severity == "ERROR")
    warns = sum(1 for i in issues if i.severity == "WARN")
    print(f"LAW CHECK: FAIL-ish  errors={errors} warnings={warns}")
    for issue in issues:
        print()
        print(f"{issue.severity}: {issue.rule}")
        print(f"  {issue.file}:{issue.line}")
        print(f"  {issue.message}")
        if issue.snippet:
            print(f"  > {issue.snippet}")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Audit Carmack-but-ZUN engine laws.")
    parser.add_argument("root", nargs="?", default=".", type=Path)
    parser.add_argument("--json", action="store_true", help="Emit JSON report.")
    parser.add_argument("--strict", action="store_true", help="Treat warnings as failing exit status.")
    args = parser.parse_args(argv)

    root = args.root.resolve()
    issues = run(root)

    if args.json:
        print(json.dumps([asdict(i) for i in issues], indent=2))
    else:
        print_text(issues)

    has_errors = any(i.severity == "ERROR" for i in issues)
    has_warns = any(i.severity == "WARN" for i in issues)
    return 1 if has_errors or (args.strict and has_warns) else 0


if __name__ == "__main__":
    raise SystemExit(main())
