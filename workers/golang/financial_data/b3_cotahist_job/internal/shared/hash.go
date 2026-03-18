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
// Fields in alphabetical key order: assetType|especi|isinCode|name|ticker
func ComputeAssetHash(ticker, name, assetType, especi, isinCode string) string {
	canonical := fmt.Sprintf("%s|%s|%s|%s|%s",
		norm(assetType),
		norm(especi),
		norm(isinCode),
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
