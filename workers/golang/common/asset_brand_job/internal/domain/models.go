package domain

import (
	"context"

	"github.com/google/uuid"
)

const (
	JobName              = "asset_brand_brandfetch"
	SourceBrandfetchISIN = "BRANDFETCH_ISIN"

	StatusPending      = "PENDING"
	StatusAutoApproved = "AUTO_APPROVED"
	StatusVerified     = "VERIFIED"
	StatusRejected     = "REJECTED"
)

type PendingAsset struct {
	ID        uuid.UUID
	Ticker    string
	Name      string
	AssetType string
	IsinCode  string
}

type BrandLookupResult struct {
	QueryUsed  string
	LogoURL    string
	LogoData   []byte
	MimeType   string
	RawPayload string
}

type ExistingBrand struct {
	ID          uuid.UUID
	LogoAssetID *uuid.UUID
}

type BrandUpsertInput struct {
	AssetID          uuid.UUID
	QueryUsed        string
	CandidateRank    int
	NormalizedName   string
	DisplayName      string
	Domain           string
	LogoURL          string
	LogoAssetID      *uuid.UUID
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
	FindBrandByNormalizedName(ctx context.Context, normalizedName string) (*ExistingBrand, error)
	UpsertBrandCandidate(ctx context.Context, input BrandUpsertInput) error
}

type JobControlRepository interface {
	MarkRunning(ctx context.Context, jobName string) error
	MarkSuccess(ctx context.Context, jobName string) error
	MarkFailed(ctx context.Context, jobName string, errMsg string) error
}

type BrandLookupClient interface {
	LookupBrand(ctx context.Context, asset PendingAsset) (*BrandLookupResult, error)
}
