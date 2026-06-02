package processor

import (
	"context"
	"fmt"
	"testing"

	"github.com/google/uuid"
	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

type fakeIssuerRepo struct {
	issuers []domain.PendingIssuer
	updates map[uuid.UUID]string
	err     error
}

func (f *fakeIssuerRepo) FindPendingLogoHydration(ctx context.Context, limit int) ([]domain.PendingIssuer, error) {
	if f.err != nil {
		return nil, f.err
	}
	if len(f.issuers) <= limit {
		return f.issuers, nil
	}
	return f.issuers[:limit], nil
}

func (f *fakeIssuerRepo) UpdateLogoKey(ctx context.Context, id uuid.UUID, logoKey string) error {
	if f.updates == nil {
		f.updates = map[uuid.UUID]string{}
	}
	f.updates[id] = logoKey
	return nil
}

type fakeLogoImageClient struct {
	bytes []byte
	err   error
}

func (f *fakeLogoImageClient) FetchLogo(ctx context.Context, domain string) ([]byte, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.bytes, nil
}

type fakeAssetUploader struct {
	assetID  string
	err      error
	filename string
	mimeType string
}

func (f *fakeAssetUploader) UploadLogo(ctx context.Context, logoData []byte, filename string, mimeType string) (string, error) {
	if f.err != nil {
		return "", f.err
	}
	f.filename = filename
	f.mimeType = mimeType
	return f.assetID, nil
}

func TestIssuerLogoHydrator_RunUpdatesIssuerLogoKey(t *testing.T) {
	issuerID := uuid.New()
	issuers := &fakeIssuerRepo{
		issuers: []domain.PendingIssuer{
			{ID: issuerID, Name: "Banco Inter", Domain: "inter.co"},
		},
	}
	jobs := &fakeJobRepo{}
	logoClient := &fakeLogoImageClient{bytes: []byte("png-bytes")}
	assetUploader := &fakeAssetUploader{assetID: "asset-123"}

	hydrator := NewIssuerLogoHydrator(issuers, jobs, logoClient, assetUploader, 10, 0, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if jobs.running != 1 || jobs.success != 1 {
		t.Fatalf("expected running=1 success=1, got running=%d success=%d", jobs.running, jobs.success)
	}
	if got := issuers.updates[issuerID]; got != "asset-123" {
		t.Fatalf("expected issuer logo_key to be updated to asset-123, got %q", got)
	}
	if assetUploader.filename != fmt.Sprintf("issuers/%s.png", issuerID) {
		t.Fatalf("unexpected filename: %q", assetUploader.filename)
	}
	if assetUploader.mimeType != "image/png" {
		t.Fatalf("unexpected mime type: %q", assetUploader.mimeType)
	}
}

func TestIssuerLogoHydrator_RunFailsWhenIssuerProcessingFails(t *testing.T) {
	issuers := &fakeIssuerRepo{
		issuers: []domain.PendingIssuer{
			{ID: uuid.New(), Name: "Banco XP", Domain: "xpi.com.br"},
		},
	}
	jobs := &fakeJobRepo{}
	logoClient := &fakeLogoImageClient{err: fmt.Errorf("boom")}
	assetUploader := &fakeAssetUploader{assetID: "asset-123"}

	hydrator := NewIssuerLogoHydrator(issuers, jobs, logoClient, assetUploader, 10, 0, testLogger())
	if err := hydrator.Run(context.Background()); err == nil {
		t.Fatalf("expected error, got nil")
	}
}
