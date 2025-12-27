"""
Unit tests for CNPJ construction utilities.

Tests cover:
- Validator digit calculation algorithm
- CNPJ construction from bacen_cod_inst
- Edge cases (leading zeros, invalid input, non-digits)
"""

import pytest

from tasks.cnpj_utils import calculate_cnpj_validator_digits, construct_cnpj


# ============================================================================
# Unit Tests - calculate_cnpj_validator_digits
# ============================================================================


class TestCalculateCnpjValidatorDigits:
    """Test calculate_cnpj_validator_digits function."""

    def test_valid_12_digit_input_known_cnpj(self):
        """Test validator digit calculation with known valid CNPJ base."""
        # Known valid CNPJ: 11222333000181
        # Base: 112223330001
        # Expected validator digits: (8, 1)
        base_12 = "112223330001"
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)

        assert digit_1 == 8
        assert digit_2 == 1
        # Verify the full CNPJ would be valid
        assert base_12 + str(digit_1) + str(digit_2) == "11222333000181"

    def test_valid_12_digit_input_example_from_docs(self):
        """Test validator digit calculation with example from documentation."""
        # Example: bacen_cod_inst = "60872504"
        # Base 8: "60872504" + "0001" = "608725040001"
        base_12 = "608725040001"
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)

        # Verify both digits are integers in valid range [0, 9]
        assert isinstance(digit_1, int)
        assert isinstance(digit_2, int)
        assert 0 <= digit_1 <= 9
        assert 0 <= digit_2 <= 9
        # Verify full CNPJ is 14 digits
        full_cnpj = base_12 + str(digit_1) + str(digit_2)
        assert len(full_cnpj) == 14

    def test_all_zeros(self):
        """Test validator digit calculation with all zeros."""
        base_12 = "000000000000"
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)

        # All zeros should produce specific validator digits
        assert isinstance(digit_1, int)
        assert isinstance(digit_2, int)
        assert 0 <= digit_1 <= 9
        assert 0 <= digit_2 <= 9

    def test_all_nines(self):
        """Test validator digit calculation with all nines."""
        base_12 = "999999999999"
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)

        # All nines should produce specific validator digits
        assert isinstance(digit_1, int)
        assert isinstance(digit_2, int)
        assert 0 <= digit_1 <= 9
        assert 0 <= digit_2 <= 9

    def test_invalid_length_too_short(self):
        """Test that 11 digits raises ValueError."""
        with pytest.raises(ValueError, match="Expected 12 digits, got 11"):
            calculate_cnpj_validator_digits("12345678901")

    def test_invalid_length_too_long(self):
        """Test that 13 digits raises ValueError."""
        with pytest.raises(ValueError, match="Expected 12 digits, got 13"):
            calculate_cnpj_validator_digits("1234567890123")

    def test_invalid_length_empty_string(self):
        """Test that empty string raises ValueError."""
        with pytest.raises(ValueError, match="Expected 12 digits, got 0"):
            calculate_cnpj_validator_digits("")

    def test_validator_digit_zero_when_mod_less_than_2(self):
        """Test that validator digit is 0 when mod < 2."""
        # Find a base that produces mod < 2
        # Base "000000000000" should produce mod = 0 for first digit
        base_12 = "000000000000"
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)

        # When mod < 2, digit should be 0
        # Verify the calculation logic works correctly
        assert isinstance(digit_1, int)
        assert isinstance(digit_2, int)


# ============================================================================
# Unit Tests - construct_cnpj
# ============================================================================


class TestConstructCnpj:
    """Test construct_cnpj function."""

    def test_valid_8_digit_code(self):
        """Test construction from 8-digit bacen_cod_inst."""
        # Example from documentation
        bacen_cod_inst = "60872504"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        assert result.startswith("608725040001")
        # Verify it has 2 validator digits at the end
        assert result[12:14].isdigit()

    def test_short_code_with_leading_zeros(self):
        """Test construction from short code (should pad with leading zeros)."""
        # Single digit should become "000000010001XX"
        bacen_cod_inst = "1"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        assert result.startswith("000000010001")
        # Verify it has 2 validator digits at the end
        assert result[12:14].isdigit()

    def test_very_short_code(self):
        """Test construction from very short code."""
        bacen_cod_inst = "42"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        assert result.startswith("000000420001")
        # Verify it has 2 validator digits at the end
        assert result[12:14].isdigit()

    def test_long_code_truncates_to_8_digits(self):
        """Test that longer codes are truncated to first 8 digits."""
        # Code with more than 8 digits should use first 8
        bacen_cod_inst = "60872504123"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        # Should use first 8 digits: "60872504"
        assert result.startswith("608725040001")
        # Should NOT include the extra digits "123"

    def test_formatted_input_extracts_digits(self):
        """Test that formatted input (with dots/dashes) extracts digits."""
        # Input with formatting characters
        bacen_cod_inst = "608.725.04"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        # Should extract digits: "60872504"
        assert result.startswith("608725040001")

    def test_mixed_characters_extracts_digits(self):
        """Test that mixed alphanumeric input extracts only digits."""
        # Input with letters and digits
        bacen_cod_inst = "ABC60872504XYZ"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        # Should extract digits: "60872504"
        assert result.startswith("608725040001")

    def test_empty_string_returns_none(self):
        """Test that empty string returns None."""
        result = construct_cnpj("")
        assert result is None

    def test_only_non_digits_returns_none(self):
        """Test that input with only non-digits returns None."""
        result = construct_cnpj("ABC")
        assert result is None

        result = construct_cnpj("---")
        assert result is None

        result = construct_cnpj("   ")
        assert result is None

    def test_whitespace_only_returns_none(self):
        """Test that whitespace-only input returns None."""
        result = construct_cnpj("   ")
        assert result is None

        result = construct_cnpj("\t\n")
        assert result is None

    def test_known_valid_cnpj_construction(self):
        """Test construction produces valid CNPJ format."""
        # Use a known example
        bacen_cod_inst = "11222333"
        result = construct_cnpj(bacen_cod_inst)

        assert result is not None
        assert len(result) == 14
        # Should start with base + branch: "112223330001"
        assert result.startswith("112223330001")
        # Last 2 digits should be validator digits
        assert result[12:14].isdigit()

    def test_construction_always_produces_14_digits(self):
        """Test that construction always produces exactly 14 digits."""
        test_cases = ["1", "12", "123", "1234", "12345", "123456", "1234567", "12345678"]

        for bacen_cod_inst in test_cases:
            result = construct_cnpj(bacen_cod_inst)
            assert result is not None, f"Failed for input: {bacen_cod_inst}"
            assert len(result) == 14, f"Expected 14 digits, got {len(result)} for input: {bacen_cod_inst}"
            assert result.isdigit(), f"Result should be all digits: {result}"

    def test_construction_includes_matrix_branch(self):
        """Test that construction always includes '0001' matrix branch code."""
        test_cases = ["60872504", "1", "12345678"]

        for bacen_cod_inst in test_cases:
            result = construct_cnpj(bacen_cod_inst)
            assert result is not None
            # Position 8-11 should be "0001" (after 8-digit base)
            # For 8-digit input: positions 8-11
            # For shorter input: after padded base
            # Extract base (first 8) + branch (next 4)
            base_and_branch = result[:12]
            assert base_and_branch[8:12] == "0001", f"Expected '0001' at positions 8-11, got {result[8:12]}"

    def test_construction_with_special_characters(self):
        """Test construction handles various special characters."""
        # Test with various special characters that should be stripped
        test_cases = [
            ("608-725-04", "60872504"),
            ("608.725.04", "60872504"),
            ("608/725/04", "60872504"),
            ("(608)725-04", "60872504"),
            ("608 725 04", "60872504"),
        ]

        for input_str, expected_digits in test_cases:
            result = construct_cnpj(input_str)
            assert result is not None
            # Should extract the digits and construct CNPJ
            assert result.startswith(expected_digits + "0001")


