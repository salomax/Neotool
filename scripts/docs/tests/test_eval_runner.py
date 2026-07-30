from __future__ import annotations

import importlib.util
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path


RUN_PATH = Path(__file__).resolve().parents[1] / "eval/run.py"
SPEC = importlib.util.spec_from_file_location("sdd_eval_run", RUN_PATH)
assert SPEC and SPEC.loader
runner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = runner
SPEC.loader.exec_module(runner)


class EvalRunnerTest(unittest.TestCase):
    def test_usage_and_cycle_parsing(self) -> None:
        events = [
            {"type": "item.completed", "item": {"type": "reasoning"}},
            {"type": "item.completed", "item": {"type": "command_execution"}},
            {"type": "item.completed", "item": {"type": "reasoning"}},
            {
                "type": "turn.completed",
                "usage": {
                    "input_tokens": 120,
                    "cached_input_tokens": 20,
                    "output_tokens": 30,
                    "reasoning_output_tokens": 10,
                },
            },
        ]
        usage = runner.parse_usage(events)
        self.assertIsNotNone(usage)
        self.assertEqual(usage["total_tokens"], 150)
        self.assertEqual(runner.count_agent_cycles(events), 2)

    def test_missing_telemetry_is_not_zero(self) -> None:
        self.assertIsNone(runner.parse_usage([{"type": "turn.completed"}]))

    def test_rollout_metrics_count_model_cycles(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            sessions = Path(temporary) / "sessions/2026/07/30"
            sessions.mkdir(parents=True)
            rollout = sessions / "rollout-test-session-123.jsonl"
            records = []
            for cycle in range(2):
                records.append(
                    {
                        "type": "event_msg",
                        "payload": {
                            "type": "token_count",
                            "info": {
                                "last_token_usage": {
                                    "input_tokens": 10 + cycle,
                                    "output_tokens": 2,
                                },
                                "total_token_usage": {
                                    "input_tokens": 20 + cycle,
                                    "output_tokens": 4,
                                },
                            },
                        },
                    }
                )
            rollout.write_text(
                "\n".join(json.dumps(record) for record in records),
                encoding="utf-8",
            )
            previous = os.environ.get("CODEX_HOME")
            os.environ["CODEX_HOME"] = temporary
            try:
                usage, cycles = runner.rollout_metrics("123")
            finally:
                if previous is None:
                    os.environ.pop("CODEX_HOME", None)
                else:
                    os.environ["CODEX_HOME"] = previous
        self.assertEqual(cycles, 2)
        self.assertEqual(usage["total_tokens"], 25)

    def test_path_normalization_and_strict_score(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            cited = runner.normalize_paths(
                [
                    str(root / "docs/current.md"),
                    "./service/current.kt",
                    "client/src/stale.ts",
                ],
                root,
            )
        score = runner.compute_score(
            cited,
            ["docs/current.md", "service/current.kt"],
            ["client/src/"],
        )
        self.assertFalse(score["correct"])
        self.assertEqual(score["missing_expected_files"], [])
        self.assertEqual(score["forbidden_files_cited"], ["client/src/stale.ts"])

    def test_resume_loads_only_complete_results(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "results.json"
            path.write_text(
                json.dumps(
                    {
                        "results": [
                            {"id": "complete", "status": "complete"},
                            {"id": "invalid", "status": "invalid"},
                        ]
                    }
                ),
                encoding="utf-8",
            )
            loaded = runner.load_existing_results(path)
        self.assertEqual(list(loaded), ["complete"])


if __name__ == "__main__":
    unittest.main()
