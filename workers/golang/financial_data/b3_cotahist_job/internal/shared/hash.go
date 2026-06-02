package shared

import (
	"crypto/sha256"
	"fmt"
	"strings"
)

// ComputeHash returns the lowercase hex SHA-256 of data.
func ComputeHash(data []byte) string {
	h := sha256.Sum256(data)
	return fmt.Sprintf("%x", h)
}

// ComputeAssetHash produces a deterministic hash of a B3 asset's mutable catalog fields.
// Fields in alphabetical key order: assetType|especi|name|ticker
//
// ISIN is intentionally excluded: options share the underlying equity's ISIN in
// COTAHIST, so including it would cause spurious hash mismatches when an ISIN
// conflict forces the stored value to NULL on the first insert.
func ComputeAssetHash(ticker, name, assetType, especi string) string {
	canonical := fmt.Sprintf("%s|%s|%s|%s",
		norm(assetType),
		norm(especi),
		norm(name),
		norm(ticker),
	)
	return ComputeHash([]byte(canonical))
}

// ComputePriceHash produces a deterministic hash for one B3 daily price row.
// Re-downloading the same COTAHIST file produces identical hashes → DB update skipped.
func ComputePriceHash(ticker, date string, open, high, low, closePrice float64) string {
	canonical := fmt.Sprintf("%s|%s|%.8f|%.8f|%.8f|%.8f",
		norm(ticker),
		date,
		open,
		high,
		low,
		closePrice,
	)
	return ComputeHash([]byte(canonical))
}

// norm trims whitespace and normalises the string "null" → "" for canonical hashing.
func norm(s string) string {
	s = strings.TrimSpace(s)
	if strings.EqualFold(s, "null") {
		return ""
	}
	return s
}
