"""Unit tests for routing-aware dispatch."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import router
from agents.backend import BackendResult


def test_dispatch_uses_haiku_for_stage_issue_from_routing() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="ok", exit_code=0, elapsed_ms=100,
    )

    with patch.object(router, "_setup_impl_branch"):
        router.dispatch(
            {"intent": "stage_issue", "agent": None, "model": None},
            prompt="open issue",
            thread="t1",
            backend=mock_backend,
        )

    assert mock_backend.invoke.call_args.kwargs["model"] == "claude-haiku-4-5"
    assert mock_backend.invoke.call_args.kwargs["agent"] == "claude"


def test_dispatch_user_model_override_beats_routing() -> None:
    mock_backend = MagicMock()
    mock_backend.invoke.return_value = BackendResult(
        output="ok", exit_code=0, elapsed_ms=100,
    )

    with patch.object(router, "_setup_impl_branch"):
        router.dispatch(
            {"intent": "stage_issue", "agent": None, "model": "claude-sonnet-4-6"},
            prompt="open issue",
            thread="t1",
            backend=mock_backend,
        )

    assert mock_backend.invoke.call_args.kwargs["model"] == "claude-sonnet-4-6"
