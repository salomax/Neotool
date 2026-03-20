package processor

import (
	"context"
	"io"
	"log/slog"
	"testing"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

type fakeAssetRepo struct {
	assets []domain.PendingAsset
}

func (f *fakeAssetRepo) FindPendingBrandHydration(ctx context.Context, limit int) ([]domain.PendingAsset, error) {
	if len(f.assets) <= limit {
		return f.assets, nil
	}
	return f.assets[:limit], nil
}

type fakeBrandRepo struct {
	inputs []domain.BrandUpsertInput
}

func (f *fakeBrandRepo) UpsertBrandCandidate(ctx context.Context, input domain.BrandUpsertInput) error {
	f.inputs = append(f.inputs, input)
	return nil
}

type fakeJobRepo struct {
	running int
	success int
	failed  int
}

func (f *fakeJobRepo) MarkRunning(ctx context.Context, jobName string) error {
	f.running++
	return nil
}

func (f *fakeJobRepo) MarkSuccess(ctx context.Context, jobName string) error {
	f.success++
	return nil
}

func (f *fakeJobRepo) MarkFailed(ctx context.Context, jobName string, errMsg string) error {
	f.failed++
	return nil
}

type fakeClient struct {
	response domain.SearchResponse
	err      error
}

func (f *fakeClient) Search(ctx context.Context, query string) (domain.SearchResponse, error) {
	return f.response, f.err
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

func TestBuildSearchQuery_RemovesShareClassSuffix(t *testing.T) {
	got := buildSearchQuery("PETROBRAS PN")
	if got != "PETROBRAS" {
		t.Fatalf("expected PETROBRAS, got %q", got)
	}
}

func TestScoreCandidate_ExactMatchAutoApprove(t *testing.T) {
	score := scoreCandidate("PETROBRAS", domain.SearchCandidate{
		Name:    "Petrobras",
		Domain:  "petrobras.com.br",
		LogoURL: "https://img.logo.dev/petrobras.com.br",
	}, 1)

	if score < 0.99 {
		t.Fatalf("expected exact match score >= 0.99, got %f", score)
	}
}

func TestRun_SavesAutoApprovedCandidate(t *testing.T) {
	assets := &fakeAssetRepo{
		assets: []domain.PendingAsset{
			{ID: uuid.New(), Ticker: "PETR4", Name: "PETROBRAS"},
		},
	}
	brands := &fakeBrandRepo{}
	jobs := &fakeJobRepo{}
	client := &fakeClient{
		response: domain.SearchResponse{
			Results: []domain.SearchCandidate{
				{Name: "Petrobras", Domain: "petrobras.com.br", LogoURL: "https://img.logo.dev/petrobras.com.br"},
			},
			RawPayload: `[{"name":"Petrobras"}]`,
		},
	}

	hydrator := NewHydrator(assets, brands, jobs, client, 10, 0, 0.93, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if jobs.running != 1 || jobs.success != 1 {
		t.Fatalf("expected running=1 success=1, got running=%d success=%d", jobs.running, jobs.success)
	}
	if len(brands.inputs) != 1 {
		t.Fatalf("expected one upsert, got %d", len(brands.inputs))
	}
	if brands.inputs[0].Status != domain.StatusAutoApproved {
		t.Fatalf("expected AUTO_APPROVED, got %s", brands.inputs[0].Status)
	}
	if brands.inputs[0].NormalizedName != "PETROBRAS" {
		t.Fatalf("unexpected normalized name: %s", brands.inputs[0].NormalizedName)
	}
}

func TestRun_LeavesWeakMatchPending(t *testing.T) {
	assets := &fakeAssetRepo{
		assets: []domain.PendingAsset{
			{ID: uuid.New(), Ticker: "RAIZ4", Name: "RAIZEN"},
		},
	}
	brands := &fakeBrandRepo{}
	jobs := &fakeJobRepo{}
	client := &fakeClient{
		response: domain.SearchResponse{
			Results: []domain.SearchCandidate{
				{Name: "Raia Drogasil", Domain: "ri.rd.com.br", LogoURL: "https://img.logo.dev/ri.rd.com.br"},
			},
			RawPayload: `[{"name":"Raia Drogasil"}]`,
		},
	}

	hydrator := NewHydrator(assets, brands, jobs, client, 10, 0, 0.93, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(brands.inputs) != 1 {
		t.Fatalf("expected one upsert, got %d", len(brands.inputs))
	}
	if brands.inputs[0].Status != domain.StatusPending {
		t.Fatalf("expected PENDING, got %s", brands.inputs[0].Status)
	}
}
