"""Flows package for BACEN IFData ingestion workflows."""

from .bacen_ifdata_ingestion import bacen_ifdata_ingestion_flow

__all__ = [
    "bacen_ifdata_ingestion_flow",
]
