package domain

import (
	"context"

	"github.com/google/uuid"
)

const IssuerLogoJobName = "banking_issuer_logo_dev"

type PendingIssuer struct {
	ID     uuid.UUID
	Name   string
	Domain string
}

type IssuerRepository interface {
	FindPendingLogoHydration(ctx context.Context, limit int) ([]PendingIssuer, error)
	UpdateLogoKey(ctx context.Context, id uuid.UUID, logoKey string) error
}

type LogoImageClient interface {
	FetchLogo(ctx context.Context, domain string) ([]byte, error)
}

type AssetUploader interface {
	UploadLogo(ctx context.Context, logoData []byte, filename string, mimeType string) (string, error)
}
