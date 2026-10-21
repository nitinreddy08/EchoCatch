#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Validate docs/third-party.json and render docs/third-party.md (ADR-0010).

Usage:
  check_third_party.py            validate the register and check the rendered markdown is current
  check_third_party.py --render   rewrite docs/third-party.md from the register

Every dependency, model file, dataset and asset must be listed with an SPDX licence that is on the
GPL-3.0-compatible allowlist below. Anything else needs an accepted ADR, named in "exception_adr".
"""
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
REGISTER = REPO / "docs" / "third-party.json"
RENDERED = REPO / "docs" / "third-party.md"
ADR_DIR = REPO / "docs" / "adr"

# ADR-0010: GPL-3.0-compatible allowlist. Prefer CC0 and CC-BY-4.0 for models, datasets and assets.
ALLOWED_LICENSES = {
    "Apache-2.0",
    "MIT",
    "BSD-2-Clause",
    "BSD-3-Clause",
    "ISC",
    "MPL-2.0",
    "LGPL-2.1-or-later",
    "LGPL-3.0-only",
    "LGPL-3.0-or-later",
    "GPL-3.0-only",
    "GPL-3.0-or-later",
    "CC0-1.0",
    "CC-BY-4.0",
    "LicenseRef-PublicDomain",  # public-domain works, datasets only (ADR-0010 clarification)
}
SCOPES = {"build", "test", "runtime", "model", "data", "asset"}
REQUIRED_FIELDS = ("name", "version", "license", "scope", "shipped", "source", "purpose")
ADR_REF = re.compile(r"^ADR-(\d{4})$")


def validate(data, adr_dir=ADR_DIR):
    errors = []
    seen = set()
    for index, component in enumerate(data.get("components", [])):
        label = component.get("name", f"components[{index}]")
        for field in REQUIRED_FIELDS:
            if field not in component or component[field] in ("", None):
                errors.append(f"{label}: missing field '{field}'")
        if errors and any(e.startswith(f"{label}:") for e in errors):
            continue
        key = (component["name"], component["version"])
        if key in seen:
            errors.append(f"{label}: duplicate entry {key}")
        seen.add(key)
        if component["scope"] not in SCOPES:
            errors.append(f"{label}: scope '{component['scope']}' is not one of {sorted(SCOPES)}")
        if not isinstance(component["shipped"], bool):
            errors.append(f"{label}: 'shipped' must be true or false")
        licence = component["license"]
        if licence in ALLOWED_LICENSES:
            continue
        exception = component.get("exception_adr")
        if exception and ADR_REF.match(exception) and list(adr_dir.glob(f"{exception[4:]}-*.md")):
            continue
        errors.append(
            f"{label}: licence '{licence}' is not on the GPL-3.0-compatible allowlist "
            f"(add 'exception_adr' naming an accepted ADR if the owner approved it)"
        )
    for index, rejected in enumerate(data.get("rejected", [])):
        label = rejected.get("name", f"rejected[{index}]")
        for field in ("name", "license", "reason"):
            if not rejected.get(field):
                errors.append(f"{label}: missing field '{field}' in rejected entry")
    return errors


def render(data):
    lines = [
        "# Third-party components",
        "",
        "Generated from `docs/third-party.json` by `python3 tools/ci/check_third_party.py --render`. Do not edit by hand.",
        "Allowed licences are defined in [ADR-0010](adr/0010-license-gpl-3.0.md).",
        "",
        "## In use",
        "",
        "| Name | Version | Licence | Scope | Shipped | Purpose |",
        "|---|---|---|---|---|---|",
    ]
    for c in sorted(data.get("components", []), key=lambda c: (c["name"].lower(), c["version"])):
        shipped = "yes" if c["shipped"] else "no"
        name = f"[{c['name']}]({c['source']})"
        lines.append(f"| {name} | {c['version']} | {c['license']} | {c['scope']} | {shipped} | {c['purpose']} |")
    lines += ["", "## Evaluated and rejected", "", "| Name | Licence | Reason |", "|---|---|---|"]
    for r in sorted(data.get("rejected", []), key=lambda r: r["name"].lower()):
        lines.append(f"| {r['name']} | {r['license']} | {r['reason']} |")
    return "\n".join(lines) + "\n"


def main(argv):
    if len(argv) > 2 or (len(argv) == 2 and argv[1] != "--render"):
        print(__doc__.strip(), file=sys.stderr)
        return 2
    data = json.loads(REGISTER.read_text(encoding="utf-8"))
    errors = validate(data)
    for error in errors:
        print(f"error: {error}")
    if errors:
        return 1
    rendered = render(data)
    if len(argv) == 2:
        RENDERED.write_text(rendered, encoding="utf-8")
        print(f"wrote {RENDERED}")
        return 0
    if not RENDERED.exists() or RENDERED.read_text(encoding="utf-8") != rendered:
        print("error: docs/third-party.md is out of date; run: python3 tools/ci/check_third_party.py --render")
        return 1
    print(f"ok: {len(data.get('components', []))} component(s), register and markdown are current")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
