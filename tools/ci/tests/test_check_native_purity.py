# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check_native_purity.py"
spec = importlib.util.spec_from_file_location("check_native_purity", SCRIPT)
purity = importlib.util.module_from_spec(spec)
spec.loader.exec_module(purity)


class NativePurityTest(unittest.TestCase):
    def check(self, files):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for name, content in files.items():
                path = root / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
            return purity.find_violations(root)

    def test_clean_code_passes(self):
        self.assertEqual(self.check({"a.cc": '#include <vector>\n#include "echocatch/pipeline.h"\n'}), [])

    def test_android_header_is_rejected(self):
        found = self.check({"src/a.cc": "#include <android/log.h>\n"})
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0][1], 1)

    def test_jni_and_oboe_are_rejected(self):
        found = self.check({"a.h": "#include <jni.h>\n", "b.cc": '#include "oboe/Oboe.h"\n'})
        self.assertEqual(len(found), 2)

    def test_non_source_files_are_ignored(self):
        self.assertEqual(self.check({"notes.md": "#include <jni.h>\n"}), [])

    def test_main_returns_nonzero_on_violation(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "a.cc").write_text("#include <aaudio/AAudio.h>\n", encoding="utf-8")
            self.assertEqual(purity.main(["check_native_purity.py", tmp]), 1)

    def test_main_rejects_bad_arguments(self):
        self.assertEqual(purity.main(["check_native_purity.py"]), 2)
        self.assertEqual(purity.main(["check_native_purity.py", "/definitely/not/a/dir"]), 2)


if __name__ == "__main__":
    unittest.main()
