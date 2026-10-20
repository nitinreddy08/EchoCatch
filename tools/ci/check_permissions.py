#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail if an Android manifest or APK requests INTERNET or any permission outside the approved list (ADR-0007).

Usage:
  check_permissions.py --profile <app|spikes> --manifest <AndroidManifest.xml>
  check_permissions.py --profile <app|spikes> --aapt-dump <file>      (output of: aapt2 dump permissions app.apk)
  check_permissions.py --profile <app|spikes> --apk <app.apk>         (runs aapt2 found via ANDROID_HOME)

Use the *merged* manifest or the built APK in CI, because libraries can add permissions to the source manifest.
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

APPROVED_FILE = Path(__file__).resolve().parent / "approved-permissions.txt"
NEVER_ALLOWED = {"android.permission.INTERNET"}
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
AAPT_LINE = re.compile(r"^\s*uses-permission[\w-]*:\s*name='([^']+)'", re.MULTILINE)


def load_profiles(text):
    profiles = {}
    current = None
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        if line.startswith("[") and line.endswith("]"):
            current = line[1:-1]
            profiles[current] = {"names": set(), "suffixes": set(), "includes": []}
        elif current is None:
            raise ValueError(f"entry before any [profile]: {line}")
        elif line.startswith("@"):
            profiles[current]["includes"].append(line[1:])
        elif line.startswith("*."):
            profiles[current]["suffixes"].add(line[1:])
        else:
            profiles[current]["names"].add(line)
    return profiles


def resolve(profiles, name, seen=()):
    if name not in profiles:
        raise KeyError(f"unknown profile '{name}'; known: {sorted(profiles)}")
    if name in seen:
        raise ValueError(f"profile include cycle at '{name}'")
    names = set(profiles[name]["names"])
    suffixes = set(profiles[name]["suffixes"])
    for included in profiles[name]["includes"]:
        inc_names, inc_suffixes = resolve(profiles, included, seen + (name,))
        names |= inc_names
        suffixes |= inc_suffixes
    return names, suffixes


def permissions_from_manifest(text):
    root = ET.fromstring(text)
    found = set()
    for element in root.iter():
        if element.tag.startswith("uses-permission"):
            name = element.get(ANDROID_NS + "name")
            if name:
                found.add(name)
    return found


def permissions_from_aapt_dump(text):
    return set(AAPT_LINE.findall(text))


def violations(requested, names, suffixes):
    problems = []
    for permission in sorted(requested):
        if permission in NEVER_ALLOWED:
            problems.append(f"{permission}: never allowed (offline app, ADR-0007)")
        elif permission in names or any(permission.endswith(s) for s in suffixes):
            continue
        else:
            problems.append(f"{permission}: not on the approved list for this profile")
    return problems


def find_aapt2():
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not home:
        raise RuntimeError("ANDROID_HOME is not set; cannot locate aapt2")
    candidates = sorted(glob.glob(os.path.join(home, "build-tools", "*", "aapt2")))
    if not candidates:
        raise RuntimeError(f"no aapt2 under {home}/build-tools")
    return candidates[-1]


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--profile", required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--manifest")
    source.add_argument("--aapt-dump")
    source.add_argument("--apk")
    args = parser.parse_args(argv[1:])

    try:
        names, suffixes = resolve(load_profiles(APPROVED_FILE.read_text(encoding="utf-8")), args.profile)
        if args.manifest:
            requested = permissions_from_manifest(Path(args.manifest).read_text(encoding="utf-8"))
        elif args.aapt_dump:
            requested = permissions_from_aapt_dump(Path(args.aapt_dump).read_text(encoding="utf-8"))
        else:
            dump = subprocess.run(
                [find_aapt2(), "dump", "permissions", args.apk], capture_output=True, text=True, check=True
            ).stdout
            requested = permissions_from_aapt_dump(dump)
    except (KeyError, ValueError, RuntimeError, OSError, ET.ParseError, subprocess.CalledProcessError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    problems = violations(requested, names, suffixes)
    for problem in problems:
        print(f"error: {problem}")
    if problems:
        return 1
    print(f"ok: {len(requested)} permission(s) requested, all approved for profile '{args.profile}'")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
