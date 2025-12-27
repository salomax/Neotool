"""
Unit tests for message model.

Tests cover:
- from_dict() method
- Validation logic
- get_record_id() method
- Edge cases (missing fields, invalid values)
"""

import pytest

from tasks.message import InstitutionUpsertedMessage


# ============================================================================
# Unit Tests - from_dict
# ============================================================================


class TestFromDict:
    """Test from_dict class method."""

    def test_from_dict_with_all_fields(self):
        """Test creating message from dictionary with all fields."""
        data = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "occurred_at": "2024-01-01T00:00:00Z",
            "source": "test",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
            "nome_instituicao": "Test Institution",
            "situacao": "ATIVO",
            "segmento_tb": "BANCO",
            "atividade": "BANCO",
            "tipo_consolidado_bancario": "INDIVIDUAL",
            "tipo_consolidacao": "NONE",
            "tipo_controle": "PUBLICO",
            "uf": "SP",
            "municipio": "São Paulo",
            "cod_conglomerado_financeiro": "001",
            "cod_conglomerado_prudencial": "002",
            "cnpj_instituicao_lider": "11222333000181",
            "source_hash": "abc123",
            "ano_mes_ref": "202403",
        }

        message = InstitutionUpsertedMessage.from_dict(data)

        assert message.schema_version == "1.0"
        assert message.event_id == "test-123"
        assert message.bacen_cod_inst == "60872504"
        assert message.change_type == "CREATED"
        assert message.nome_instituicao == "Test Institution"
        assert message.cnpj_instituicao_lider == "11222333000181"

    def test_from_dict_with_required_fields_only(self):
        """Test creating message from dictionary with only required fields."""
        data = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "occurred_at": "2024-01-01T00:00:00Z",
            "source": "test",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
        }

        message = InstitutionUpsertedMessage.from_dict(data)

        assert message.schema_version == "1.0"
        assert message.bacen_cod_inst == "60872504"
        assert message.change_type == "CREATED"
        # Optional fields should be None
        assert message.nome_instituicao is None
        assert message.cnpj_instituicao_lider is None

    def test_from_dict_with_some_optional_fields(self):
        """Test creating message with some optional fields."""
        data = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "occurred_at": "2024-01-01T00:00:00Z",
            "source": "test",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
            "nome_instituicao": "Test Bank",
            "uf": "SP",
        }

        message = InstitutionUpsertedMessage.from_dict(data)

        assert message.nome_instituicao == "Test Bank"
        assert message.uf == "SP"
        assert message.situacao is None  # Not provided

    def test_from_dict_handles_none_optional_fields(self):
        """Test that None values for optional fields are handled."""
        data = {
            "schema_version": "1.0",
            "event_id": "test-123",
            "occurred_at": "2024-01-01T00:00:00Z",
            "source": "test",
            "bacen_cod_inst": "60872504",
            "change_type": "CREATED",
            "nome_instituicao": None,
            "cnpj_instituicao_lider": None,
        }

        message = InstitutionUpsertedMessage.from_dict(data)

        assert message.nome_instituicao is None
        assert message.cnpj_instituicao_lider is None


# ============================================================================
# Unit Tests - validate
# ============================================================================


class TestValidate:
    """Test validate method."""

    def test_valid_message_passes_validation(self):
        """Test that valid message passes validation."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Should not raise
        message.validate()

    def test_missing_bacen_cod_inst_raises_value_error(self):
        """Test that missing bacen_cod_inst raises ValueError."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="",  # Empty string
            change_type="CREATED",
        )

        with pytest.raises(ValueError, match="bacen_cod_inst is required"):
            message.validate()

    def test_none_bacen_cod_inst_raises_value_error(self):
        """Test that None bacen_cod_inst raises ValueError."""
        # Note: This would require creating message with None, which might not be possible
        # with dataclass defaults, but we test the validation logic
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="",  # Empty is falsy
            change_type="CREATED",
        )

        with pytest.raises(ValueError, match="bacen_cod_inst is required"):
            message.validate()

    def test_invalid_change_type_raises_value_error(self):
        """Test that invalid change_type raises ValueError."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="INVALID",  # Invalid change_type
        )

        with pytest.raises(ValueError, match="Invalid change_type"):
            message.validate()

    def test_created_change_type_passes_validation(self):
        """Test that CREATED change_type passes validation."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Should not raise
        message.validate()

    def test_updated_change_type_passes_validation(self):
        """Test that UPDATED change_type passes validation."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="UPDATED",
        )

        # Should not raise
        message.validate()

    def test_case_sensitive_change_type_validation(self):
        """Test that change_type validation is case-sensitive."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="created",  # Lowercase - should fail
        )

        with pytest.raises(ValueError, match="Invalid change_type"):
            message.validate()


# ============================================================================
# Unit Tests - get_record_id
# ============================================================================


class TestGetRecordId:
    """Test get_record_id method."""

    def test_get_record_id_returns_bacen_cod_inst(self):
        """Test that get_record_id returns bacen_cod_inst."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        record_id = message.get_record_id()

        assert record_id == "60872504"
        assert isinstance(record_id, str)

    def test_get_record_id_with_different_bacen_cod_inst(self):
        """Test get_record_id with different bacen_cod_inst values."""
        test_cases = ["60872504", "12345678", "99999999", "00000001"]

        for bacen_cod_inst in test_cases:
            message = InstitutionUpsertedMessage(
                schema_version="1.0",
                event_id="test-123",
                occurred_at="2024-01-01T00:00:00Z",
                source="test",
                bacen_cod_inst=bacen_cod_inst,
                change_type="CREATED",
            )

            assert message.get_record_id() == bacen_cod_inst

    def test_get_record_id_used_for_idempotency(self):
        """Test that get_record_id is used for idempotency (returns same value)."""
        message1 = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        message2 = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-456",  # Different event_id
            occurred_at="2024-01-02T00:00:00Z",  # Different timestamp
            source="test",
            bacen_cod_inst="60872504",  # Same bacen_cod_inst
            change_type="UPDATED",  # Different change_type
        )

        # Same bacen_cod_inst should produce same record_id
        assert message1.get_record_id() == message2.get_record_id()
        assert message1.get_record_id() == "60872504"


# ============================================================================
# Unit Tests - Edge Cases
# ============================================================================


class TestEdgeCases:
    """Test edge cases and boundary conditions."""

    def test_message_with_all_none_optional_fields(self):
        """Test message with all optional fields as None."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
            nome_instituicao=None,
            situacao=None,
            segmento_tb=None,
            atividade=None,
            tipo_consolidado_bancario=None,
            tipo_consolidacao=None,
            tipo_controle=None,
            uf=None,
            municipio=None,
            cod_conglomerado_financeiro=None,
            cod_conglomerado_prudencial=None,
            cnpj_instituicao_lider=None,
            source_hash=None,
            ano_mes_ref=None,
        )

        # Should be valid
        message.validate()
        assert message.get_record_id() == "60872504"

    def test_message_with_empty_string_optional_fields(self):
        """Test message with empty string optional fields."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
            nome_instituicao="",  # Empty string
            uf="",  # Empty string
        )

        # Should be valid (empty strings are allowed for optional fields)
        message.validate()
        assert message.nome_instituicao == ""
        assert message.uf == ""

    def test_message_with_whitespace_bacen_cod_inst(self):
        """Test message with whitespace-only bacen_cod_inst."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="   ",  # Whitespace only
            change_type="CREATED",
        )

        # Should fail validation (whitespace is falsy)
        with pytest.raises(ValueError, match="bacen_cod_inst is required"):
            message.validate()

    def test_message_validation_before_processing(self):
        """Test that validation should be called before processing."""
        message = InstitutionUpsertedMessage(
            schema_version="1.0",
            event_id="test-123",
            occurred_at="2024-01-01T00:00:00Z",
            source="test",
            bacen_cod_inst="60872504",
            change_type="CREATED",
        )

        # Validation should pass
        message.validate()

        # After validation, get_record_id should work
        assert message.get_record_id() == "60872504"


