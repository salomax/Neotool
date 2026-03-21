package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

type IssuerLogoHydrator struct {
	issuers       domain.IssuerRepository
	jobs          domain.JobControlRepository
	logoClient    domain.LogoImageClient
	assetUploader domain.AssetUploader
	batchLimit    int
	throttleMS    int
	logger        *slog.Logger
}

func NewIssuerLogoHydrator(
	issuers domain.IssuerRepository,
	jobs domain.JobControlRepository,
	logoClient domain.LogoImageClient,
	assetUploader domain.AssetUploader,
	batchLimit int,
	throttleMS int,
	logger *slog.Logger,
) *IssuerLogoHydrator {
	return &IssuerLogoHydrator{
		issuers:       issuers,
		jobs:          jobs,
		logoClient:    logoClient,
		assetUploader: assetUploader,
		batchLimit:    batchLimit,
		throttleMS:    throttleMS,
		logger:        logger,
	}
}

func (h *IssuerLogoHydrator) Run(ctx context.Context) error {
	if err := h.jobs.MarkRunning(ctx, domain.IssuerLogoJobName); err != nil {
		return fmt.Errorf("mark issuer logo job running: %w", err)
	}

	pending, err := h.issuers.FindPendingLogoHydration(ctx, h.batchLimit)
	if err != nil {
		return fmt.Errorf("load pending issuers: %w", err)
	}
	if len(pending) == 0 {
		h.logger.Info("issuer-logo hydrator: no pending issuers")
		return h.jobs.MarkSuccess(ctx, domain.IssuerLogoJobName)
	}

	successes := 0
	failures := 0
	for _, issuer := range pending {
		if err := ctx.Err(); err != nil {
			return err
		}

		if err := h.processIssuer(ctx, issuer); err != nil {
			failures++
			h.logger.Warn("issuer-logo hydrator: issuer failed", "issuer_id", issuer.ID, "issuer_name", issuer.Name, "domain", issuer.Domain, "error", err)
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

	h.logger.Info("issuer-logo hydrator finished", "issuers_scanned", len(pending), "successes", successes, "failures", failures)

	if failures > 0 {
		return fmt.Errorf("issuer-logo hydrator completed with %d failures", failures)
	}

	return h.jobs.MarkSuccess(ctx, domain.IssuerLogoJobName)
}

func (h *IssuerLogoHydrator) processIssuer(ctx context.Context, issuer domain.PendingIssuer) error {
	logoBytes, err := h.logoClient.FetchLogo(ctx, issuer.Domain)
	if err != nil {
		return err
	}

	assetID, err := h.assetUploader.UploadLogo(
		ctx,
		logoBytes,
		fmt.Sprintf("issuers/%s.png", issuer.ID.String()),
		"image/png",
	)
	if err != nil {
		return err
	}

	if err := h.issuers.UpdateLogoKey(ctx, issuer.ID, assetID); err != nil {
		return err
	}

	return nil
}
