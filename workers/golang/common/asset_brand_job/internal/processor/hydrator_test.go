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
	brand  *domain.ExistingBrand
	err    error
}

func (f *fakeBrandRepo) FindBrandByNormalizedName(ctx context.Context, normalizedName string) (*domain.ExistingBrand, error) {
	return f.brand, f.err
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
	result *domain.BrandLookupResult
	err    error
}

func (f *fakeClient) LookupBrand(ctx context.Context, asset domain.PendingAsset) (*domain.BrandLookupResult, error) {
	return f.result, f.err
}

type fakeBrandAssetUploader struct {
	assetID  string
	uploads  int
	filename string
	mimeType string
	data     []byte
	err      error
}

func (f *fakeBrandAssetUploader) UploadLogo(ctx context.Context, logoData []byte, filename string, mimeType string) (string, error) {
	f.uploads++
	f.filename = filename
	f.mimeType = mimeType
	f.data = append([]byte(nil), logoData...)
	if f.assetID == "" {
		f.assetID = uuid.NewString()
	}
	return f.assetID, f.err
}

func testLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

func TestResolveConfidence_UsesExactISINLookup(t *testing.T) {
	score := resolveConfidence(
		domain.PendingAsset{IsinCode: "BRPETRACNPR6"},
		&domain.BrandLookupResult{QueryUsed: "BRPETRACNPR6"},
	)

	if score != 1 {
		t.Fatalf("expected confidence 1.0, got %f", score)
	}
}

func TestRun_SavesAutoApprovedCandidate(t *testing.T) {
	assets := &fakeAssetRepo{
		assets: []domain.PendingAsset{
			{ID: uuid.New(), Ticker: "PETR4", Name: "PETROBRAS", AssetType: "EQUITY", IsinCode: "BRPETRACNPR6"},
		},
	}
	brands := &fakeBrandRepo{}
	jobs := &fakeJobRepo{}
	uploader := &fakeBrandAssetUploader{assetID: uuid.NewString()}
	client := &fakeClient{
		result: &domain.BrandLookupResult{
			QueryUsed:  "BRPETRACNPR6",
			LogoURL:    "https://cdn.brandfetch.io/isin/BRPETRACNPR6/w/400/h/400/?c=test-client",
			LogoData:   []byte("png"),
			MimeType:   "image/png",
			RawPayload: `{"provider":"BRANDFETCH_ISIN","isinCode":"BRPETRACNPR6","resolvedType":"default"}`,
		},
	}

	hydrator := NewHydrator(assets, brands, jobs, client, uploader, 10, 0, 0.93, testLogger())
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
	if brands.inputs[0].Source != domain.SourceBrandfetchISIN {
		t.Fatalf("unexpected source: %s", brands.inputs[0].Source)
	}
	if brands.inputs[0].LogoAssetID == nil {
		t.Fatal("expected logo asset id to be set")
	}
	if uploader.uploads != 1 {
		t.Fatalf("expected one upload, got %d", uploader.uploads)
	}
}

func TestRun_SkipsWhenProviderReturnsNoResult(t *testing.T) {
	assets := &fakeAssetRepo{
		assets: []domain.PendingAsset{
			{ID: uuid.New(), Ticker: "RAIZ4", Name: "RAIZEN", AssetType: "EQUITY", IsinCode: "BRRAIZACNOR4"},
		},
	}
	brands := &fakeBrandRepo{}
	jobs := &fakeJobRepo{}
	uploader := &fakeBrandAssetUploader{}
	client := &fakeClient{}

	hydrator := NewHydrator(assets, brands, jobs, client, uploader, 10, 0, 0.93, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(brands.inputs) != 0 {
		t.Fatalf("expected no upserts, got %d", len(brands.inputs))
	}
	if uploader.uploads != 0 {
		t.Fatalf("expected no uploads, got %d", uploader.uploads)
	}
}

func TestRun_ReusesExistingBrandLogoAsset(t *testing.T) {
	existingAssetID := uuid.New()
	assets := &fakeAssetRepo{
		assets: []domain.PendingAsset{
			{ID: uuid.New(), Ticker: "VALE5", Name: "VALE", AssetType: "EQUITY", IsinCode: "BRVALEACNOR0"},
		},
	}
	brands := &fakeBrandRepo{
		brand: &domain.ExistingBrand{ID: uuid.New(), LogoAssetID: &existingAssetID},
	}
	jobs := &fakeJobRepo{}
	uploader := &fakeBrandAssetUploader{}
	client := &fakeClient{
		result: &domain.BrandLookupResult{
			QueryUsed:  "BRVALEACNOR0",
			LogoURL:    "https://cdn.brandfetch.io/isin/BRVALEACNOR0/w/400/h/400/?c=test-client",
			LogoData:   []byte("png"),
			MimeType:   "image/png",
			RawPayload: `{"provider":"BRANDFETCH_ISIN","isinCode":"BRVALEACNOR0","resolvedType":"default"}`,
		},
	}

	hydrator := NewHydrator(assets, brands, jobs, client, uploader, 10, 0, 0.93, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if uploader.uploads != 0 {
		t.Fatalf("expected no upload, got %d", uploader.uploads)
	}
	if len(brands.inputs) != 1 {
		t.Fatalf("expected one upsert, got %d", len(brands.inputs))
	}
	if brands.inputs[0].LogoAssetID == nil || *brands.inputs[0].LogoAssetID != existingAssetID {
		t.Fatalf("expected existing logo asset id %s, got %+v", existingAssetID, brands.inputs[0].LogoAssetID)
	}
}
