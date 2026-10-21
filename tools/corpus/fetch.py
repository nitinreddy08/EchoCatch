#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Download and verify the inputs listed in sources.json into the data directory.

Usage: fetch.py [--data DIR] [--pin] [id ...]

The data directory defaults to $ECHOCATCH_DATA or tools/corpus/data (ignored by git). Downloads are verified against
the pinned SHA-256. An entry with an empty checksum is refused unless --pin is given, which records the checksum of
what was downloaded into sources.json so the next run is verified.
"""
import argparse
import hashlib
import json
import os
import shutil
import sys
import tarfile
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCES = HERE / "sources.json"


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    tmp = target.with_suffix(target.suffix + ".part")
    with urllib.request.urlopen(url, timeout=120) as response, tmp.open("wb") as out:
        shutil.copyfileobj(response, out, 1 << 20)
    tmp.replace(target)


def extract(archive: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    name = archive.name
    if name.endswith((".tar.bz2", ".tar.gz", ".tgz")):
        with tarfile.open(archive) as tar:
            # Strip one leading directory so every source lands directly under its id.
            members = tar.getmembers()
            prefix = os.path.commonpath([m.name for m in members if m.name]) if members else ""
            for member in members:
                relative = Path(member.name).relative_to(prefix) if prefix else Path(member.name)
                if str(relative) in ("", "."):
                    continue
                target = (destination / relative).resolve()
                if destination.resolve() not in target.parents and target != destination.resolve():
                    raise ValueError(f"unsafe path in archive: {member.name}")
                member.name = str(relative)
                tar.extract(member, destination, filter="data")
    elif name.endswith(".zip"):
        with zipfile.ZipFile(archive) as archive_zip:
            for member in archive_zip.namelist():
                target = (destination / member).resolve()
                if destination.resolve() not in target.parents:
                    raise ValueError(f"unsafe path in archive: {member}")
            archive_zip.extractall(destination)
    else:
        shutil.copy(archive, destination / name)


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", default=os.environ.get("ECHOCATCH_DATA", str(HERE / "data")))
    parser.add_argument("--pin", action="store_true", help="record checksums for entries that have none")
    parser.add_argument("ids", nargs="*")
    args = parser.parse_args(argv[1:])

    data_dir = Path(args.data)
    manifest = json.loads(SOURCES.read_text(encoding="utf-8"))
    changed = False
    for source in manifest["sources"]:
        if args.ids and source["id"] not in args.ids:
            continue
        archive = data_dir / "downloads" / source["url"].rsplit("/", 1)[-1]
        destination = data_dir / source["extracts_to"]
        if destination.exists() and archive.exists() and source["sha256"] and sha256_of(archive) == source["sha256"]:
            print(f"ok (already present): {source['id']}")
            continue
        print(f"downloading {source['id']} ...")
        download(source["url"], archive)
        actual = sha256_of(archive)
        if not source["sha256"]:
            if not args.pin:
                print(f"error: {source['id']} has no pinned checksum (actual {actual}); re-run with --pin to record it")
                return 1
            source["sha256"], source["bytes"] = actual, archive.stat().st_size
            changed = True
        elif actual != source["sha256"]:
            print(f"error: checksum mismatch for {source['id']}: expected {source['sha256']}, got {actual}")
            archive.unlink()
            return 1
        extract(archive, destination)
        print(f"verified and extracted: {source['id']} -> {destination}")
    if changed:
        SOURCES.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        print("updated sources.json with new checksums")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
