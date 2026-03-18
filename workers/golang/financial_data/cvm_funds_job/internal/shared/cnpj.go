package shared

import "strings"

// NormalizeCNPJ strips non-digit characters and zero-pads to 14 digits.
// Returns an empty string if the input contains no digits.
func NormalizeCNPJ(raw string) string {
	var b strings.Builder
	for _, r := range raw {
		if r >= '0' && r <= '9' {
			b.WriteRune(r)
		}
	}
	d := b.String()
	if d == "" {
		return ""
	}
	for len(d) < 14 {
		d = "0" + d
	}
	return d
}
