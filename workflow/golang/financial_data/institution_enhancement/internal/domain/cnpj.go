package domain

import (
	"errors"
	"fmt"
	"regexp"
	"strconv"
)

var (
	// ErrInvalidCNPJFormat is returned when CNPJ format is invalid
	ErrInvalidCNPJFormat = errors.New("invalid CNPJ format: must be exactly 14 digits")

	// ErrInvalidBase12Digits is returned when base 12 digits are not 12 characters
	ErrInvalidBase12Digits = errors.New("base digits must be exactly 12 characters")

	// ErrEmptyBacenCode is returned when bacen_cod_inst is empty
	ErrEmptyBacenCode = errors.New("bacen_cod_inst cannot be empty")
)

// ValidateCNPJFormat validates that a CNPJ string is exactly 14 digits.
// It strips non-digit characters before validation.
func ValidateCNPJFormat(cnpj string) bool {
	if cnpj == "" {
		return false
	}

	// Extract only digits
	digits := extractDigits(cnpj)

	// Must be exactly 14 digits
	return len(digits) == 14
}

// CalculateCNPJValidatorDigits calculates the two validator digits for a CNPJ.
//
// The CNPJ validator algorithm:
// - First validator digit uses weights: [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
// - Second validator digit uses weights: [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
// - If mod 11 < 2, digit = 0, otherwise digit = 11 - mod
//
// Args:
//   base12Digits: First 12 digits of CNPJ (8 base + 4 branch)
//
// Returns:
//   (firstValidatorDigit, secondValidatorDigit, error)
func CalculateCNPJValidatorDigits(base12Digits string) (int, int, error) {
	if len(base12Digits) != 12 {
		return 0, 0, fmt.Errorf("%w: got %d digits", ErrInvalidBase12Digits, len(base12Digits))
	}

	// Validate all characters are digits
	for _, c := range base12Digits {
		if c < '0' || c > '9' {
			return 0, 0, fmt.Errorf("%w: contains non-digit character", ErrInvalidBase12Digits)
		}
	}

	// First validator digit: weights [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
	weights1 := []int{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
	sum1 := 0
	for i := 0; i < 12; i++ {
		digit, _ := strconv.Atoi(string(base12Digits[i]))
		sum1 += digit * weights1[i]
	}
	mod1 := sum1 % 11
	digit1 := 0
	if mod1 >= 2 {
		digit1 = 11 - mod1
	}

	// Second validator digit: weights [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
	base13 := base12Digits + strconv.Itoa(digit1)
	weights2 := []int{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
	sum2 := 0
	for i := 0; i < 13; i++ {
		digit, _ := strconv.Atoi(string(base13[i]))
		sum2 += digit * weights2[i]
	}
	mod2 := sum2 % 11
	digit2 := 0
	if mod2 >= 2 {
		digit2 = 11 - mod2
	}

	return digit1, digit2, nil
}

// ConstructCNPJ constructs a full CNPJ from bacen_cod_inst.
//
// Process:
// 1. Extract digits only from bacen_cod_inst
// 2. Pad to 8 digits with leading zeros
// 3. Append "0001" (matrix branch code) → 12 digits
// 4. Calculate 2 validator digits
// 5. Return full 14-digit CNPJ
//
// Args:
//   bacenCodInst: BACEN institution code (8 digits or less)
//
// Returns:
//   Full 14-digit CNPJ string
func ConstructCNPJ(bacenCodInst string) (string, error) {
	digits := extractDigits(bacenCodInst)
	if digits == "" {
		return "", ErrEmptyBacenCode
	}

	// Pad to 8 digits with leading zeros (or truncate if longer)
	var base8 string
	if len(digits) >= 8 {
		// Take first 8 digits if longer than 8
		base8 = digits[:8]
	} else {
		// Pad with leading zeros if shorter than 8
		base8 = padLeft(digits, 8, '0')
	}

	// Append matrix branch code "0001"
	base12 := base8 + "0001"

	// Calculate validator digits
	digit1, digit2, err := CalculateCNPJValidatorDigits(base12)
	if err != nil {
		return "", err
	}

	// Construct full CNPJ
	cnpj := base12 + strconv.Itoa(digit1) + strconv.Itoa(digit2)

	return cnpj, nil
}

// padLeft pads a string on the left with a character to reach the desired length
func padLeft(s string, length int, padChar rune) string {
	if len(s) >= length {
		return s
	}
	padding := make([]rune, length-len(s))
	for i := range padding {
		padding[i] = padChar
	}
	return string(padding) + s
}

// ConstructParentCNPJ determines the parent CNPJ for an institution.
//
// Logic:
// 1. If cnpjLider is provided, construct CNPJ from it (8 digits base)
// 2. If not provided and bacenCodInst doesn't start with 'C', construct from bacenCodInst
// 3. If bacenCodInst starts with 'C', return error (no identifiable root CNPJ)
//
// Args:
//   bacenCodInst: BACEN institution code (may start with 'C' for composite codes)
//   cnpjLider: Optional parent institution base code (8 digits)
//
// Returns:
//   Parent CNPJ string (14 digits) or error if not constructible
func ConstructParentCNPJ(bacenCodInst string, cnpjLider *string) (string, error) {
	// Priority 1: If cnpjLider is provided, construct CNPJ from it
	if cnpjLider != nil && *cnpjLider != "" {
		return ConstructCNPJ(*cnpjLider)
	}

	// Priority 2: Check if bacenCodInst starts with 'C' (composite code)
	if bacenCodInst != "" && bacenCodInst[0] == 'C' {
		return "", fmt.Errorf("bacen_cod_inst starts with 'C' (%s): no identifiable root CNPJ", bacenCodInst)
	}

	// Priority 3: Construct from bacen_cod_inst (numeric code)
	if bacenCodInst == "" {
		return "", ErrEmptyBacenCode
	}

	return ConstructCNPJ(bacenCodInst)
}

// extractDigits removes all non-digit characters from a string
func extractDigits(s string) string {
	re := regexp.MustCompile(`\D`)
	return re.ReplaceAllString(s, "")
}

// FormatCNPJ formats a 14-digit CNPJ string with standard formatting.
// Example: "12345678000195" → "12.345.678/0001-95"
func FormatCNPJ(cnpj string) string {
	digits := extractDigits(cnpj)
	if len(digits) != 14 {
		return cnpj // Return as-is if not 14 digits
	}

	return fmt.Sprintf("%s.%s.%s/%s-%s",
		digits[0:2],
		digits[2:5],
		digits[5:8],
		digits[8:12],
		digits[12:14])
}
