package domain

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestValidateCNPJFormat(t *testing.T) {
	tests := []struct {
		name     string
		cnpj     string
		expected bool
	}{
		{"Valid 14 digits", "12345678000195", true},
		{"Valid with formatting", "12.345.678/0001-95", true},
		{"Empty string", "", false},
		{"Too short", "123456", false},
		{"Too long", "123456780001951234", false},
		{"Contains letters", "1234567800019A", false},
		{"Valid with leading zeros", "00000001000161", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ValidateCNPJFormat(tt.cnpj)
			assert.Equal(t, tt.expected, result)
		})
	}
}

func TestCalculateCNPJValidatorDigits(t *testing.T) {
	tests := []struct {
		name          string
		base12        string
		expectedDigit1 int
		expectedDigit2 int
		expectError   bool
	}{
		{"Valid CNPJ 1", "123456780001", 9, 5, false},
		{"Valid CNPJ 2", "000000010001", 3, 6, false}, // Corrected: calculated values are 3, 6
		{"Too short", "12345", 0, 0, true},
		{"Too long", "1234567800011", 0, 0, true},
		{"Contains non-digits", "12345678000A", 0, 0, true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			digit1, digit2, err := CalculateCNPJValidatorDigits(tt.base12)

			if tt.expectError {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expectedDigit1, digit1)
				assert.Equal(t, tt.expectedDigit2, digit2)
			}
		})
	}
}

func TestConstructCNPJ(t *testing.T) {
	tests := []struct {
		name         string
		bacenCodInst string
		expected     string
		expectError  bool
	}{
		{"Valid 8-digit code", "12345678", "12345678000195", false},
		{"Valid code with leading zeros needed", "1", "00000001000136", false}, // Corrected: calculated digits are 3, 6
		{"Empty string", "", "", true},
		{"Code with formatting (stripped)", "123.456.78", "12345678000195", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := ConstructCNPJ(tt.bacenCodInst)

			if tt.expectError {
				assert.Error(t, err)
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, result)
			}
		})
	}
}

func TestConstructParentCNPJ(t *testing.T) {
	cnpjLider8Digits := "48795256"
	cnpjLider14Digits := "98765432000198" // Already has correct validator digits

	tests := []struct {
		name         string
		bacenCodInst string
		cnpjLider    *string
		expected     string
		expectError  bool
	}{
		// Priority 1: Use cnpj_lider if provided (8 digits base)
		{"With cnpj_lider (8 digits)", "C0081256", &cnpjLider8Digits, "48795256000169", false},
		{"With cnpj_lider (14 digits - extract base)", "C0081256", &cnpjLider14Digits, "98765432000198", false},

		// Priority 2: Use bacen_cod_inst if numeric (doesn't start with 'C')
		{"Without cnpj_lider, numeric bacen", "12345678", nil, "12345678000195", false},
		{"With empty cnpj_lider, numeric bacen", "12345678", new(string), "12345678000195", false},

		// Priority 3: Skip if bacen_cod_inst starts with 'C' and no cnpj_lider
		{"Bacen starts with C, no cnpj_lider", "C0081256", nil, "", true},
		{"Bacen starts with C, empty cnpj_lider", "C0081256", new(string), "", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result, err := ConstructParentCNPJ(tt.bacenCodInst, tt.cnpjLider)

			if tt.expectError {
				assert.Error(t, err)
				assert.Contains(t, err.Error(), "starts with 'C'")
			} else {
				assert.NoError(t, err)
				assert.Equal(t, tt.expected, result)
			}
		})
	}
}

func TestFormatCNPJ(t *testing.T) {
	tests := []struct {
		name     string
		cnpj     string
		expected string
	}{
		{"Valid unformatted", "12345678000195", "12.345.678/0001-95"},
		{"Already formatted", "12.345.678/0001-95", "12.345.678/0001-95"},
		{"Invalid length", "123", "123"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := FormatCNPJ(tt.cnpj)
			assert.Equal(t, tt.expected, result)
		})
	}
}
