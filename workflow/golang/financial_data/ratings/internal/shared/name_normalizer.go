package shared

import (
	"regexp"
	"strings"
)

var (
	nonAlphaNumeric     = regexp.MustCompile(`[^a-zA-Z0-9]+`)
	multipleUnderscores = regexp.MustCompile(`_+`)
)

// NormalizeRatingName converts a rating type description to UPPER_SNAKE_CASE
// Example: "Long Term Issuer Default Rating" -> "LONG_TERM_ISSUER_DEFAULT_RATING"
// Example: "National Short Term Rating" -> "NATIONAL_SHORT_TERM_RATING"
// Example: "Viability Rating" -> "VIABILITY_RATING"
func NormalizeRatingName(name string) string {
	if name == "" {
		return ""
	}

	// Replace non-alphanumeric characters with underscores
	normalized := nonAlphaNumeric.ReplaceAllString(name, "_")

	// Collapse multiple underscores
	normalized = multipleUnderscores.ReplaceAllString(normalized, "_")

	// Trim leading/trailing underscores and convert to uppercase
	normalized = strings.Trim(normalized, "_")

	return strings.ToUpper(normalized)
}
