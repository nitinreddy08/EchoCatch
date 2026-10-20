#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail if the native core includes Android or JNI headers (ADR-0006).

The native pipeline must build on a plain Linux host, so tests and the benchmark run without a phone.
Usage: check_native_purity.py <directory>
"""
import re
import sys
from pathlib import Path

SOURCE_SUFFIXES = {".h", ".hpp", ".hh", ".c", ".cc", ".cpp", ".cxx", ".inl"}
FORBIDDEN = re.compile(
    r'^\s*#\s*include\s*[<"]'
    r"(android/|jni\.h|aaudio/|oboe/|SLES/|OpenSLES|sys/system_properties\.h)",
    re.MULTILINE,
)


def find_violations(root: Path):
    violations = []
    for path in sorted(root.rglob("*")):
        if path.suffix not in SOURCE_SUFFIXES or not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in FORBIDDEN.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            violations.append((path, line, match.group(0).strip()))
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
        print(f"{path}:{line}: forbidden include in native core: {text}")
    if violations:
        print("native core must not depend on Android (ADR-0006)", file=sys.stderr)
        return 1
    print(f"ok: no Android dependencies under {root}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
