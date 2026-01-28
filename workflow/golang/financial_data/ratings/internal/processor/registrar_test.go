package processor

import (
	"context"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
)

type fakeRatingRepo struct {
	records map[string]domain.Rating
}

func newFakeRatingRepo() *fakeRatingRepo {
	return &fakeRatingRepo{records: make(map[string]domain.Rating)}
}

func (f *fakeRatingRepo) GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) ([]domain.Rating, error) {
	var results []domain.Rating
	for _, rating := range f.records {
		if rating.FinancialInstitutionID == institutionID && rating.IssuerID == issuerID {
			results = append(results, rating)
		}
	}
	return results, nil
}

func (f *fakeRatingRepo) GetByInstitutionIssuerAndName(ctx context.Context, institutionID, issuerID uuid.UUID, ratingName string) (*domain.Rating, error) {
	key := f.key(institutionID, issuerID, ratingName)
	rating, ok := f.records[key]
	if !ok {
		return nil, domain.ErrRatingNotFound
	}
	return &rating, nil
}

func (f *fakeRatingRepo) Upsert(ctx context.Context, rating *domain.Rating) (bool, error) {
	key := f.key(rating.FinancialInstitutionID, rating.IssuerID, rating.RatingName)
	_, exists := f.records[key]
	f.records[key] = *rating
	return !exists, nil
}

func (f *fakeRatingRepo) UpsertBatch(ctx context.Context, ratings []domain.Rating) (int, int, error) {
	created := 0
	updated := 0
	for i := range ratings {
		rating := &ratings[i]
		key := f.key(rating.FinancialInstitutionID, rating.IssuerID, rating.RatingName)
		_, exists := f.records[key]
		f.records[key] = *rating
		if exists {
			updated++
		} else {
			created++
		}
	}
	return created, updated, nil
}

func (f *fakeRatingRepo) key(instID, issuerID uuid.UUID, name string) string {
	return instID.String() + "|" + issuerID.String() + "|" + name
}

type fakeHistoryRepo struct {
	inserted []domain.RatingHistory
}

func (f *fakeHistoryRepo) Insert(ctx context.Context, history domain.RatingHistory) error {
	f.inserted = append(f.inserted, history)
	return nil
}

func (f *fakeHistoryRepo) InsertBatch(ctx context.Context, histories []domain.RatingHistory) error {
	f.inserted = append(f.inserted, histories...)
	return nil
}

func (f *fakeHistoryRepo) GetByInstitution(ctx context.Context, institutionID uuid.UUID, limit int) ([]domain.RatingHistory, error) {
	return nil, nil
}

func (f *fakeHistoryRepo) GetByInstitutionAndRatingName(ctx context.Context, institutionID uuid.UUID, ratingName string, limit int) ([]domain.RatingHistory, error) {
	return nil, nil
}

type fakeEvidenceRepo struct {
	records map[string]domain.SourceEvidence
}

func newFakeEvidenceRepo() *fakeEvidenceRepo {
	return &fakeEvidenceRepo{records: make(map[string]domain.SourceEvidence)}
}

func (f *fakeEvidenceRepo) Insert(ctx context.Context, evidence *domain.SourceEvidence) error {
	f.records[evidence.ContentHash] = *evidence
	return nil
}

func (f *fakeEvidenceRepo) GetByHash(ctx context.Context, hash string) (*domain.SourceEvidence, error) {
	evidence, ok := f.records[hash]
	if !ok {
		return nil, domain.ErrEvidenceNotFound
	}
	return &evidence, nil
}

func (f *fakeEvidenceRepo) Exists(ctx context.Context, hash string) (bool, error) {
	_, ok := f.records[hash]
	return ok, nil
}

func (f *fakeEvidenceRepo) GetByID(ctx context.Context, id uuid.UUID) (*domain.SourceEvidence, error) {
	for _, evidence := range f.records {
		if evidence.ID == id {
			return &evidence, nil
		}
	}
	return nil, domain.ErrEvidenceNotFound
}

func TestDefaultRegistrar_Register_MixedChanges(t *testing.T) {
	instID := uuid.New()
	issuerID := uuid.New()
	now := time.Date(2024, 1, 10, 0, 0, 0, 0, time.UTC)

	ratingRepo := newFakeRatingRepo()
	historyRepo := &fakeHistoryRepo{}
	evidenceRepo := newFakeEvidenceRepo()

	ratingRepo.records[ratingRepo.key(instID, issuerID, "EXISTING_UPDATED")] = domain.Rating{
		ID:                     uuid.New(),
		FinancialInstitutionID: instID,
		IssuerID:               issuerID,
		RatingName:             "EXISTING_UPDATED",
		RatingLabel:            "BBB",
		RatingDate:             now,
		ContentHash:            "hash-old",
	}
	ratingRepo.records[ratingRepo.key(instID, issuerID, "EXISTING_SAME")] = domain.Rating{
		ID:                     uuid.New(),
		FinancialInstitutionID: instID,
		IssuerID:               issuerID,
		RatingName:             "EXISTING_SAME",
		RatingLabel:            "A",
		RatingDate:             now,
		ContentHash:            "hash-same",
	}

	registrar := NewDefaultRegistrar(ratingRepo, historyRepo, evidenceRepo, testLogger())

	result := &domain.FetchResult{
		InstitutionID: instID,
		IssuerID:      issuerID,
		Ratings: []domain.Rating{
			{
				ID:                     uuid.New(),
				FinancialInstitutionID: instID,
				IssuerID:               issuerID,
				RatingName:             "NEW",
				RatingLabel:            "AA",
				RatingDate:             now,
				ContentHash:            "hash-new",
			},
			{
				ID:                     uuid.New(),
				FinancialInstitutionID: instID,
				IssuerID:               issuerID,
				RatingName:             "EXISTING_UPDATED",
				RatingLabel:            "BB+",
				RatingDate:             now,
				ContentHash:            "hash-newer",
			},
			{
				ID:                     uuid.New(),
				FinancialInstitutionID: instID,
				IssuerID:               issuerID,
				RatingName:             "EXISTING_SAME",
				RatingLabel:            "A",
				RatingDate:             now,
				ContentHash:            "hash-same",
			},
		},
		Evidence: domain.SourceEvidence{
			ID:          uuid.New(),
			IssuerID:    issuerID,
			FetchedAt:   now,
			RawPayload:  "{}",
			ContentHash: "evidence-hash",
		},
	}

	stats, err := registrar.Register(context.Background(), domain.InstitutionIssuerMapping{
		RatingIssuerID:         issuerID,
		FinancialInstitutionID: instID,
		Enabled:                true,
	}, result)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if stats.RatingsCreated != 1 || stats.RatingsUpdated != 1 || stats.RatingsUnchanged != 1 {
		t.Fatalf("unexpected stats: created=%d updated=%d unchanged=%d", stats.RatingsCreated, stats.RatingsUpdated, stats.RatingsUnchanged)
	}
	if stats.EvidenceStored != 1 {
		t.Fatalf("expected evidence stored")
	}
	if len(historyRepo.inserted) != 2 {
		t.Fatalf("expected 2 history records, got %d", len(historyRepo.inserted))
	}
}

func TestDefaultRegistrar_StoreEvidence_Dedupes(t *testing.T) {
	registrar := NewDefaultRegistrar(newFakeRatingRepo(), &fakeHistoryRepo{}, newFakeEvidenceRepo(), testLogger())

	evidence := domain.SourceEvidence{
		ID:          uuid.New(),
		IssuerID:    uuid.New(),
		FetchedAt:   time.Now().UTC(),
		RawPayload:  "{}",
		ContentHash: "hash-1234567890abcd",
	}

	id, stored, err := registrar.StoreEvidence(context.Background(), &evidence)
	if err != nil || !stored || id == nil {
		t.Fatalf("expected evidence stored")
	}

	id, stored, err = registrar.StoreEvidence(context.Background(), &evidence)
	if err != nil || stored || id == nil {
		t.Fatalf("expected evidence deduped")
	}
}
