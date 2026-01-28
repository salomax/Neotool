package fitch

import (
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
	"github.com/invistus/ratings/internal/shared"
)

// fitchResponse represents the GraphQL response structure
type fitchResponse struct {
	Data struct {
		GetEntity *fitchEntity `json:"getEntity"`
	} `json:"data"`
	Errors []struct {
		Message string `json:"message"`
	} `json:"errors,omitempty"`
}

type fitchEntity struct {
	Slug    string        `json:"slug"`
	Name    string        `json:"name"`
	ID      int64         `json:"id"`
	Ratings []fitchRating `json:"ratings"`
}

type fitchRating struct {
	ID                           string  `json:"id"`
	OrangeDisplay                bool    `json:"orangeDisplay"`
	CorrectionFlag               bool    `json:"correctionFlag"`
	RatingChangeDate             string  `json:"ratingChangeDate"`
	RatingActionDescription      string  `json:"ratingActionDescription"`
	RatingCode                   string  `json:"ratingCode"`
	RatingAlertCode              *string `json:"ratingAlertCode"`
	RatingEffectiveDate          string  `json:"ratingEffectiveDate"`
	RatingTypeDescription        string  `json:"ratingTypeDescription"`
	RatingLocalValue             string  `json:"ratingLocalValue"`
	RatingLocalActionDescription string  `json:"ratingLocalActionDescription"`
	RecoveryRatingValue          *string `json:"recoveryRatingValue"`
}

// parseResponse converts the Fitch API response to domain ratings
func parseResponse(body []byte, institutionID, issuerID uuid.UUID, evidenceID *uuid.UUID, logger *slog.Logger) ([]domain.Rating, error) {
	var resp fitchResponse
	if err := json.Unmarshal(body, &resp); err != nil {
		return nil, fmt.Errorf("failed to unmarshal response: %w", err)
	}

	// Check for GraphQL errors
	if len(resp.Errors) > 0 {
		return nil, fmt.Errorf("%w: %s", domain.ErrGraphQLError, resp.Errors[0].Message)
	}

	// Check if entity was found
	if resp.Data.GetEntity == nil {
		return nil, domain.ErrEntityNotFound
	}

	ratings := make([]domain.Rating, 0, len(resp.Data.GetEntity.Ratings))

	for _, fr := range resp.Data.GetEntity.Ratings {
		// Skip withdrawn ratings (WD = Withdrawn)
		if fr.RatingCode == "WD" {
			logger.Debug("skipping withdrawn rating",
				"rating_type", fr.RatingTypeDescription,
				"fitch_id", fr.ID,
			)
			continue
		}

		// Parse rating effective date
		ratingDate, err := parseRatingDate(fr.RatingEffectiveDate)
		if err != nil {
			logger.Warn("failed to parse rating date, using change date",
				"rating_type", fr.RatingTypeDescription,
				"effective_date", fr.RatingEffectiveDate,
				"error", err,
			)
			// Try change date as fallback
			ratingDate, err = parseRatingDate(fr.RatingChangeDate)
			if err != nil {
				return nil, fmt.Errorf("failed to parse any date for %s: %w", fr.RatingTypeDescription, err)
			}
		}

		// Normalize rating name to UPPER_SNAKE_CASE
		ratingName := shared.NormalizeRatingName(fr.RatingTypeDescription)

		// Build custom params with Fitch-specific fields
		customParams := map[string]interface{}{
			"fitch_id":       fr.ID,
			"action":         fr.RatingActionDescription,
			"local_value":    fr.RatingLocalValue,
			"local_action":   fr.RatingLocalActionDescription,
			"orange_display": fr.OrangeDisplay,
			"correction":     fr.CorrectionFlag,
		}

		if fr.RatingAlertCode != nil && *fr.RatingAlertCode != "" {
			customParams["alert_code"] = *fr.RatingAlertCode
		}
		if fr.RecoveryRatingValue != nil && *fr.RecoveryRatingValue != "" {
			customParams["recovery_value"] = *fr.RecoveryRatingValue
		}

		// Compute content hash for idempotency
		contentHash := shared.ComputeRatingHash(
			issuerID,
			ratingName,
			fr.RatingCode,
			ratingDate,
			customParams,
		)

		rating := domain.Rating{
			ID:                     uuid.New(),
			FinancialInstitutionID: institutionID,
			IssuerID:               issuerID,
			RatingName:             ratingName,
			RatingLabel:            fr.RatingCode,
			RatingDate:             ratingDate,
			CustomParams:           customParams,
			SourceEvidenceID:       evidenceID,
			ContentHash:            contentHash,
		}

		ratings = append(ratings, rating)
	}

	return ratings, nil
}

// parseRatingDate parses the Fitch date format (ISO 8601)
// Example: "2025-12-12T15:02:52.000Z"
func parseRatingDate(dateStr string) (time.Time, error) {
	if dateStr == "" {
		return time.Time{}, fmt.Errorf("empty date string")
	}

	// Try RFC3339 format first (most common)
	t, err := time.Parse(time.RFC3339, dateStr)
	if err == nil {
		return t, nil
	}

	// Try with milliseconds
	t, err = time.Parse("2006-01-02T15:04:05.000Z", dateStr)
	if err == nil {
		return t, nil
	}

	// Try date only
	t, err = time.Parse("2006-01-02", dateStr)
	if err == nil {
		return t, nil
	}

	return time.Time{}, fmt.Errorf("unable to parse date: %s", dateStr)
}
