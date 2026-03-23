package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

type MerchantLogoHydrator struct {
	merchants     domain.MerchantLogoRepository
	jobs          domain.JobControlRepository
	logoClient    domain.LogoImageClient
	assetUploader domain.AssetUploader
	batchLimit    int
	throttleMS    int
	logger        *slog.Logger
}

func NewMerchantLogoHydrator(
	merchants domain.MerchantLogoRepository,
	jobs domain.JobControlRepository,
	logoClient domain.LogoImageClient,
	assetUploader domain.AssetUploader,
	batchLimit int,
	throttleMS int,
	logger *slog.Logger,
) *MerchantLogoHydrator {
	return &MerchantLogoHydrator{
		merchants:     merchants,
		jobs:          jobs,
		logoClient:    logoClient,
		assetUploader: assetUploader,
		batchLimit:    batchLimit,
		throttleMS:    throttleMS,
		logger:        logger,
	}
}

func (h *MerchantLogoHydrator) Run(ctx context.Context) error {
	if err := h.jobs.MarkRunning(ctx, domain.MerchantLogoJobName); err != nil {
		return fmt.Errorf("mark merchant logo job running: %w", err)
	}

	pending, err := h.merchants.FindPendingLogoHydration(ctx, h.batchLimit)
	if err != nil {
		return fmt.Errorf("load pending merchant domains: %w", err)
	}
	if len(pending) == 0 {
		h.logger.Info("merchant-logo hydrator: no pending merchant domains")
		return h.jobs.MarkSuccess(ctx, domain.MerchantLogoJobName)
	}

	successes := 0
	failures := 0
	for _, merchant := range pending {
		if err := ctx.Err(); err != nil {
			return err
		}

		if err := h.processDomain(ctx, merchant.Domain); err != nil {
			failures++
			h.logger.Warn("merchant-logo hydrator: domain failed", "domain", merchant.Domain, "error", err)
		} else {
			successes++
		}

		if h.throttleMS > 0 {
			select {
			case <-ctx.Done():
				return ctx.Err()
			case <-time.After(time.Duration(h.throttleMS) * time.Millisecond):
			}
		}
	}

	h.logger.Info("merchant-logo hydrator finished", "domains_scanned", len(pending), "successes", successes, "failures", failures)

	if failures > 0 {
		return fmt.Errorf("merchant-logo hydrator completed with %d failures", failures)
	}

	return h.jobs.MarkSuccess(ctx, domain.MerchantLogoJobName)
}

func (h *MerchantLogoHydrator) processDomain(ctx context.Context, domainName string) error {
	logoBytes, err := h.logoClient.FetchLogo(ctx, domainName)
	if err != nil {
		return err
	}

	assetID, err := h.assetUploader.UploadLogo(
		ctx,
		logoBytes,
		fmt.Sprintf("merchants/%s.png", domainName),
		"image/png",
	)
	if err != nil {
		return err
	}

	if err := h.merchants.UpsertLogoAssetID(ctx, domainName, assetID); err != nil {
		return err
	}

	return nil
}
