#!/usr/bin/env python3
"""
cinterop_audit.py

Heuristic scanner for Kotlin/Native C interop lifetime risks.

Usage:
  python tools/cinterop_audit.py .
  python tools/cinterop_audit.py . --json
  python tools/cinterop_audit.py . --strict
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable


SKIP_DIRS = {".git", ".gradle", ".idea", "build", ".kotlin", ".konan", "node_modules", "out"}

PERSISTENT_TERMS = re.compile(
    r"\b(ma_device|ma_engine|AVAudio|AudioTrack|Metal|MTL|HWND|WNDCLASSEX|WNDPROC|"
    r"global|device|engine|audioState|windowHandle|callback|userData|userdata)\b",
    re.I,
)

OWNERSHIP = ("SCOPED", "BORROWED", "NATIVE_OWNED", "PINNED_SYNC", "STABLE_REF")

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


def iter_kt(root: Path) -> Iterable[Path]:
    for p in root.rglob("*.kt"):
        if p.is_file() and not is_skipped(p):
            yield p


def is_common_main(path: Path) -> bool:
    return "commonMain" in path.parts


def add(issues, severity, path, line, rule, msg, snippet=""):
    issues.append(Issue(severity, str(path), line, rule, msg, snippet.strip()))


def extract_blocks(text: str, keyword: str):
    pattern = re.compile(rf"\b{re.escape(keyword)}\s*\{{")
    for m in pattern.finditer(text):
        brace = text.find("{", m.start())
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
        yield start_line, text[brace:end]


def line_offset(block: str, start_line: int, offset: int) -> int:
    return start_line + block.count("\n", 0, offset)


def has_ownership_comment(lines: list[str], idx: int) -> bool:
    start = max(0, idx - 3)
    context = "\n".join(lines[start:idx + 1])
    return any(marker in context for marker in OWNERSHIP)


def audit_file(path: Path, issues: list[Issue]) -> None:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()

    if is_common_main(path) and re.search(r"\bkotlinx\.cinterop\b|\bCPointer\b|\bCOpaquePointer\b|\bmemScoped\b|\bnativeHeap\b|\bStableRef\b", text):
        add(issues, "ERROR", path, 1, "NO C INTEROP IN COMMONMAIN", "C interop symbol detected in commonMain. Move to platform source set.")

    # memScoped blocks
    for start_line, block in extract_blocks(text, "memScoped"):
        if PERSISTENT_TERMS.search(block):
            add(issues, "WARN", path, start_line, "MEMSCOPED LIFETIME REVIEW", "memScoped block mentions persistent hardware/state terms. Verify no long-lived struct/pointer is allocated here.")
        if re.search(r"\breturn\b", block):
            add(issues, "ERROR", path, start_line, "POINTER ESCAPE RISK", "return inside memScoped block. Verify no scoped pointer escapes.")
        if re.search(r"(this\.)?[A-Za-z_][A-Za-z0-9_]*\s*=\s*.*\.ptr\b", block):
            add(issues, "ERROR", path, start_line, "POINTER ESCAPE RISK", "Pointer assigned inside memScoped. Scoped pointer may escape.", block.splitlines()[0] if block else "")
        if re.search(r"\balloc<\s*(ma_device|ma_engine|WNDCLASSEX|AVAudio|MTL)", block, re.I):
            add(issues, "ERROR", path, start_line, "PERSISTENT STRUCT IN MEMSCOPED", "Persistent-looking native struct allocated in memScoped. Use nativeHeap/Arena with shutdown free path.")

    # nativeHeap lifecycle
    alloc_lines = [i + 1 for i, l in enumerate(lines) if re.search(r"\bnativeHeap\.(alloc|allocArray)\b", l)]
    free_present = any("nativeHeap.free" in l for l in lines)
    for ln in alloc_lines:
        if not free_present:
            add(issues, "ERROR", path, ln, "NATIVEHEAP WITHOUT FREE", "nativeHeap allocation found but no nativeHeap.free in this file. Add explicit shutdown/free path.", lines[ln - 1])

    # StableRef lifecycle
    stable_create_lines = [i + 1 for i, l in enumerate(lines) if "StableRef.create" in l]
    stable_dispose_present = any(".dispose()" in l for l in lines)
    for ln in stable_create_lines:
        if not stable_dispose_present:
            add(issues, "ERROR", path, ln, "STABLEREF WITHOUT DISPOSE", "StableRef.create found without visible dispose path in this file.", lines[ln - 1])

    # staticCFunction state handling
    for i, l in enumerate(lines, 1):
        if "staticCFunction" in l:
            file_text = "\n".join(lines[max(0, i - 30): min(len(lines), i + 80)])
            if "StableRef" not in file_text and "asStableRef" not in file_text:
                add(issues, "WARN", path, i, "STATIC CALLBACK REVIEW", "staticCFunction found without nearby StableRef/asStableRef. If callback needs Kotlin state, use StableRef userdata.", l)

    # Array pointer handoff
    for i, l in enumerate(lines):
        line_no = i + 1
        if re.search(r"\b(addressOf|refTo)\s*\(", l):
            context = "\n".join(lines[max(0, i - 5): i + 3])
            if "usePinned" not in context:
                add(issues, "ERROR", path, line_no, "ARRAY POINTER WITHOUT PINNING", "addressOf/refTo used without nearby usePinned. Pin Kotlin arrays for synchronous C handoff.", l)
            if re.search(r"\b(register|set|store|retain|init|start)\w*\s*\(", context, re.I):
                add(issues, "WARN", path, line_no, "PINNED POINTER RETENTION REVIEW", "Pointer may be retained by native API. usePinned is only valid for synchronous access.", l)

    # COpaquePointer / CPointer ownership comments
    for i, l in enumerate(lines):
        if re.search(r"\b(COpaquePointer|CPointer<)", l):
            if not has_ownership_comment(lines, i):
                add(issues, "WARN", path, i + 1, "MISSING OWNERSHIP COMMENT", "C pointer declaration/crossing lacks ownership marker: SCOPED, BORROWED, NATIVE_OWNED, PINNED_SYNC, or STABLE_REF.", l)


def run(root: Path) -> list[Issue]:
    issues: list[Issue] = []
    for path in iter_kt(root):
        text = path.read_text(encoding="utf-8", errors="replace")
        if any(tok in text for tok in ("kotlinx.cinterop", "CPointer", "COpaquePointer", "memScoped", "nativeHeap", "StableRef", "staticCFunction", "usePinned", "addressOf", "refTo")):
            audit_file(path, issues)
    return issues


def print_text(issues):
    if not issues:
        print("C INTEROP AUDIT: PASS")
        return
    errors = sum(1 for i in issues if i.severity == "ERROR")
    warns = sum(1 for i in issues if i.severity == "WARN")
    print(f"C INTEROP AUDIT: REVIEW  errors={errors} warnings={warns}")
    for i in issues:
        print()
        print(f"{i.severity}: {i.rule}")
        print(f"  {i.file}:{i.line}")
        print(f"  {i.message}")
        if i.snippet:
            print(f"  > {i.snippet}")


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Audit Kotlin/Native C interop pointer lifetime risks.")
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
