from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import audit  # noqa: E402


def git(root: Path, *args: str, commit_date: str | None = None) -> None:
    env = os.environ.copy()
    if commit_date:
        env["GIT_AUTHOR_DATE"] = commit_date
        env["GIT_COMMITTER_DATE"] = commit_date
    subprocess.run(
        ["git", *args],
        cwd=root,
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=True,
    )


class AuditTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / "docs/one").mkdir(parents=True)
        (self.root / "docs/two").mkdir(parents=True)
        git(self.root, "init")
        git(self.root, "config", "user.email", "audit@example.test")
        git(self.root, "config", "user.name", "Audit Test")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_links_frontmatter_orphans_duplicates_and_uncommitted(self) -> None:
        self.write(
            "docs/README.md",
            "---\ntitle: Root\n---\n"
            "[relative](one/dup.md)\n"
            "[absolute](/docs/two/dup.md)\n"
            "[broken](missing.md)\n"
            "[external](https://example.com)\n",
        )
        self.write("docs/one/dup.md", "first document\n")
        self.write("docs/two/dup.md", "second document\n")

        result = audit.audit_repository(
            self.root,
            as_of=date(2026, 7, 30),
        )

        self.assertEqual(result["markdown"]["files"], 3)
        self.assertEqual(result["links"]["internal"], 3)
        self.assertEqual(result["links"]["broken"], 1)
        self.assertEqual(result["frontmatter"]["present"], 1)
        self.assertEqual(result["orphans"]["files"], ["docs/README.md"])
        self.assertEqual(result["duplicate_basenames"]["names"], 1)
        self.assertEqual(result["history"]["uncommitted"], 3)

    def test_staleness_and_followed_rename(self) -> None:
        self.write("docs/old.md", "---\ntitle: Old\n---\nold\n")
        git(self.root, "add", "docs/old.md")
        git(
            self.root,
            "commit",
            "-m",
            "add old doc",
            commit_date="2025-01-01T12:00:00+0000",
        )
        git(self.root, "mv", "docs/old.md", "docs/renamed.md")
        git(self.root, "commit", "-m", "rename doc", commit_date="2026-07-01T12:00:00+0000")

        result = audit.audit_repository(
            self.root,
            as_of=date(2026, 7, 30),
        )

        self.assertEqual(result["history"]["stale_over_180_days"], 0)
        self.assertEqual(result["history"]["uncommitted"], 0)


if __name__ == "__main__":
    unittest.main()
