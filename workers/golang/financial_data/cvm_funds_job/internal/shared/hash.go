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

// ComputeFundHash produces a deterministic hash of a fund's mutable catalog fields.
// Uses a pipe-delimited canonical string with fields in alphabetical key order.
// This avoids the map/sort/json.Marshal allocations of the previous implementation
// in the hot per-row loop (~500k rows per catalog import).
//
// Field order (alphabetical by key):
// administrador|auditor|classe|cnpj|cnpj_auditor|cnpj_custodiante|custodiante|
// data_patrimonio_liquido|is_active|manager|name|patrimonio_liquido|publico_alvo|
// tipo_fundo|tributacao_longo_prazo
func ComputeFundHash(
	cnpj, name, classe, tipoFundo, manager, administrador, tributacaoLongoPrazo,
	publicoAlvo, patrimonioLiquido, dataPatrimonioLiquido, cnpjAuditor, auditor,
	cnpjCustodiante, custodiante string,
	isActive bool,
) string {
	canonical := fmt.Sprintf("%s|%s|%s|%s|%s|%s|%s|%s|%t|%s|%s|%s|%s|%s|%s",
		normStr(administrador),
		normStr(auditor),
		normStr(classe),
		normStr(cnpj),
		normStr(cnpjAuditor),
		normStr(cnpjCustodiante),
		normStr(custodiante),
		normStr(dataPatrimonioLiquido),
		isActive,
		normStr(manager),
		normStr(name),
		normStr(patrimonioLiquido),
		normStr(publicoAlvo),
		normStr(tipoFundo),
		normStr(tributacaoLongoPrazo),
	)
	return ComputeHash([]byte(canonical))
}

// ComputeNavHash produces a deterministic hash for one NAV price row.
// Re-downloading the same monthly file produces identical hashes → DB update skipped.
func ComputeNavHash(cnpj, navDate string, navPerQuota float64) string {
	canonical := fmt.Sprintf("%s|%s|%.8f", normStr(cnpj), navDate, navPerQuota)
	return ComputeHash([]byte(canonical))
}

// normStr trims whitespace and normalises the string "null" → "".
func normStr(s string) string {
	s = strings.TrimSpace(s)
	if strings.EqualFold(s, "null") {
		return ""
	}
	return s
}
