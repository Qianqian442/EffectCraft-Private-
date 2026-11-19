import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SourceLockTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / "compatibility/sources.lock.json").read_text(encoding="utf-8"))
        self.by_id = {x["id"]: x for x in self.data["sources"]}

    def test_all_sources_are_pinned(self):
        for item in self.data["sources"]:
            self.assertRegex(item["commit"], r"^[0-9a-f]{40}$")
            self.assertIn("/", item["repo"])
            self.assertTrue(item["license"])

    def test_android_main_path_is_present(self):
        for source in ("winlator", "wine", "box64", "box86", "proot"):
            self.assertIn(source, self.by_id)

    def test_fex_is_not_marked_android_ready(self):
        self.assertEqual(self.by_id["fex"]["tier"], "research_only")
        self.assertEqual(self.by_id["fex"]["status"], "not_android_target")

    def test_server_verification_is_not_bypassed(self):
        policy = self.data["policy"]
        self.assertTrue(policy["doNotBypassAndroidSandboxOrAeServerVerification"])
        self.assertTrue(policy["runWindowsProgramsOutOfProcess"])


if __name__ == "__main__":
    unittest.main()
