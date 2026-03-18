package shared

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"sort"
	"strings"
)

// ComputeInstitutionHash computes SHA-256 hash of institution fields for change detection
// This matches the Python implementation's hash calculation
func ComputeInstitutionHash(fields map[string]interface{}) (string, error) {
	// Normalize the fields (trim strings, convert empty or "null" to nil)
	normalized := make(map[string]interface{})
	for k, v := range fields {
		if str, ok := v.(string); ok {
			trimmed := strings.TrimSpace(str)
			if trimmed == "" || strings.EqualFold(trimmed, "null") {
				normalized[k] = nil
			} else {
				normalized[k] = trimmed
			}
		} else {
			normalized[k] = v
		}
	}

	// Create a sorted JSON representation
	// We need to sort keys to ensure consistent hash
	keys := make([]string, 0, len(normalized))
	for k := range normalized {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	// Build ordered map
	orderedMap := make(map[string]interface{})
	for _, k := range keys {
		orderedMap[k] = normalized[k]
	}

	// Serialize to JSON
	jsonBytes, err := json.Marshal(orderedMap)
	if err != nil {
		return "", err
	}

	// Compute SHA-256 hash
	hash := sha256.Sum256(jsonBytes)
	return hex.EncodeToString(hash[:]), nil
}

// NormalizeString trims whitespace and converts empty strings or "null" strings to nil
func NormalizeString(s string) *string {
	trimmed := strings.TrimSpace(s)
	if trimmed == "" || strings.EqualFold(trimmed, "null") {
		return nil
	}
	return &trimmed
}
