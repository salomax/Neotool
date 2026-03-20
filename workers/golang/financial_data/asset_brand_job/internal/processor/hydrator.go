package processor

import (
	"context"
	"fmt"
	"log/slog"
	"math"
	"regexp"
	"strings"
	"time"
	"unicode"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
	"golang.org/x/text/unicode/norm"
)

type Hydrator struct {
	assets               domain.AssetRepository
	brands               domain.BrandRepository
	jobs                 domain.JobControlRepository
	client               domain.SearchClient
	batchLimit           int
	throttleMS           int
	autoApproveThreshold float64
	logger               *slog.Logger
}

func NewHydrator(
	assets domain.AssetRepository,
	brands domain.BrandRepository,
	jobs domain.JobControlRepository,
	client domain.SearchClient,
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
	query := buildSearchQuery(asset.Name)
	if query == "" {
		return false, nil
	}

	response, err := h.client.Search(ctx, query)
	if err != nil {
		return false, err
	}
	if len(response.Results) == 0 {
		h.logger.Info("asset-brand hydrator: no search results", "ticker", asset.Ticker, "query", query)
		return false, nil
	}

	top := response.Results[0]
	confidence := scoreCandidate(query, top, 1)
	status := domain.StatusPending
	if confidence >= h.autoApproveThreshold {
		status = domain.StatusAutoApproved
	}

	err = h.brands.UpsertBrandCandidate(ctx, domain.BrandUpsertInput{
		AssetID:          asset.ID,
		QueryUsed:        query,
		CandidateRank:    1,
		NormalizedName:   normalizeName(top.Name),
		DisplayName:      top.Name,
		Domain:           top.Domain,
		LogoURL:          top.LogoURL,
		Source:           domain.SourceLogoDevSearch,
		Status:           status,
		Confidence:       confidence,
		RawSearchPayload: response.RawPayload,
	})
	if err != nil {
		return false, err
	}

	return true, nil
}

func buildSearchQuery(assetName string) string {
	query := collapseWhitespace(assetName)
	query = shareClassSuffixRegex.ReplaceAllString(query, "")
	return strings.TrimSpace(query)
}

func scoreCandidate(query string, candidate domain.SearchCandidate, rank int) float64 {
	normalizedQuery := normalizeName(query)
	normalizedCandidate := normalizeName(candidate.Name)

	queryTokens := tokenSet(normalizedQuery)
	candidateTokens := tokenSet(normalizedCandidate)

	tokenOverlap := 0.0
	if len(queryTokens) > 0 {
		shared := 0
		for token := range queryTokens {
			if _, ok := candidateTokens[token]; ok {
				shared++
			}
		}
		tokenOverlap = float64(shared) / float64(len(queryTokens))
	}

	score := 0.55 + (tokenOverlap * 0.35)
	switch {
	case normalizedCandidate == normalizedQuery:
		score = 0.99
	case strings.Contains(normalizedCandidate, normalizedQuery), strings.Contains(normalizedQuery, normalizedCandidate):
		score = 0.93
	}

	if rank > 1 {
		score -= float64(rank-1) * 0.05
	}
	if strings.TrimSpace(candidate.Domain) == "" {
		score -= 0.10
	}

	return math.Round(clamp(score, 0, 0.99)*10000) / 10000
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

func tokenSet(value string) map[string]struct{} {
	tokens := strings.Fields(value)
	out := make(map[string]struct{}, len(tokens))
	for _, token := range tokens {
		out[token] = struct{}{}
	}
	return out
}

func clamp(value float64, min float64, max float64) float64 {
	if value < min {
		return min
	}
	if value > max {
		return max
	}
	return value
}

func collapseWhitespace(value string) string {
	return strings.Join(strings.Fields(value), " ")
}

var shareClassSuffixRegex = regexp.MustCompile(`\s+(ON|PN|PNA|PNB|PNC|UNIT|UNT|CI|N1|N2|NM)$`)
