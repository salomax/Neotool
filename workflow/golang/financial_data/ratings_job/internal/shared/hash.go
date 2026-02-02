package shared

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/google/uuid"
)

// ComputeHash computes SHA-256 hash of raw bytes and returns hex string
func ComputeHash(data []byte) string {
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:])
}

// ComputeRatingHash computes a content hash for idempotency detection
// Hash is based on: issuer_id + rating_name + rating_label + rating_date + normalized_custom_params
func ComputeRatingHash(issuerID uuid.UUID, ratingName, ratingLabel string, ratingDate time.Time, customParams map[string]interface{}) string {
	// Normalize custom params by sorting keys
	normalizedParams := NormalizeJSON(customParams)

	// Build canonical string
	canonical := fmt.Sprintf("%s|%s|%s|%s|%s",
		issuerID.String(),
		ratingName,
		strings.ToUpper(ratingLabel),
		ratingDate.Format("2006-01-02"),
		normalizedParams,
	)

	return ComputeHash([]byte(canonical))
}

// NormalizeJSON returns a deterministic JSON string with sorted keys
func NormalizeJSON(m map[string]interface{}) string {
	if m == nil || len(m) == 0 {
		return "{}"
	}

	// Get sorted keys
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)

	// Build normalized JSON
	var sb strings.Builder
	sb.WriteString("{")
	for i, k := range keys {
		if i > 0 {
			sb.WriteString(",")
		}
		v, _ := json.Marshal(m[k])
		sb.WriteString(fmt.Sprintf("%q:%s", k, string(v)))
	}
	sb.WriteString("}")

	return sb.String()
}
