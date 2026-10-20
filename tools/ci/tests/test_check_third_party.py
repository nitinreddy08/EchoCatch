# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check_third_party.py"
spec = importlib.util.spec_from_file_location("check_third_party", SCRIPT)
tp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(tp)


def component(**overrides):
    base = {
        "name": "Lib",
        "version": "1.0",
        "license": "MIT",
        "scope": "runtime",
        "shipped": True,
        "source": "https://example.org/lib",
        "purpose": "testing",
    }
    base.update(overrides)
    return base


class ThirdPartyTest(unittest.TestCase):
    def errors(self, components, rejected=None, adrs=()):
        with tempfile.TemporaryDirectory() as tmp:
            adr_dir = Path(tmp)
            for name in adrs:
                (adr_dir / name).write_text("x", encoding="utf-8")
            return tp.validate({"components": components, "rejected": rejected or []}, adr_dir)

    def test_allowed_licence_passes(self):
        for licence in ("Apache-2.0", "MIT", "BSD-3-Clause", "CC0-1.0", "GPL-3.0-only"):
            self.assertEqual(self.errors([component(license=licence)]), [], licence)

    def test_non_commercial_licence_is_rejected(self):
        found = self.errors([component(license="CC-BY-NC-SA-4.0")])
        self.assertEqual(len(found), 1)
        self.assertIn("allowlist", found[0])

    def test_unknown_and_proprietary_are_rejected(self):
        self.assertTrue(self.errors([component(license="unknown")]))
        self.assertTrue(self.errors([component(license="proprietary")]))
        self.assertTrue(self.errors([component(license="GPL-2.0-only")]))

    def test_exception_needs_an_existing_adr(self):
        odd = component(license="CC-BY-SA-4.0", exception_adr="ADR-0042")
        self.assertTrue(self.errors([odd]))
        self.assertEqual(self.errors([odd], adrs=["0042-share-alike-model.md"]), [])

    def test_missing_field_is_reported(self):
        incomplete = component()
        del incomplete["purpose"]
        self.assertIn("missing field 'purpose'", self.errors([incomplete])[0])

    def test_bad_scope_and_shipped_type(self):
        self.assertTrue(self.errors([component(scope="everywhere")]))
        self.assertTrue(self.errors([component(shipped="yes")]))

    def test_duplicates_are_reported(self):
        self.assertTrue(self.errors([component(), component()]))

    def test_rejected_entries_need_a_reason(self):
        self.assertTrue(self.errors([], rejected=[{"name": "X", "license": "unknown"}]))

    def test_render_is_stable_and_sorted(self):
        data = {"components": [component(name="b"), component(name="A")], "rejected": []}
        out = tp.render(data)
        self.assertLess(out.index("[A]"), out.index("[b]"))
        self.assertEqual(out, tp.render(data))

    def test_real_register_is_valid_and_rendered(self):
        self.assertEqual(tp.main(["check_third_party.py"]), 0)


if __name__ == "__main__":
    unittest.main()
