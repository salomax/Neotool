"""
Kafka Message Model

Data classes and validation for institution upserted messages.
"""

from dataclasses import dataclass
from typing import Any, Dict, Optional


@dataclass
class InstitutionUpsertedMessage:
    """
    Message model for financial-data.institution.upserted Kafka topic.

    Matches the schema defined in bacen_ifdata/schemas/institution_upserted.json
    """

    schema_version: str
    event_id: str
    occurred_at: str
    source: str
    bacen_cod_inst: str
    change_type: str
    nome_instituicao: Optional[str] = None
    situacao: Optional[str] = None
    segmento_tb: Optional[str] = None
    atividade: Optional[str] = None
    tipo_consolidado_bancario: Optional[str] = None
    tipo_consolidacao: Optional[str] = None
    tipo_controle: Optional[str] = None
    uf: Optional[str] = None
    municipio: Optional[str] = None
    cod_conglomerado_financeiro: Optional[str] = None
    cod_conglomerado_prudencial: Optional[str] = None
    cnpj_instituicao_lider: Optional[str] = None
    source_hash: Optional[str] = None
    ano_mes_ref: Optional[str] = None

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "InstitutionUpsertedMessage":
        """
        Create message from dictionary.

        Args:
            data: Dictionary with message fields

        Returns:
            InstitutionUpsertedMessage instance
        """
        return cls(**data)

    def get_record_id(self) -> str:
        """
        Get record ID for idempotency (used by consumer).

        Returns:
            bacen_cod_inst as record identifier
        """
        return self.bacen_cod_inst

    def validate(self) -> None:
        """
        Validate message fields.

        Raises:
            ValueError: If required fields are missing or invalid
        """
        if not self.bacen_cod_inst or not self.bacen_cod_inst.strip():
            raise ValueError("bacen_cod_inst is required")
        if self.change_type not in ("CREATED", "UPDATED"):
            raise ValueError(f"Invalid change_type: {self.change_type}")

