package processor

import (
	"encoding/csv"
	"strings"
	"testing"

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/shared"
)

func TestBuildManagerByRegistroFundoAndParseFundRow(t *testing.T) {
	managerReader := csv.NewReader(strings.NewReader(strings.Join([]string{
		"ID_Registro_Fundo;Administrador;Gestor",
		"fund-1;Administrador Correto;Gestor Correto",
		"fund-2;;Outro Gestor",
	}, "\n")))
	managerReader.Comma = ';'
	managerReader.ReuseRecord = true

	header, err := managerReader.Read()
	if err != nil {
		t.Fatalf("Read() header error = %v", err)
	}

	managerByRegistroFundo, err := buildManagerByRegistroFundo(managerReader, shared.ColumnIndex(header))
	if err != nil {
		t.Fatalf("buildManagerByRegistroFundo() error = %v", err)
	}

	resolver := managerFromRegistroFundoJoin(managerByRegistroFundo)

	t.Run("uses joined gestor and administrador instead of classe row data", func(t *testing.T) {
		row := []string{
			"12.345.678/0001-90",
			"Classe XPTO",
			"Renda Fixa",
			"FIC",
			"EM FUNCIONAMENTO NORMAL",
			"fund-1",
			"S",
			"Investidores em Geral",
			"12345.67",
			"2026-07-28",
			"11.222.333/0001-44",
			"Auditor XPTO",
			"99.888.777/0001-66",
			"Custodiante XPTO",
		}
		cols := shared.ColumnIndex([]string{
			"CNPJ_Classe",
			"Denominacao_Social",
			"Classificacao_Anbima",
			"Tipo_Classe",
			"Situacao",
			"ID_Registro_Fundo",
			"Tributacao_Longo_Prazo",
			"Publico_Alvo",
			"Patrimonio_Liquido",
			"Data_Patrimonio_Liquido",
			"CNPJ_Auditor",
			"Auditor",
			"CNPJ_Custodiante",
			"Custodiante",
		})

		record, err := parseFundRow(row, cols, rcvm175Cols, resolver)
		if err != nil {
			t.Fatalf("parseFundRow() error = %v", err)
		}

		if got, want := record.Manager, "Gestor Correto"; got != want {
			t.Fatalf("record.Manager = %q, want %q", got, want)
		}
		if got, want := record.Administrador, "Administrador Correto"; got != want {
			t.Fatalf("record.Administrador = %q, want %q", got, want)
		}
		if got, want := record.TributacaoLongoPrazo, "S"; got != want {
			t.Fatalf("record.TributacaoLongoPrazo = %q, want %q", got, want)
		}
		if got, want := record.PublicoAlvo, "Investidores em Geral"; got != want {
			t.Fatalf("record.PublicoAlvo = %q, want %q", got, want)
		}
		if got, want := record.PatrimonioLiquido, "12345.67"; got != want {
			t.Fatalf("record.PatrimonioLiquido = %q, want %q", got, want)
		}
		if got, want := record.DataPatrimonioLiquido, "2026-07-28"; got != want {
			t.Fatalf("record.DataPatrimonioLiquido = %q, want %q", got, want)
		}
		if got, want := record.CNPJAuditor, "11.222.333/0001-44"; got != want {
			t.Fatalf("record.CNPJAuditor = %q, want %q", got, want)
		}
		if got, want := record.Auditor, "Auditor XPTO"; got != want {
			t.Fatalf("record.Auditor = %q, want %q", got, want)
		}
		if got, want := record.CNPJCustodiante, "99.888.777/0001-66"; got != want {
			t.Fatalf("record.CNPJCustodiante = %q, want %q", got, want)
		}
		if got, want := record.Custodiante, "Custodiante XPTO"; got != want {
			t.Fatalf("record.Custodiante = %q, want %q", got, want)
		}
	})

	t.Run("keeps blank administrador when join source field is blank", func(t *testing.T) {
		row := []string{
			"12.345.678/0001-90",
			"Classe XPTO",
			"Renda Fixa",
			"FIC",
			"EM FUNCIONAMENTO NORMAL",
			"fund-2",
		}
		cols := shared.ColumnIndex([]string{
			"CNPJ_Classe",
			"Denominacao_Social",
			"Classificacao_Anbima",
			"Tipo_Classe",
			"Situacao",
			"ID_Registro_Fundo",
		})

		record, err := parseFundRow(row, cols, rcvm175Cols, resolver)
		if err != nil {
			t.Fatalf("parseFundRow() error = %v", err)
		}

		if got, want := record.Manager, "Outro Gestor"; got != want {
			t.Fatalf("record.Manager = %q, want %q", got, want)
		}
		if record.Administrador != "" {
			t.Fatalf("record.Administrador = %q, want empty string", record.Administrador)
		}
	})

	t.Run("falls back to empty manager and administrador when join has no match", func(t *testing.T) {
		row := []string{
			"12.345.678/0001-90",
			"Classe XPTO",
			"Renda Fixa",
			"FIC",
			"EM FUNCIONAMENTO NORMAL",
			"unknown",
		}
		cols := shared.ColumnIndex([]string{
			"CNPJ_Classe",
			"Denominacao_Social",
			"Classificacao_Anbima",
			"Tipo_Classe",
			"Situacao",
			"ID_Registro_Fundo",
		})

		record, err := parseFundRow(row, cols, rcvm175Cols, resolver)
		if err != nil {
			t.Fatalf("parseFundRow() error = %v", err)
		}

		if record.Manager != "" {
			t.Fatalf("record.Manager = %q, want empty string", record.Manager)
		}
		if record.Administrador != "" {
			t.Fatalf("record.Administrador = %q, want empty string", record.Administrador)
		}
	})
}

func TestParseFundRowLegacyManagerUnchanged(t *testing.T) {
	row := []string{
		"12.345.678/0001-90",
		"Fundo Legado",
		"Renda Fixa",
		"FIC",
		"Gestor Legado",
		"EM FUNCIONAMENTO NORMAL",
	}
	cols := shared.ColumnIndex([]string{
		"CNPJ_FUNDO",
		"DENOM_SOCIAL",
		"CLASSE",
		"TP_FUNDO",
		"GESTOR",
		"SITUACAO",
	})

	record, err := parseFundRow(row, cols, legacyCols, nil)
	if err != nil {
		t.Fatalf("parseFundRow() error = %v", err)
	}

	if got, want := record.Manager, "Gestor Legado"; got != want {
		t.Fatalf("record.Manager = %q, want %q", got, want)
	}
	if record.Administrador != "" || record.TributacaoLongoPrazo != "" || record.PublicoAlvo != "" ||
		record.PatrimonioLiquido != "" || record.DataPatrimonioLiquido != "" ||
		record.CNPJAuditor != "" || record.Auditor != "" ||
		record.CNPJCustodiante != "" || record.Custodiante != "" {
		t.Fatalf("legacy row should leave RCVM175-only raw fields empty: %+v", record)
	}
}
