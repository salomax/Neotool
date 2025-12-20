"""Tasks package for BACEN IFData ingestion workflows."""

from .quarter_determination import determine_target_quarter

__all__ = ["determine_target_quarter"]
