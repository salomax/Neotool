//go:build integration

package processor

import (
	"archive/zip"
	"bytes"
	"context"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/database"
	cvmhttp "github.com/salomax/invistus/workers/golang/financial_data/cvm-funds/internal/infrastructure/http"
	"github.com/salomax/invistus/workflow/golang/shared/workerpool"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
)

func TestCatalogIngester_IntegrationPersistsRawFieldsAndSkipsUnchangedRerun(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test")
	}

	ctx := context.Background()
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	pool := setupPostgresPool(ctx, t, logger)
	defer pool.Close()

	applyMigration(ctx, t, pool, migrationPath(t, "V1_1__create_financial_data_schema.sql"))
	applyMigration(ctx, t, pool, migrationPath(t, "V1_14__create_investment_asset_tables.sql"))
	applyMigration(ctx, t, pool, migrationPath(t, "V1_18__add_investment_fund_search_fields.sql"))
	applyMigration(ctx, t, pool, migrationPath(t, "V1_21__add_rcvm175_fund_raw_fields.sql"))

	zipBytes := buildRegistroFundoClasseZip(t)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dados/FI/CAD/DADOS/cad_fi.csv":
			_, _ = w.Write([]byte("CNPJ_FUNDO;DENOM_SOCIAL;CLASSE;TP_FUNDO;GESTOR;SITUACAO\n"))
		case "/dados/FI/CAD/DADOS/registro_fundo_classe.zip":
			_, _ = w.Write(zipBytes)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	repo := database.NewAssetRepository(pool, logger)

	first := runCatalogIngester(ctx, t, repo, server.URL, logger)
	if first.total != 1 || first.submitted != 1 || first.skipped != 0 {
		t.Fatalf("first run counts = %+v, want total=1 submitted=1 skipped=0", first)
	}

	var got struct {
		Manager               string
		Administrador         string
		TributacaoLongoPrazo  string
		PublicoAlvo           string
		PatrimonioLiquido     string
		DataPatrimonioLiquido string
		CNPJAuditor           string
		Auditor               string
		CNPJCustodiante       string
		Custodiante           string
		UpdatedAt             time.Time
	}
	if err := pool.QueryRow(ctx, `
		SELECT manager, administrador, tributacao_longo_prazo, publico_alvo, patrimonio_liquido,
		       data_patrimonio_liquido, cnpj_auditor, auditor, cnpj_custodiante, custodiante, updated_at
		FROM financial_data.investment_asset
		WHERE fund_cnpj = '12345678000190'
	`).Scan(
		&got.Manager,
		&got.Administrador,
		&got.TributacaoLongoPrazo,
		&got.PublicoAlvo,
		&got.PatrimonioLiquido,
		&got.DataPatrimonioLiquido,
		&got.CNPJAuditor,
		&got.Auditor,
		&got.CNPJCustodiante,
		&got.Custodiante,
		&got.UpdatedAt,
	); err != nil {
		t.Fatalf("QueryRow() error = %v", err)
	}

	if got.Manager != "Gestor Correto" ||
		got.Administrador != "Administrador Correto" ||
		got.TributacaoLongoPrazo != "S" ||
		got.PublicoAlvo != "Investidores em Geral" ||
		got.PatrimonioLiquido != "12345.67" ||
		got.DataPatrimonioLiquido != "2026-07-28" ||
		got.CNPJAuditor != "11.222.333/0001-44" ||
		got.Auditor != "Auditor XPTO" ||
		got.CNPJCustodiante != "99.888.777/0001-66" ||
		got.Custodiante != "Custodiante XPTO" {
		t.Fatalf("persisted raw fields mismatch: %+v", got)
	}

	firstUpdatedAt := got.UpdatedAt
	time.Sleep(1100 * time.Millisecond)

	second := runCatalogIngester(ctx, t, repo, server.URL, logger)
	if second.total != 1 || second.submitted != 0 || second.skipped != 1 {
		t.Fatalf("second run counts = %+v, want total=1 submitted=0 skipped=1", second)
	}

	var secondUpdatedAt time.Time
	if err := pool.QueryRow(ctx, `
		SELECT updated_at
		FROM financial_data.investment_asset
		WHERE fund_cnpj = '12345678000190'
	`).Scan(&secondUpdatedAt); err != nil {
		t.Fatalf("QueryRow(updated_at) error = %v", err)
	}
	if !secondUpdatedAt.Equal(firstUpdatedAt) {
		t.Fatalf("updated_at changed on unchanged rerun: first=%s second=%s", firstUpdatedAt, secondUpdatedAt)
	}
}

type ingestCounts struct {
	total     int
	submitted int
	skipped   int
}

func runCatalogIngester(
	ctx context.Context,
	t *testing.T,
	repo *database.AssetRepository,
	baseURL string,
	logger *slog.Logger,
) ingestCounts {
	t.Helper()

	wp := workerpool.NewWorkerPool(1, 1, logger)
	wp.Start(ctx, func(ctx context.Context, item workerpool.WorkItem) error {
		return item.Process(ctx)
	})

	client := cvmhttp.NewCVMClient(baseURL, 30, logger)
	ingester := NewCatalogIngester(client, repo, wp, 1, logger)
	total, submitted, skipped, err := ingester.Run(ctx)
	if err != nil {
		t.Fatalf("CatalogIngester.Run() error = %v", err)
	}

	wp.Shutdown()
	wp.Wait()
	if errs := wp.Errors(); len(errs) > 0 {
		t.Fatalf("worker pool errors = %v", errs)
	}

	return ingestCounts{total: total, submitted: submitted, skipped: skipped}
}

func setupPostgresPool(ctx context.Context, t *testing.T, logger *slog.Logger) *pgxpool.Pool {
	t.Helper()

	container, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: testcontainers.ContainerRequest{
			Image:        "postgres:18rc1-alpine",
			ExposedPorts: []string{"5432/tcp"},
			Env: map[string]string{
				"POSTGRES_DB":       "invistus_test",
				"POSTGRES_USER":     "postgres",
				"POSTGRES_PASSWORD": "postgres",
			},
			WaitingFor: wait.ForListeningPort("5432/tcp").WithStartupTimeout(60 * time.Second),
		},
		Started: true,
	})
	if err != nil {
		t.Fatalf("starting postgres container: %v", err)
	}
	t.Cleanup(func() {
		_ = container.Terminate(context.Background())
	})

	host, err := container.Host(ctx)
	if err != nil {
		t.Fatalf("container.Host() error = %v", err)
	}
	port, err := container.MappedPort(ctx, "5432")
	if err != nil {
		t.Fatalf("container.MappedPort() error = %v", err)
	}

	connString := "postgres://postgres:postgres@" + host + ":" + port.Port() + "/invistus_test?sslmode=disable"
	pool, err := database.NewConnectionPool(ctx, connString, logger)
	if err != nil {
		t.Fatalf("NewConnectionPool() error = %v", err)
	}
	return pool
}

func applyMigration(ctx context.Context, t *testing.T, pool *pgxpool.Pool, path string) {
	t.Helper()

	sqlBytes, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("ReadFile(%s) error = %v", path, err)
	}
	if _, err := pool.Exec(ctx, string(sqlBytes)); err != nil {
		t.Fatalf("Exec migration %s error = %v", filepath.Base(path), err)
	}
}

func migrationPath(t *testing.T, filename string) string {
	t.Helper()

	_, currentFile, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller() failed")
	}
	return filepath.Join(
		filepath.Dir(currentFile),
		"..", "..", "..", "..", "..", "..",
		"service", "kotlin", "financialdata", "src", "main", "resources", "db", "migration", filename,
	)
}

func buildRegistroFundoClasseZip(t *testing.T) []byte {
	t.Helper()

	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)

	classeCSV := stringsJoinLines(
		"ID_Registro_Fundo;ID_Registro_Classe;CNPJ_Classe;Codigo_CVM;Data_Registro;Data_Constituicao;Data_Inicio;Tipo_Classe;Denominacao_Social;Situacao;Data_Inicio_Situacao;Classificacao;Indicador_Desempenho;Classe_Cotas;Classificacao_Anbima;Tributacao_Longo_Prazo;Entidade_Investimento;Permitido_Aplicacao_CemPorCento_Exterior;Classe_ESG;Forma_Condominio;Exclusivo;Publico_Alvo;Patrimonio_Liquido;Data_Patrimonio_Liquido;CNPJ_Auditor;Auditor;CNPJ_Custodiante;Custodiante;CNPJ_Controlador;Controlador",
		"fund-1;classe-1;12.345.678/0001-90;12345;2026-07-01;2026-07-01;2026-07-01;FIC;FUNDO TESTE RCVM175;EM FUNCIONAMENTO NORMAL;2026-07-01;Classe;CDI;Fechada;Renda Fixa;S;N;N;N;Aberto;N;Investidores em Geral;12345.67;2026-07-28;11.222.333/0001-44;Auditor XPTO;99.888.777/0001-66;Custodiante XPTO;00.000.000/0001-00;Controlador Errado",
	)
	fundoCSV := stringsJoinLines(
		"ID_Registro_Fundo;CNPJ_Fundo;Codigo_CVM;Data_Registro;Data_Constituicao;Tipo_Fundo;Denominacao_Social;Data_Cancelamento;Situacao;Data_Inicio_Situacao;Data_Adaptacao_RCVM175;Data_Inicio_Exercicio_Social;Data_Fim_Exercicio_Social;Patrimonio_Liquido;Data_Patrimonio_Liquido;Diretor;CNPJ_Administrador;Administrador;Tipo_Pessoa_Gestor;CPF_CNPJ_Gestor;Gestor",
		"fund-1;12.345.678/0001-90;12345;2026-07-01;2026-07-01;FIC;FUNDO TESTE RCVM175;;EM FUNCIONAMENTO NORMAL;2026-07-01;2026-07-01;2026-01-01;2026-12-31;12345.67;2026-07-28;Diretor XPTO;55.444.333/0001-22;Administrador Correto;J;44.333.222/0001-11;Gestor Correto",
	)

	for name, contents := range map[string]string{
		rcvm175ClasseEntryName: classeCSV,
		rcvm175FundoEntryName:  fundoCSV,
	} {
		w, err := zw.Create(name)
		if err != nil {
			t.Fatalf("zip.Create(%s) error = %v", name, err)
		}
		if _, err := w.Write([]byte(contents)); err != nil {
			t.Fatalf("zip write %s error = %v", name, err)
		}
	}

	if err := zw.Close(); err != nil {
		t.Fatalf("zip.Close() error = %v", err)
	}
	return buf.Bytes()
}

func stringsJoinLines(lines ...string) string {
	var buf bytes.Buffer
	for i, line := range lines {
		if i > 0 {
			buf.WriteByte('\n')
		}
		buf.WriteString(line)
	}
	buf.WriteByte('\n')
	return buf.String()
}
