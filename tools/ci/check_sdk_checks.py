#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail if app code reads Build.VERSION.SDK_INT outside the platform adapter (ADR-0009, M1-T8).

Differences between Android versions are answered in one place, app/src/main/kotlin/.../platform/, so supporting
API 26 up to the newest release stays reviewable. Business logic and screens ask the adapter instead of checking
the version themselves. Comments are ignored. The throwaway `spikes` app is not scanned.

Usage: check_sdk_checks.py <app source directory>
"""
import re
import sys
from pathlib import Path

ADAPTER_DIR = "platform"
SDK_CHECK = re.compile(r"\bSDK_INT\b")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    """Remove comments but keep line breaks so reported line numbers stay right."""
    text = BLOCK_COMMENT.sub(lambda m: "\n" * m.group(0).count("\n"), text)
    return LINE_COMMENT.sub("", text)


def find_violations(root: Path):
    violations = []
    for path in sorted(root.rglob("*.kt")):
        if ADAPTER_DIR in path.relative_to(root).parts[:-1]:
            continue
        code = strip_comments(path.read_text(encoding="utf-8", errors="replace"))
        for number, line in enumerate(code.splitlines(), start=1):
            if SDK_CHECK.search(line):
                violations.append((path, number, line.strip()))
    return violations


def main(argv):
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    root = Path(argv[1])
    if not root.is_dir():
        print(f"error: {root} is not a directory", file=sys.stderr)
        return 2
    violations = find_violations(root)
    for path, line, text in violations:
        print(f"{path}:{line}: SDK_INT check outside the platform adapter: {text}")
    if violations:
        print(f"move the check into a function in a '{ADAPTER_DIR}' package (ADR-0009)", file=sys.stderr)
        return 1
    print(f"ok: no SDK_INT checks outside the platform adapter under {root}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
