package domain

import "context"

const MerchantLogoJobName = "banking_merchant_logo_dev"

type PendingMerchantDomain struct {
	Domain string
}

type MerchantLogoRepository interface {
	FindPendingLogoHydration(ctx context.Context, limit int) ([]PendingMerchantDomain, error)
	UpsertLogoAssetID(ctx context.Context, domain string, assetID string) error
}
