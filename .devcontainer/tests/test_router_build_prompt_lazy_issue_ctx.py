"""Unit tests for lazy issue context injection in router.build_prompt()."""
from __future__ import annotations

from unittest.mock import MagicMock, patch

import pytest

import router


@pytest.fixture
def base_state() -> dict:
    """Base thread state with no issue."""
    return {
        "current_stage": 0,
        "issue_number": None,
    }


@pytest.fixture
def state_with_issue_in_thread() -> dict:
    """Thread state where issue #42 was set from a previous stage."""
    return {
        "current_stage": 1,
        "issue_number": 42,
    }


class TestQuestionIntentLazyIssueContext:
    """Test that 'question' intent only injects issue context when referenced in current message."""

    def test_question_without_issue_reference_does_not_fetch(
        self, base_state: dict
    ) -> None:
        """question intent with no issue in message should NOT call fetch_issue_context."""
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "What is the best practice for error handling?"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            prompt = router.build_prompt(classification, message, base_state)

        # fetch_issue_context should NOT be called
        mock_fetch.assert_not_called()
        # Prompt should just be the original message, no issue context
        assert prompt == message

    def test_question_with_issue_in_state_no_reference_does_not_fetch(
        self, state_with_issue_in_thread: dict
    ) -> None:
        """question intent with issue in state but NOT in message should NOT fetch."""
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "How should I approach this?"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            prompt = router.build_prompt(
                classification, message, state_with_issue_in_thread
            )

        # fetch_issue_context should NOT be called
        mock_fetch.assert_not_called()
        # Prompt should just be the original message
        assert prompt == message

    def test_question_with_issue_reference_in_message_does_fetch(
        self, base_state: dict
    ) -> None:
        """question intent where message references #42 should fetch and inject context."""
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "What is the design approach for issue #42?"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #42: Add feature X ===\nBody of issue."
            prompt = router.build_prompt(classification, message, base_state)

        # fetch_issue_context SHOULD be called with issue 42
        mock_fetch.assert_called_once_with(42)
        # Prompt should include both message and issue context
        assert message in prompt
        assert "=== Issue #42: Add feature X ===" in prompt

    def test_question_with_issue_in_state_and_reference_does_fetch(
        self, state_with_issue_in_thread: dict
    ) -> None:
        """question intent where message references an issue: fetch the message-referenced issue.

        For question intent, we only fetch if the issue is referenced in the CURRENT message.
        Since the message references #99, we fetch #99 (not #42 from state).
        """
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "What's the approach for #99?"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #99: Another feature ==="
            prompt = router.build_prompt(
                classification, message, state_with_issue_in_thread
            )

        # Should fetch the issue from the message (#99), not from state (#42)
        mock_fetch.assert_called_once_with(99)
        assert "=== Issue #99: Another feature ===" in prompt


class TestOtherIntentsIssueContextBehavior:
    """Test that other intents continue to inject issue context when issue_number is present."""

    def test_stage_design_with_issue_in_state_fetches(
        self, state_with_issue_in_thread: dict
    ) -> None:
        """stage_design should fetch issue context when issue_number is in state."""
        classification = {
            "intent": "stage_design",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "Design the solution"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #42: Design this ==="
            prompt = router.build_prompt(
                classification, message, state_with_issue_in_thread
            )

        # Should fetch issue #42 from state
        mock_fetch.assert_called_once_with(42)
        assert "=== Issue #42: Design this ===" in prompt

    def test_stage_impl_with_issue_in_state_fetches(
        self, state_with_issue_in_thread: dict
    ) -> None:
        """stage_impl should fetch issue context when issue_number is in state."""
        classification = {
            "intent": "stage_impl",
            "agent": "codex",
            "model": None,
            "issue_number": None,
        }
        message = "Implement the solution"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #42: Implementation ==="
            prompt = router.build_prompt(
                classification, message, state_with_issue_in_thread
            )

        # Should fetch issue #42 from state
        mock_fetch.assert_called_once_with(42)
        assert "=== Issue #42: Implementation ===" in prompt

    def test_stage_review_without_issue_does_not_fetch(self, base_state: dict) -> None:
        """stage_review without issue_number should not fetch."""
        classification = {
            "intent": "stage_review",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "Review the code"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            prompt = router.build_prompt(classification, message, base_state)

        # Should NOT fetch when there's no issue_number anywhere
        mock_fetch.assert_not_called()

    def test_fast_track_with_issue_in_classification_fetches(
        self, base_state: dict
    ) -> None:
        """fast_track with issue_number in classification should fetch."""
        classification = {
            "intent": "fast_track",
            "agent": None,
            "model": None,
            "issue_number": 42,
        }
        message = "Do everything"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #42: Full pipeline ==="
            prompt = router.build_prompt(classification, message, base_state)

        # Should fetch issue #42 from classification
        mock_fetch.assert_called_once_with(42)
        assert "=== Issue #42: Full pipeline ===" in prompt


class TestEdgeCases:
    """Test edge cases and boundary conditions."""

    def test_question_with_issue_in_classification_only_fetches(
        self, base_state: dict
    ) -> None:
        """question with issue_number ONLY in classification (not message) should NOT fetch.

        This tests that question respects the new lazy behavior: it only fetches
        if the issue is referenced in the CURRENT message, not from classification.
        """
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": 42,  # Issue in classification, not in message
        }
        message = "General question"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            prompt = router.build_prompt(classification, message, base_state)

        # Should NOT fetch because the issue wasn't referenced in the message
        mock_fetch.assert_not_called()
        assert prompt == message

    def test_empty_issue_context_still_returns_prompt(
        self, state_with_issue_in_thread: dict
    ) -> None:
        """When fetch_issue_context returns empty string, prompt format depends on intent."""
        classification = {
            "intent": "stage_design",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "Design this"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = ""  # Empty response
            prompt = router.build_prompt(
                classification, message, state_with_issue_in_thread
            )

        # Should fetch but get empty
        mock_fetch.assert_called_once_with(42)
        # stage_design uses SKILL_MAP, so it formats as: skill + message + issue number
        # (not just the message)
        assert "/neotool-solution-design" in prompt
        assert "Design this" in prompt
        assert "Issue: #42" in prompt

    def test_question_with_multiple_issue_references_uses_first(
        self, base_state: dict
    ) -> None:
        """question with multiple issue references should fetch the first one."""
        classification = {
            "intent": "question",
            "agent": "claude",
            "model": None,
            "issue_number": None,
        }
        message = "Compare #42 and #99"

        with patch.object(router, "fetch_issue_context") as mock_fetch:
            mock_fetch.return_value = "=== Issue #42 ==="
            prompt = router.build_prompt(classification, message, base_state)

        # Should fetch the first issue found in the message
        mock_fetch.assert_called_once_with(42)
        assert "=== Issue #42 ===" in prompt
