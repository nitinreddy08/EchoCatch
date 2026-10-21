# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check_sdk_checks.py"
spec = importlib.util.spec_from_file_location("check_sdk_checks", SCRIPT)
sdk = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sdk)

CHECK = "if (Build.VERSION.SDK_INT >= 33) {}\n"


class SdkChecksTest(unittest.TestCase):
    def check(self, files):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            return [(p.relative_to(root).as_posix(), line) for p, line, _ in sdk.find_violations(root)]

    def test_check_in_the_adapter_is_allowed(self):
        self.assertEqual(self.check({"main/kotlin/x/platform/Platform.kt": CHECK}), [])

    def test_check_elsewhere_is_reported_with_its_line(self):
        found = self.check({"main/kotlin/x/ui/Home.kt": "package x\n\n" + CHECK})
        self.assertEqual(found, [("main/kotlin/x/ui/Home.kt", 3)])

    def test_check_in_a_file_that_merely_has_platform_in_its_name_is_reported(self):
        found = self.check({"main/kotlin/x/ui/Platform.kt": CHECK})
        self.assertEqual(len(found), 1)

    def test_comments_are_ignored(self):
        text = "// uses Build.VERSION.SDK_INT\n/* SDK_INT\n   again */\nval x = 1 // SDK_INT\n"
        self.assertEqual(self.check({"main/kotlin/x/A.kt": text}), [])

    def test_line_numbers_survive_block_comments(self):
        text = "/*\n one\n two\n*/\n" + CHECK
        self.assertEqual(self.check({"main/kotlin/x/A.kt": text}), [("main/kotlin/x/A.kt", 5)])

    def test_non_kotlin_files_are_ignored(self):
        self.assertEqual(self.check({"main/notes.md": CHECK}), [])

    def test_main_returns_nonzero_on_violation_and_zero_when_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "A.kt").write_text(CHECK, encoding="utf-8")
            self.assertEqual(sdk.main(["check_sdk_checks.py", tmp]), 1)
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "A.kt").write_text("val x = 1\n", encoding="utf-8")
            self.assertEqual(sdk.main(["check_sdk_checks.py", tmp]), 0)

    def test_main_rejects_missing_directory(self):
        self.assertEqual(sdk.main(["check_sdk_checks.py", "/definitely/not/here"]), 2)


if __name__ == "__main__":
    unittest.main()
