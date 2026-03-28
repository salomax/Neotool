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

// ComputeCashDividendHash produces a deterministic hash for one cash dividend row.
// Includes rate so corrections to the value are detected and re-applied.
func ComputeCashDividendHash(isinCode, dividendType, lastDatePrior, paymentDate, approvedOn string, rate float64) string {
	canonical := fmt.Sprintf("%s|%s|%s|%s|%s|%.8f",
		norm(isinCode),
		norm(dividendType),
		norm(lastDatePrior),
		norm(paymentDate),
		norm(approvedOn),
		rate,
	)
	return ComputeHash([]byte(canonical))
}

// ComputeStockEventHash produces a deterministic hash for one stock event row.
func ComputeStockEventHash(isinCode, eventType, lastDatePrior, approvedOn string, factor *float64) string {
	factorStr := ""
	if factor != nil {
		factorStr = fmt.Sprintf("%.8f", *factor)
	}
	canonical := fmt.Sprintf("%s|%s|%s|%s|%s",
		norm(isinCode),
		norm(eventType),
		norm(lastDatePrior),
		norm(approvedOn),
		factorStr,
	)
	return ComputeHash([]byte(canonical))
}

func norm(s string) string {
	s = strings.TrimSpace(s)
	if strings.EqualFold(s, "null") {
		return ""
	}
	return s
}
