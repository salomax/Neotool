from __future__ import annotations

from typing import Any, Optional

from pydantic import BaseModel, ConfigDict, Field


class ExtractionMetadata(BaseModel):
    page_count: int
    tables_found: int
    bank_name_hint: Optional[str]
    extraction_duration_ms: int


class ExtractionResult(BaseModel):
    extracted_text: str
    metadata: ExtractionMetadata


class SubmittedPayload(BaseModel):
    model_config = ConfigDict(extra="allow")

    job_id: str
    asset_id: Optional[str] = None
    download_url: str
    password: Optional[str] = None
    document_type: str
    callback_context: dict[str, Any] = Field(default_factory=dict)


class DlqPayload(BaseModel):
    job_id: Optional[str] = None
    asset_id: Optional[str] = None
    document_type: Optional[str] = None
    error: str
    error_code: Optional[str] = None
    callback_context: dict[str, Any] = Field(default_factory=dict)
