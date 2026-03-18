package domain

import (
	"time"

	"github.com/google/uuid"
)

// RunType distinguishes a first-run backfill from an incremental update.
type RunType string

const (
	RunTypeBackfill    RunType = "BACKFILL"
	RunTypeIncremental RunType = "INCREMENTAL"
)

// AssetRecord is one unique B3 asset extracted from a COTAHIST file.
// One per ticker — multiple price records for the same ticker in the same file
// are deduplicated before upsert.
type AssetRecord struct {
	Ticker      string // CODNEG trimmed — e.g. "PETR4", "HGLG11", "PETRH325"
	Name        string // NOMRES trimmed — e.g. "PETROBRAS"
	AssetType   string // one of the AssetType* constants
	Especi      string // ESPECI trimmed — e.g. "ON", "PN", "CI", "ON  EJ"
	IsinCode    string // CODISI trimmed — empty string stored as NULL
	LotSize     int    // 100 for equities, 1 for FIIs/options
	ContentHash string // SHA-256 of mutable catalog fields
}

// PriceUpsertParam is one OHLCV row destined for asset_daily_price.
type PriceUpsertParam struct {
	AssetID     uuid.UUID
	PriceDate   time.Time
	OpenPrice   float64
	HighPrice   float64
	LowPrice    float64
	ClosePrice  float64
	AvgPrice    float64
	// Volume is the raw VOLTOT integer (implicit 2 decimal places).
	// The actual monetary volume is Volume / 100. Division is done in SQL
	// (cast to numeric) to avoid float64 precision loss for large values.
	Volume      int64
	Trades      int
	Source      string
	ContentHash string // SHA-256 of (ticker + date + open + high + low + close)
}

// JobControlRecord tracks load state for one import job.
type JobControlRecord struct {
	JobName       string
	LastDataDate  *time.Time
	LastSuccessAt *time.Time
	Status        string
}
