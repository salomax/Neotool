from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class ExtractionMetadata(BaseModel):
    page_count: int
    tables_found: int
    bank_name_hint: str | None
    extraction_duration_ms: int


class ExtractionResult(BaseModel):
    extracted_text: str
    metadata: ExtractionMetadata


class SubmittedPayload(BaseModel):
    model_config = ConfigDict(extra="allow")

    job_id: str
    asset_id: str | None = None
    download_url: str
    document_type: str
    callback_context: dict[str, Any] = Field(default_factory=dict)


class DlqPayload(BaseModel):
    job_id: str | None = None
    asset_id: str | None = None
    document_type: str | None = None
    error: str
    callback_context: dict[str, Any] = Field(default_factory=dict)
