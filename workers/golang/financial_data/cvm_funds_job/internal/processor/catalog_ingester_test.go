package processor

import (
	"encoding/csv"
	"strings"
	"testing"

	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/shared"
)

func TestBuildManagerByRegistroFundoAndParseFundRow(t *testing.T) {
	managerReader := csv.NewReader(strings.NewReader(strings.Join([]string{
		"ID_Registro_Fundo;Gestor",
		"fund-1;Gestor Correto",
		"fund-2;Outro Gestor",
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

	t.Run("uses joined gestor instead of controlador", func(t *testing.T) {
		row := []string{
			"12.345.678/0001-90",
			"Classe XPTO",
			"Renda Fixa",
			"FIC",
			"Controlador Errado",
			"EM FUNCIONAMENTO NORMAL",
			"fund-1",
		}
		cols := shared.ColumnIndex([]string{
			"CNPJ_Classe",
			"Denominacao_Social",
			"Classificacao_Anbima",
			"Tipo_Classe",
			"Controlador",
			"Situacao",
			"ID_Registro_Fundo",
		})

		record, err := parseFundRow(row, cols, rcvm175Cols, resolver)
		if err != nil {
			t.Fatalf("parseFundRow() error = %v", err)
		}

		if got, want := record.Manager, "Gestor Correto"; got != want {
			t.Fatalf("record.Manager = %q, want %q", got, want)
		}
	})

	t.Run("falls back to empty manager when join has no match", func(t *testing.T) {
		row := []string{
			"12.345.678/0001-90",
			"Classe XPTO",
			"Renda Fixa",
			"FIC",
			"Controlador Errado",
			"EM FUNCIONAMENTO NORMAL",
			"unknown",
		}
		cols := shared.ColumnIndex([]string{
			"CNPJ_Classe",
			"Denominacao_Social",
			"Classificacao_Anbima",
			"Tipo_Classe",
			"Controlador",
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
}
