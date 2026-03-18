package domain

import (
	"time"
)

// Institution represents a financial institution from BACEN registry
type Institution struct {
	BacenCodInst               string
	AnoMesRef                  string
	NomeInstituicao            string
	Situacao                   string
	SegmentoTB                 *string
	Atividade                  *string
	TipoConsolidadoBancario    *string
	TipoConsolidacao           *string
	TipoControle               *string
	UF                         *string
	Municipio                  *string
	CodConglomeradoFinanceiro  *string
	CodConglomeradoPrudencial  *string
	CNPJInstituicaoLider       *string
	SourceHash                 string
	CreatedAt                  time.Time
	UpdatedAt                  time.Time
}

// ChangeType represents the type of change for an institution
type ChangeType string

const (
	ChangeTypeCreated   ChangeType = "CREATED"
	ChangeTypeUpdated   ChangeType = "UPDATED"
	ChangeTypeUnchanged ChangeType = "UNCHANGED"
)

// InstitutionChange represents a detected change in institution data
type InstitutionChange struct {
	Institution Institution
	ChangeType  ChangeType
}

// Report represents a BACEN report from the catalog
type Report struct {
	NomeRelatorio   string
	NumeroRelatorio string
}

// ExtractTaskStatus represents the status of an extract task
type ExtractTaskStatus string

const (
	StatusPending ExtractTaskStatus = "PENDING"
	StatusRunning ExtractTaskStatus = "RUNNING"
	StatusSuccess ExtractTaskStatus = "SUCCESS"
	StatusEmpty   ExtractTaskStatus = "EMPTY"
	StatusError   ExtractTaskStatus = "ERROR"
)

// ExtractTask represents a task to extract data from BACEN API
type ExtractTask struct {
	AnoMes          string
	TipoInstituicao int
	NomeRelatorio   string
	NumeroRelatorio string
	PageSkip        int
	PageTop         int
	Status          ExtractTaskStatus
	AttemptCount    int
	RowsProcessed   int // Number of rows processed in this task
	LastError       *string
	LastHTTPStatus  *int
	StartedAt       *time.Time
	FinishedAt      *time.Time
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// IFDataRaw represents raw CSV data from BACEN API
type IFDataRaw struct {
	AnoMes          string
	TipoInstituicao int
	NomeRelatorio   string
	NumeroRelatorio string
	CodInst         string
	RawData         string
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// InstitutionReport represents a normalized institution report (parent)
type InstitutionReport struct {
	ID              string // UUID
	CodInst         string
	AnoMes          string
	NumeroRelatorio string
	TipoInstituicao int
	CreatedAt       time.Time
	UpdatedAt       time.Time
}

// InstitutionReportAccount represents normalized account metadata
type InstitutionReportAccount struct {
	ID                 string // UUID
	CanonicalAccountID *string // UUID - Foreign key to canonical account
	Grupo              *string
	Conta              *string
	NomeColuna         *string
	DescricaoColuna    *string
	CreatedAt          time.Time
	UpdatedAt          time.Time
}

// InstitutionReportAccountCanonical represents a canonical account used to group account variants
type InstitutionReportAccountCanonical struct {
	ID             string // UUID
	NormalizedName string
	DisplayName    *string
	CreatedAt      time.Time
	UpdatedAt      time.Time
}

// InstitutionReportData represents individual report data rows (child)
type InstitutionReportData struct {
	ID            string // UUID
	InstReportID  string
	AccountID     string // UUID - Foreign key to InstitutionReportAccount
	Saldo         *float64
	SaldoAjustado *float64 // Quarter-adjusted balance for cumulative accounts (e.g., 78187 in months 06/12)
	CreatedAt     time.Time
	UpdatedAt     time.Time
}

// LoadControl tracks the completion status of data loads
type LoadControl struct {
	AnoMes        string
	NomeRelatorio string
	Extracted     bool
	Promoted      bool
	Completed     bool
	CreatedAt     time.Time
	UpdatedAt     time.Time
}

// InstitutionUpsertedEvent represents the Kafka event for institution changes
type InstitutionUpsertedEvent struct {
	BacenCodInst               string      `json:"bacen_cod_inst"`
	ChangeType                 string      `json:"change_type"`
	AnoMesRef                  string      `json:"ano_mes_ref"`
	NomeInstituicao            string      `json:"nome_instituicao"`
	Situacao                   string      `json:"situacao"`
	SegmentoTB                 *string     `json:"segmento_tb,omitempty"`
	Atividade                  *string     `json:"atividade,omitempty"`
	TipoConsolidadoBancario    *string     `json:"tipo_consolidado_bancario,omitempty"`
	TipoConsolidacao           *string     `json:"tipo_consolidacao,omitempty"`
	TipoControle               *string     `json:"tipo_controle,omitempty"`
	UF                         *string     `json:"uf,omitempty"`
	Municipio                  *string     `json:"municipio,omitempty"`
	CodConglomeradoFinanceiro  *string     `json:"cod_conglomerado_financeiro,omitempty"`
	CodConglomeradoPrudencial  *string     `json:"cod_conglomerado_prudencial,omitempty"`
	CNPJInstituicaoLider       *string     `json:"cnpj_instituicao_lider,omitempty"`
	SourceHash                 string      `json:"source_hash"`
}

// QuarterInfo contains information about a target quarter
type QuarterInfo struct {
	AnoMes             string
	PublicationDate    time.Time
	IsPublicationReady bool
}
