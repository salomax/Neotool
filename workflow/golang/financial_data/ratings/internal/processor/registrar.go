package processor

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
)

// RatingRegistrar handles persistence of fetched ratings.
type RatingRegistrar interface {
	Register(ctx context.Context, mapping domain.InstitutionIssuerMapping, result *domain.FetchResult) (*domain.IngestionStats, error)
	StoreEvidence(ctx context.Context, evidence *domain.SourceEvidence) (*uuid.UUID, bool, error)
}

// DefaultRegistrar implements RatingRegistrar.
type DefaultRegistrar struct {
	ratingRepo   domain.RatingRepository
	historyRepo  domain.HistoryRepository
	evidenceRepo domain.EvidenceRepository
	logger       *slog.Logger
}

// NewDefaultRegistrar creates a new registrar.
func NewDefaultRegistrar(
	ratingRepo domain.RatingRepository,
	historyRepo domain.HistoryRepository,
	evidenceRepo domain.EvidenceRepository,
	logger *slog.Logger,
) *DefaultRegistrar {
	return &DefaultRegistrar{
		ratingRepo:   ratingRepo,
		historyRepo:  historyRepo,
		evidenceRepo: evidenceRepo,
		logger:       logger.With("processor", "rating_registrar"),
	}
}

// Register persists fetched ratings and history.
func (r *DefaultRegistrar) Register(ctx context.Context, mapping domain.InstitutionIssuerMapping, result *domain.FetchResult) (*domain.IngestionStats, error) {
	stats := &domain.IngestionStats{}

	evidenceID, stored, err := r.StoreEvidence(ctx, &result.Evidence)
	if err != nil {
		r.logger.Warn("failed to store evidence",
			"error", err,
		)
	}
	if stored {
		stats.EvidenceStored = 1
	}

	if evidenceID != nil {
		for i := range result.Ratings {
			result.Ratings[i].SourceEvidenceID = evidenceID
		}
	} else {
		for i := range result.Ratings {
			result.Ratings[i].SourceEvidenceID = nil
		}
	}

	if result.Error != nil {
		return stats, fmt.Errorf("fetch error: %w", result.Error)
	}

	changes, err := r.detectChanges(ctx, result.Ratings, mapping.FinancialInstitutionID, mapping.RatingIssuerID)
	if err != nil {
		return stats, err
	}

	var historyRecords []domain.RatingHistory

	for _, change := range changes {
		switch change.ChangeType {
		case domain.ChangeTypeUnchanged:
			stats.RatingsUnchanged++
			continue
		case domain.ChangeTypeCreated, domain.ChangeTypeUpdated:
			isNew, err := r.ratingRepo.Upsert(ctx, &change.Rating)
			if err != nil {
				r.logger.Error("failed to upsert rating",
					"rating_name", change.Rating.RatingName,
					"error", err,
				)
				stats.Errors++
				continue
			}

			if isNew {
				stats.RatingsCreated++
			} else {
				stats.RatingsUpdated++
			}

			history := domain.RatingHistory{
				ID:                     uuid.New(),
				FinancialInstitutionID: change.Rating.FinancialInstitutionID,
				IssuerID:               change.Rating.IssuerID,
				RatingName:             change.Rating.RatingName,
				RatingLabel:            change.Rating.RatingLabel,
				RatingDate:             change.Rating.RatingDate,
				CustomParams:           change.Rating.CustomParams,
				SourceEvidenceID:       change.Rating.SourceEvidenceID,
				ContentHash:            change.Rating.ContentHash,
				ChangedAt:              time.Now().UTC(),
			}
			historyRecords = append(historyRecords, history)

			r.logger.Info("rating processed",
				"rating_name", change.Rating.RatingName,
				"rating_label", change.Rating.RatingLabel,
				"change_type", change.ChangeType,
			)
		}
	}

	if len(historyRecords) > 0 {
		if err := r.historyRepo.InsertBatch(ctx, historyRecords); err != nil {
			r.logger.Error("failed to insert history batch",
				"count", len(historyRecords),
				"error", err,
			)
		}
	}

	return stats, nil
}

// StoreEvidence saves evidence if it doesn't already exist.
func (r *DefaultRegistrar) StoreEvidence(ctx context.Context, evidence *domain.SourceEvidence) (*uuid.UUID, bool, error) {
	existing, err := r.evidenceRepo.GetByHash(ctx, evidence.ContentHash)
	if err != nil && !errors.Is(err, domain.ErrEvidenceNotFound) {
		return nil, false, err
	}

	if existing != nil {
		r.logger.Debug("evidence already exists",
			"hash", evidence.ContentHash[:16]+"...",
			"evidence_id", existing.ID,
		)
		return &existing.ID, false, nil
	}

	if err := r.evidenceRepo.Insert(ctx, evidence); err != nil {
		return nil, false, err
	}

	return &evidence.ID, true, nil
}

func (r *DefaultRegistrar) detectChanges(ctx context.Context, ratings []domain.Rating, institutionID, issuerID uuid.UUID) ([]domain.RatingChange, error) {
	changes := make([]domain.RatingChange, 0, len(ratings))

	for _, rating := range ratings {
		existing, err := r.ratingRepo.GetByInstitutionIssuerAndName(ctx, institutionID, issuerID, rating.RatingName)
		var change domain.RatingChange
		change.Rating = rating

		if err != nil {
			if !errors.Is(err, domain.ErrRatingNotFound) {
				return nil, fmt.Errorf("failed to load existing rating %s: %w", rating.RatingName, err)
			}
		}

		if err != nil || existing == nil {
			change.ChangeType = domain.ChangeTypeCreated
		} else if existing.ContentHash == rating.ContentHash {
			change.ChangeType = domain.ChangeTypeUnchanged
			change.PreviousHash = existing.ContentHash
		} else {
			change.ChangeType = domain.ChangeTypeUpdated
			change.PreviousHash = existing.ContentHash
			change.Rating.ID = existing.ID
		}

		changes = append(changes, change)
	}

	return changes, nil
}
