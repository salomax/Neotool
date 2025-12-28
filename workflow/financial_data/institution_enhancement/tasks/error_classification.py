"""
Error classification shim for institution enhancement.

Re-exports the shared logic maintained in workflow.common.error_classification.
"""

from workflow.common.error_classification import is_retryable_error

__all__ = ["is_retryable_error"]
