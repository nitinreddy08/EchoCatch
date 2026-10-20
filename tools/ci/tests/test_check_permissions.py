# SPDX-License-Identifier: GPL-3.0-only
import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check_permissions.py"
spec = importlib.util.spec_from_file_location("check_permissions", SCRIPT)
perm = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perm)

MANIFEST = """<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.example">
  <uses-permission android:name="android.permission.RECORD_AUDIO"/>
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
  <uses-permission-sdk-23 android:name="android.permission.POST_NOTIFICATIONS"/>
  <uses-permission android:name="org.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"/>
  %s
</manifest>"""

AAPT = """package: org.example
uses-permission: name='android.permission.RECORD_AUDIO'
uses-permission-sdk-23: name='android.permission.POST_NOTIFICATIONS'
%s
"""


def run(profile, kind, body):
    with tempfile.TemporaryDirectory() as tmp:
        path = Path(tmp) / "input"
        path.write_text(body, encoding="utf-8")
        return perm.main(["check_permissions.py", "--profile", profile, f"--{kind}", str(path)])


class PermissionGuardTest(unittest.TestCase):
    def test_clean_manifest_passes(self):
        self.assertEqual(run("app", "manifest", MANIFEST % ""), 0)

    def test_internet_is_rejected_in_manifest(self):
        extra = '<uses-permission android:name="android.permission.INTERNET"/>'
        self.assertEqual(run("app", "manifest", MANIFEST % extra), 1)

    def test_internet_is_rejected_in_every_profile_even_if_listed(self):
        extra = '<uses-permission android:name="android.permission.INTERNET"/>'
        self.assertEqual(run("spikes", "manifest", MANIFEST % extra), 1)
        problems = perm.violations({"android.permission.INTERNET"}, {"android.permission.INTERNET"}, set())
        self.assertEqual(len(problems), 1)

    def test_unapproved_permission_is_rejected(self):
        extra = '<uses-permission android:name="android.permission.READ_PHONE_STATE"/>'
        self.assertEqual(run("app", "manifest", MANIFEST % extra), 1)

    def test_spikes_profile_allows_extra_but_app_does_not(self):
        extra = '<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>'
        self.assertEqual(run("spikes", "manifest", MANIFEST % extra), 0)
        self.assertEqual(run("app", "manifest", MANIFEST % extra), 1)

    def test_aapt_dump_parsing(self):
        self.assertEqual(run("app", "aapt-dump", AAPT % ""), 0)
        self.assertEqual(run("app", "aapt-dump", AAPT % "uses-permission: name='android.permission.INTERNET'"), 1)

    def test_unknown_profile_and_bad_input_are_usage_errors(self):
        self.assertEqual(run("nope", "manifest", MANIFEST % ""), 2)
        self.assertEqual(run("app", "manifest", "<not xml"), 2)

    def test_profile_parsing_supports_includes_and_suffixes(self):
        profiles = perm.load_profiles("[a]\nx.A\n*.SUF\n[b]\n@a\nx.B\n")
        names, suffixes = perm.resolve(profiles, "b")
        self.assertEqual(names, {"x.A", "x.B"})
        self.assertEqual(suffixes, {".SUF"})

    def test_include_cycle_is_detected(self):
        profiles = perm.load_profiles("[a]\n@b\n[b]\n@a\n")
        with self.assertRaises(ValueError):
            perm.resolve(profiles, "a")


if __name__ == "__main__":
    unittest.main()
