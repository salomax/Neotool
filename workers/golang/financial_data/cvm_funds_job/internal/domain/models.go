package domain

import (
	"time"

	"github.com/google/uuid"
)

type ChangeType string

const (
	ChangeTypeCreated   ChangeType = "CREATED"
	ChangeTypeUpdated   ChangeType = "UPDATED"
	ChangeTypeUnchanged ChangeType = "UNCHANGED"
)

type RunType string

const (
	RunTypeBackfill    RunType = "BACKFILL"
	RunTypeIncremental RunType = "INCREMENTAL"
)

// FundRecord is one parsed row from cad_fi.csv.
type FundRecord struct {
	CNPJ                  string // normalized: 14 raw digits, zero-padded
	Name                  string
	Classe                string // "Fundo de Ações", "Renda Fixa", "Multimercado", "Cambial"
	TipoFundo             string // "PGBL", "VGBL", "FIC", "Master", etc.
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
	IsActive              bool   // SITUACAO == "EM FUNCIONAMENTO NORMAL"
	AssetType             string // resolved: PGBL | VGBL | MUTUAL_FUND
	ContentHash           string // SHA-256 of mutable catalog fields
}

// NavUpsertParam is one price row destined for asset_daily_price.
type NavUpsertParam struct {
	AssetID        uuid.UUID
	PriceDate      time.Time
	ClosePrice     float64 // VL_QUOTA — NAV per quota
	TotalNetAssets float64 // VL_TOTAL — patrimônio líquido
	Shareholders   *int    // NR_COTST — may be absent
	Source         string
	ContentHash    string // SHA-256 of (cnpj + date + nav_per_quota)
}

// InvestmentAsset is the persisted catalog row returned by repository lookups.
type InvestmentAsset struct {
	ID          uuid.UUID
	FundCNPJ    string
	Name        string
	AssetType   string
	IsActive    bool
	ContentHash string
}

// JobControlRecord tracks load state for one import job.
type JobControlRecord struct {
	JobName       string
	LastDataDate  *time.Time
	LastSuccessAt *time.Time
	Status        string
}
