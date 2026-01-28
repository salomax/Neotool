package processor

import (
	"context"
	"errors"
	"testing"

	"github.com/google/uuid"
	"github.com/invistus/ratings/internal/domain"
)

type fakeMappingRepo struct {
	list        []domain.InstitutionMapping
	listErr     error
	upserts     []domain.InstitutionIssuerMapping
	upsertErr   error
	enabledList []domain.InstitutionIssuerMapping
}

func (f *fakeMappingRepo) GetEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) ([]domain.InstitutionIssuerMapping, error) {
	return f.enabledList, nil
}

func (f *fakeMappingRepo) ListInstitutionsWithIssuerMapping(ctx context.Context, issuerID uuid.UUID) ([]domain.InstitutionMapping, error) {
	if f.listErr != nil {
		return nil, f.listErr
	}
	return f.list, nil
}

func (f *fakeMappingRepo) GetByInstitutionAndIssuer(ctx context.Context, institutionID, issuerID uuid.UUID) (*domain.InstitutionIssuerMapping, error) {
	return nil, domain.ErrMappingNotFound
}

func (f *fakeMappingRepo) Upsert(ctx context.Context, mapping *domain.InstitutionIssuerMapping) error {
	if f.upsertErr != nil {
		return f.upsertErr
	}
	f.upserts = append(f.upserts, *mapping)
	return nil
}

func (f *fakeMappingRepo) CountEnabledByIssuer(ctx context.Context, issuerID uuid.UUID) (int, error) {
	return len(f.enabledList), nil
}

type fakeFitchClient struct {
	suggestSlug   string
	suggestErr    error
	entityName    string
	entityNameErr error
}

func (f *fakeFitchClient) SuggestSlug(ctx context.Context, term string) (string, error) {
	if f.suggestErr != nil {
		return "", f.suggestErr
	}
	return f.suggestSlug, nil
}

func (f *fakeFitchClient) FetchEntityName(ctx context.Context, slug string) (string, error) {
	if f.entityNameErr != nil {
		return "", f.entityNameErr
	}
	return f.entityName, nil
}

func (f *fakeFitchClient) FetchRatings(ctx context.Context, mapping domain.InstitutionIssuerMapping) (*domain.FetchResult, error) {
	return nil, nil
}

func (f *fakeFitchClient) GetIssuerCode() string {
	return "FITCH"
}

func TestFitchStrategy_ListMappings_ExistingEnabled(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	mapping := domain.InstitutionIssuerMapping{
		ID:                     uuid.New(),
		RatingIssuerID:         issuerID,
		FinancialInstitutionID: instID,
		Config:                 domain.MappingConfig{Slug: "slug-1"},
		Enabled:                true,
	}
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "Banco"},
				Mapping:     &mapping,
			},
		},
	}
	client := &fakeFitchClient{}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 1 {
		t.Fatalf("expected 1 mapping, got %d", len(mappings))
	}
	if len(repo.upserts) != 0 {
		t.Fatalf("expected no upserts, got %d", len(repo.upserts))
	}
}

func TestFitchStrategy_ListMappings_DisabledMappingSkipped(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	mapping := domain.InstitutionIssuerMapping{
		ID:                     uuid.New(),
		RatingIssuerID:         issuerID,
		FinancialInstitutionID: instID,
		Enabled:                false,
	}
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "Banco"},
				Mapping:     &mapping,
			},
		},
	}
	client := &fakeFitchClient{}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 0 {
		t.Fatalf("expected 0 mappings, got %d", len(mappings))
	}
}

func TestFitchStrategy_ListMappings_CreatesDisabledWhenNoSlug(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "Banco"},
				Mapping:     nil,
			},
		},
	}
	client := &fakeFitchClient{suggestSlug: ""}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 0 {
		t.Fatalf("expected 0 mappings, got %d", len(mappings))
	}
	if len(repo.upserts) != 1 || repo.upserts[0].Enabled {
		t.Fatalf("expected disabled mapping upsert")
	}
}

func TestFitchStrategy_ListMappings_CreatesDisabledOnMismatch(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "Banco"},
				Mapping:     nil,
			},
		},
	}
	client := &fakeFitchClient{suggestSlug: "slug-1", entityName: "Outro Banco"}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 0 {
		t.Fatalf("expected 0 mappings, got %d", len(mappings))
	}
	if len(repo.upserts) != 1 || repo.upserts[0].Enabled {
		t.Fatalf("expected disabled mapping upsert")
	}
}

func TestFitchStrategy_ListMappings_CreatesEnabledOnMatch(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "Banco do Brasil S.A."},
				Mapping:     nil,
			},
		},
	}
	client := &fakeFitchClient{suggestSlug: "banco-do-brasil", entityName: "BANCO DO BRASIL S.A."}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 1 {
		t.Fatalf("expected 1 mapping, got %d", len(mappings))
	}
	if mappings[0].Config.Slug != "banco-do-brasil" {
		t.Fatalf("expected slug to be stored")
	}
	if len(repo.upserts) != 1 || !repo.upserts[0].Enabled {
		t.Fatalf("expected enabled mapping upsert")
	}
}

func TestFitchStrategy_ListMappings_CreatesEnabledOnNormalizedMatch(t *testing.T) {
	issuerID := uuid.New()
	instID := uuid.New()
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: instID, LegalName: "BANCO SEMEAR S. A."},
				Mapping:     nil,
			},
		},
	}
	client := &fakeFitchClient{suggestSlug: "banco-semear", entityName: "BANCO SEMEAR S.A."}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 0 {
		t.Fatalf("expected no errors, got %d", errorsCount)
	}
	if len(mappings) != 1 {
		t.Fatalf("expected 1 mapping, got %d", len(mappings))
	}
	if mappings[0].Config.Slug != "banco-semear" {
		t.Fatalf("expected slug to be stored")
	}
	if len(repo.upserts) != 1 || !repo.upserts[0].Enabled {
		t.Fatalf("expected enabled mapping upsert")
	}
}

func TestFitchStrategy_ListMappings_SuggestError(t *testing.T) {
	issuerID := uuid.New()
	repo := &fakeMappingRepo{
		list: []domain.InstitutionMapping{
			{
				Institution: domain.FinancialInstitution{ID: uuid.New(), LegalName: "Banco"},
				Mapping:     nil,
			},
		},
	}
	client := &fakeFitchClient{suggestErr: errors.New("boom")}
	strategy := NewFitchStrategy(domain.RatingIssuer{ID: issuerID, Code: "FITCH"}, client, repo, testLogger())

	mappings, errorsCount, err := strategy.ListMappings(context.Background(), domain.RatingIssuer{ID: issuerID})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if errorsCount != 1 {
		t.Fatalf("expected 1 error, got %d", errorsCount)
	}
	if len(mappings) != 0 {
		t.Fatalf("expected 0 mappings, got %d", len(mappings))
	}
}
