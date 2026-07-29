package shared

import "testing"

func TestComputeFundHashIncludesEveryNewRawField(t *testing.T) {
	base := func() string {
		return ComputeFundHash(
			"12345678000190",
			"Fund Name",
			"Renda Fixa",
			"FIC",
			"Gestor Base",
			"Administrador Base",
			"S",
			"Investidores em Geral",
			"12345.67",
			"2026-07-28",
			"11222333000144",
			"Auditor Base",
			"99888777000166",
			"Custodiante Base",
			true,
		)
	}

	if got, want := base(), base(); got != want {
		t.Fatalf("ComputeFundHash() should be deterministic: got %q want %q", got, want)
	}

	mutations := map[string]string{
		"manager":                 ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Alterado", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"administrador":           ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Alterado", "S", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"tributacao_longo_prazo":  ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "N", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"publico_alvo":            ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Profissional", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"patrimonio_liquido":      ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "76543.21", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"data_patrimonio_liquido": ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-29", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"cnpj_auditor":            ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-28", "00000000000100", "Auditor Base", "99888777000166", "Custodiante Base", true),
		"auditor":                 ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Alterado", "99888777000166", "Custodiante Base", true),
		"cnpj_custodiante":        ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "11111111000111", "Custodiante Base", true),
		"custodiante":             ComputeFundHash("12345678000190", "Fund Name", "Renda Fixa", "FIC", "Gestor Base", "Administrador Base", "S", "Investidores em Geral", "12345.67", "2026-07-28", "11222333000144", "Auditor Base", "99888777000166", "Custodiante Alterado", true),
	}

	original := base()
	for field, mutated := range mutations {
		if mutated == original {
			t.Fatalf("ComputeFundHash() ignored field %s", field)
		}
	}
}
