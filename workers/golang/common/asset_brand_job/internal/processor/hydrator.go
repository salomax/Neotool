package processor

import (
	"context"
	"fmt"
	"log/slog"
	"path/filepath"
	"strings"
	"time"
	"unicode"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
	"golang.org/x/text/unicode/norm"
)

type Hydrator struct {
	assets               domain.AssetRepository
	brands               domain.BrandRepository
	jobs                 domain.JobControlRepository
	client               domain.BrandLookupClient
	assetUploader        domain.AssetUploader
	batchLimit           int
	throttleMS           int
	autoApproveThreshold float64
	logger               *slog.Logger
}

func NewHydrator(
	assets domain.AssetRepository,
	brands domain.BrandRepository,
	jobs domain.JobControlRepository,
	client domain.BrandLookupClient,
	assetUploader domain.AssetUploader,
	batchLimit int,
	throttleMS int,
	autoApproveThreshold float64,
	logger *slog.Logger,
) *Hydrator {
	return &Hydrator{
		assets:               assets,
		brands:               brands,
		jobs:                 jobs,
		client:               client,
		assetUploader:        assetUploader,
		batchLimit:           batchLimit,
		throttleMS:           throttleMS,
		autoApproveThreshold: autoApproveThreshold,
		logger:               logger,
	}
}

func (h *Hydrator) Run(ctx context.Context) error {
	if err := h.jobs.MarkRunning(ctx, domain.JobName); err != nil {
		return fmt.Errorf("mark job running: %w", err)
	}

	pending, err := h.assets.FindPendingBrandHydration(ctx, h.batchLimit)
	if err != nil {
		return fmt.Errorf("load pending assets: %w", err)
	}
	if len(pending) == 0 {
		h.logger.Info("asset-brand hydrator: no pending assets")
		return h.jobs.MarkSuccess(ctx, domain.JobName)
	}

	stats := domain.RunStats{AssetsScanned: len(pending)}
	for _, asset := range pending {
		if err := ctx.Err(); err != nil {
			return err
		}

		matched, err := h.processAsset(ctx, asset)
		if err != nil {
			stats.Failures++
			h.logger.Warn("asset-brand hydrator: asset failed", "ticker", asset.Ticker, "name", asset.Name, "error", err)
			continue
		}
		if matched {
			stats.MatchesSaved++
		} else {
			stats.NoResults++
		}

		if h.throttleMS > 0 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(time.Duration(h.throttleMS) * time.Millisecond):
			}
		}
	}

	h.logger.Info(
		"asset-brand hydrator finished",
		"assets_scanned", stats.AssetsScanned,
		"matches_saved", stats.MatchesSaved,
		"no_results", stats.NoResults,
		"failures", stats.Failures,
	)

	if stats.Failures > 0 {
		return fmt.Errorf("asset-brand hydrator completed with %d failures", stats.Failures)
	}

	return h.jobs.MarkSuccess(ctx, domain.JobName)
}

func (h *Hydrator) processAsset(ctx context.Context, asset domain.PendingAsset) (bool, error) {
	lookup, err := h.client.LookupBrand(ctx, asset)
	if err != nil {
		return false, err
	}
	if lookup == nil {
		h.logger.Info(
			"asset-brand hydrator: no provider result",
			"ticker", asset.Ticker,
			"asset_type", asset.AssetType,
			"isin_code", asset.IsinCode,
		)
		return false, nil
	}

	normalizedName := normalizeName(asset.Name)
	existingBrand, err := h.brands.FindBrandByNormalizedName(ctx, normalizedName)
	if err != nil {
		return false, err
	}

	logoAssetID, err := h.resolveLogoAssetID(ctx, asset, normalizedName, lookup, existingBrand)
	if err != nil {
		return false, err
	}

	confidence := resolveConfidence(asset, lookup)
	status := domain.StatusPending
	if confidence >= h.autoApproveThreshold {
		status = domain.StatusAutoApproved
	}

	err = h.brands.UpsertBrandCandidate(ctx, domain.BrandUpsertInput{
		AssetID:          asset.ID,
		QueryUsed:        lookup.QueryUsed,
		CandidateRank:    1,
		NormalizedName:   normalizedName,
		DisplayName:      asset.Name,
		Domain:           "",
		LogoURL:          lookup.LogoURL,
		LogoAssetID:      logoAssetID,
		Source:           domain.SourceBrandfetchISIN,
		Status:           status,
		Confidence:       confidence,
		RawSearchPayload: lookup.RawPayload,
	})
	if err != nil {
		return false, err
	}

	return true, nil
}

func (h *Hydrator) resolveLogoAssetID(
	ctx context.Context,
	asset domain.PendingAsset,
	normalizedName string,
	lookup *domain.BrandLookupResult,
	existingBrand *domain.ExistingBrand,
) (*uuid.UUID, error) {
	if existingBrand != nil && existingBrand.LogoAssetID != nil {
		return existingBrand.LogoAssetID, nil
	}
	if lookup == nil || len(lookup.LogoData) == 0 {
		return nil, nil
	}
	if strings.TrimSpace(lookup.MimeType) == "" {
		return nil, fmt.Errorf("missing mime type for asset %s", asset.Ticker)
	}

	assetID, err := h.assetUploader.UploadLogo(
		ctx,
		lookup.LogoData,
		buildBrandLogoFilename(normalizedName, lookup.MimeType),
		lookup.MimeType,
	)
	if err != nil {
		return nil, fmt.Errorf("upload brand logo: %w", err)
	}

	parsedAssetID, err := uuid.Parse(assetID)
	if err != nil {
		return nil, fmt.Errorf("parse uploaded asset id %q: %w", assetID, err)
	}

	return &parsedAssetID, nil
}

func buildBrandLogoFilename(normalizedName string, mimeType string) string {
	baseName := strings.ToLower(strings.ReplaceAll(normalizedName, " ", "-"))
	baseName = strings.Trim(baseName, "-")
	if baseName == "" {
		baseName = "brand-logo"
	}
	return filepath.ToSlash(fmt.Sprintf("asset-brands/%s.%s", baseName, fileExtensionForMimeType(mimeType)))
}

func fileExtensionForMimeType(mimeType string) string {
	switch mimeType {
	case "image/jpeg":
		return "jpg"
	case "image/png":
		return "png"
	case "image/webp":
		return "webp"
	case "image/svg+xml":
		return "svg"
	default:
		return "bin"
	}
}

func resolveConfidence(asset domain.PendingAsset, lookup *domain.BrandLookupResult) float64 {
	if lookup == nil || strings.TrimSpace(lookup.QueryUsed) == "" {
		return 0
	}
	if strings.TrimSpace(asset.IsinCode) == "" {
		return 0
	}
	return 1
}

func normalizeName(value string) string {
	decomposed := norm.NFD.String(value)
	var builder strings.Builder
	lastWasSpace := false
	for _, r := range decomposed {
		switch {
		case unicode.Is(unicode.Mn, r):
			continue
		case unicode.IsLetter(r) || unicode.IsDigit(r):
			builder.WriteRune(unicode.ToUpper(r))
			lastWasSpace = false
		default:
			if !lastWasSpace {
				builder.WriteRune(' ')
				lastWasSpace = true
			}
		}
	}
	return strings.TrimSpace(builder.String())
}
