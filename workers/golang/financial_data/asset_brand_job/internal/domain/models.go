package domain

import (
	"context"

	"github.com/google/uuid"
)

const (
	JobName             = "asset_brand_logo_dev"
	SourceLogoDevSearch = "LOGO_DEV_SEARCH"

	StatusPending      = "PENDING"
	StatusAutoApproved = "AUTO_APPROVED"
	StatusVerified     = "VERIFIED"
	StatusRejected     = "REJECTED"
)

type PendingAsset struct {
	ID     uuid.UUID
	Ticker string
	Name   string
}

type SearchCandidate struct {
	Name    string
	Domain  string
	LogoURL string
}

type SearchResponse struct {
	Results    []SearchCandidate
	RawPayload string
}

type BrandUpsertInput struct {
	AssetID          uuid.UUID
	QueryUsed        string
	CandidateRank    int
	NormalizedName   string
	DisplayName      string
	Domain           string
	LogoURL          string
	Source           string
	Status           string
	Confidence       float64
	RawSearchPayload string
}

type RunStats struct {
	AssetsScanned int
	MatchesSaved  int
	NoResults     int
	Failures      int
}

type AssetRepository interface {
	FindPendingBrandHydration(ctx context.Context, limit int) ([]PendingAsset, error)
}

type BrandRepository interface {
	UpsertBrandCandidate(ctx context.Context, input BrandUpsertInput) error
}

type JobControlRepository interface {
	MarkRunning(ctx context.Context, jobName string) error
	MarkSuccess(ctx context.Context, jobName string) error
	MarkFailed(ctx context.Context, jobName string, errMsg string) error
}

type SearchClient interface {
	Search(ctx context.Context, query string) (SearchResponse, error)
}
