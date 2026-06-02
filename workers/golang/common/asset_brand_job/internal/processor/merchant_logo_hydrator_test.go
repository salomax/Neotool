package processor

import (
	"context"
	"fmt"
	"testing"

	"github.com/salomax/invistus/workers/golang/financial_data/asset-brand/internal/domain"
)

type fakeMerchantLogoRepo struct {
	domains []domain.PendingMerchantDomain
	upserts map[string]string
	err     error
}

func (f *fakeMerchantLogoRepo) FindPendingLogoHydration(ctx context.Context, limit int) ([]domain.PendingMerchantDomain, error) {
	if f.err != nil {
		return nil, f.err
	}
	if len(f.domains) <= limit {
		return f.domains, nil
	}
	return f.domains[:limit], nil
}

func (f *fakeMerchantLogoRepo) UpsertLogoAssetID(ctx context.Context, domainName string, assetID string) error {
	if f.upserts == nil {
		f.upserts = map[string]string{}
	}
	f.upserts[domainName] = assetID
	return nil
}

func TestMerchantLogoHydrator_RunUpsertsMerchantLogoCache(t *testing.T) {
	merchants := &fakeMerchantLogoRepo{
		domains: []domain.PendingMerchantDomain{
			{Domain: "netflix.com"},
		},
	}
	jobs := &fakeJobRepo{}
	logoClient := &fakeLogoImageClient{bytes: []byte("png-bytes")}
	assetUploader := &fakeAssetUploader{assetID: "asset-merchant-123"}

	hydrator := NewMerchantLogoHydrator(merchants, jobs, logoClient, assetUploader, 10, 0, testLogger())
	if err := hydrator.Run(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if jobs.running != 1 || jobs.success != 1 {
		t.Fatalf("expected running=1 success=1, got running=%d success=%d", jobs.running, jobs.success)
	}
	if got := merchants.upserts["netflix.com"]; got != "asset-merchant-123" {
		t.Fatalf("expected domain netflix.com to be upserted with asset-merchant-123, got %q", got)
	}
	if assetUploader.filename != "merchants/netflix.com.png" {
		t.Fatalf("unexpected filename: %q", assetUploader.filename)
	}
	if assetUploader.mimeType != "image/png" {
		t.Fatalf("unexpected mime type: %q", assetUploader.mimeType)
	}
}

func TestMerchantLogoHydrator_RunFailsWhenDomainProcessingFails(t *testing.T) {
	merchants := &fakeMerchantLogoRepo{
		domains: []domain.PendingMerchantDomain{
			{Domain: "openai.com"},
		},
	}
	jobs := &fakeJobRepo{}
	logoClient := &fakeLogoImageClient{err: fmt.Errorf("boom")}
	assetUploader := &fakeAssetUploader{assetID: "asset-merchant-123"}

	hydrator := NewMerchantLogoHydrator(merchants, jobs, logoClient, assetUploader, 10, 0, testLogger())
	if err := hydrator.Run(context.Background()); err == nil {
		t.Fatalf("expected error, got nil")
	}
}
