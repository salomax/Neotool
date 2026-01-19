package processor

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/invistus/bacen-ifdata/internal/domain"
	"github.com/invistus/bacen-ifdata/internal/infrastructure/database"
	"github.com/invistus/bacen-ifdata/internal/shared"
)

// QuarterDeterminer determines the target quarter for processing
type QuarterDeterminer struct {
	institutionRepo *database.InstitutionRepository
	logger          *slog.Logger
}

// NewQuarterDeterminer creates a new quarter determiner
func NewQuarterDeterminer(institutionRepo *database.InstitutionRepository, logger *slog.Logger) *QuarterDeterminer {
	return &QuarterDeterminer{
		institutionRepo: institutionRepo,
		logger:          logger,
	}
}

// DetermineTargetQuarter determines the next quarter to process
// If forcedAnoMes is provided, it validates and uses that quarter
// Otherwise, it calculates the next quarter based on last extraction
func (qd *QuarterDeterminer) DetermineTargetQuarter(ctx context.Context, forcedAnoMes string) (*domain.QuarterInfo, error) {
	now := time.Now()

	// If forced ano_mes is provided, validate and use it
	if forcedAnoMes != "" {
		return qd.validateForcedQuarter(forcedAnoMes, now)
	}

	// Get last extracted ano_mes
	lastAnoMes, err := qd.institutionRepo.GetLastExtractedAnoMes(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to get last extracted ano_mes: %w", err)
	}

	var targetAnoMes string

	if lastAnoMes == "" {
		// No prior data, start from historical beginning
		targetAnoMes = shared.GetHistoricalStartQuarter() // 201603
		qd.logger.Info("no prior data found, starting from historical beginning",
			"target_ano_mes", targetAnoMes)
	} else {
		// Calculate next quarter
		nextQuarter, err := shared.GetNextQuarter(lastAnoMes)
		if err != nil {
			return nil, fmt.Errorf("failed to calculate next quarter: %w", err)
		}
		targetAnoMes = nextQuarter
	}

	// Validate quarter month
	year, month, err := shared.ParseAnoMes(targetAnoMes)
	if err != nil {
		return nil, err
	}

	if !shared.IsQuarterMonth(month) {
		return nil, domain.ErrInvalidQuarter
	}

	// Check publication window
	publicationDate, err := shared.GetPublicationDate(targetAnoMes)
	if err != nil {
		return nil, fmt.Errorf("failed to get publication date: %w", err)
	}

	isReady := now.After(publicationDate) || now.Equal(publicationDate)

	qd.logger.Info("determined target quarter",
		"target_ano_mes", targetAnoMes,
		"year", year,
		"month", month,
		"publication_date", publicationDate.Format("2006-01-02"),
		"is_ready", isReady,
		"last_extracted", lastAnoMes)

	return &domain.QuarterInfo{
		AnoMes:             targetAnoMes,
		PublicationDate:    publicationDate,
		IsPublicationReady: isReady,
	}, nil
}

// validateForcedQuarter validates a forced ano_mes and returns QuarterInfo
func (qd *QuarterDeterminer) validateForcedQuarter(anoMes string, now time.Time) (*domain.QuarterInfo, error) {
	// Validate format
	year, month, err := shared.ParseAnoMes(anoMes)
	if err != nil {
		return nil, err
	}

	// Validate it's a quarter month
	if !shared.IsQuarterMonth(month) {
		return nil, fmt.Errorf("%w: %s (month %d)", domain.ErrInvalidQuarter, anoMes, month)
	}

	// Get publication date (but don't enforce window for forced runs)
	publicationDate, err := shared.GetPublicationDate(anoMes)
	if err != nil {
		return nil, fmt.Errorf("failed to get publication date: %w", err)
	}

	isReady := now.After(publicationDate) || now.Equal(publicationDate)

	qd.logger.Info("using forced quarter",
		"target_ano_mes", anoMes,
		"year", year,
		"month", month,
		"publication_date", publicationDate.Format("2006-01-02"),
		"is_ready", isReady,
		"forced", true)

	// Always mark as ready for forced runs
	return &domain.QuarterInfo{
		AnoMes:             anoMes,
		PublicationDate:    publicationDate,
		IsPublicationReady: true, // Always ready when forced
	}, nil
}
