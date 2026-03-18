package domain

const (
	// JobName is the key used in import_job_control.
	JobName = "b3_cotahist"

	// SourceB3 is stored in investment_asset.source and asset_daily_price.source.
	SourceB3 = "B3"

	// ExchangeB3 is stored in investment_asset.exchange.
	ExchangeB3 = "B3"

	// CurrencyBRL is the default currency for all B3 assets.
	CurrencyBRL = "BRL"

	// Asset types written to investment_asset.asset_type.
	AssetTypeEquity     = "EQUITY"
	AssetTypeFII        = "FII"
	AssetTypeOptionCall = "OPTION_CALL"
	AssetTypeOptionPut  = "OPTION_PUT"

	// BDI codes from the COTAHIST format.
	BDIEquityLot  = "02" // Lote padrão — standard equity lot
	BDIFII        = "12" // Fundos de Investimento Imobiliário
	BDIOptionCall = "78" // Opções de compra
	BDIOptionPut  = "79" // Opções de venda

	// TPMERC values we care about.
	TpMercSpot = "010" // Mercado à vista (spot)

	// Record type identifiers within the COTAHIST fixed-width file.
	RecordTypeData    = "01" // Daily price data
	RecordTypeHeader  = "00"
	RecordTypeTrailer = "99"

	// Lot sizes by asset type.
	LotSizeEquity = 100
	LotSizeFII    = 1
	LotSizeOption = 1

	// Price factor for all B3 assets (prices are stored in BRL, no factor needed).
	PriceFactor = 1
)

// BDIToAssetType maps a CODBDI code to an asset_type string.
// Returns ("", false) when the BDI code is not one we import.
func BDIToAssetType(codbdi string) (assetType string, ok bool) {
	switch codbdi {
	case BDIEquityLot:
		return AssetTypeEquity, true
	case BDIFII:
		return AssetTypeFII, true
	case BDIOptionCall:
		return AssetTypeOptionCall, true
	case BDIOptionPut:
		return AssetTypeOptionPut, true
	default:
		return "", false
	}
}

// LotSizeFor returns the lot size for a given asset type.
func LotSizeFor(assetType string) int {
	if assetType == AssetTypeEquity {
		return LotSizeEquity
	}
	return LotSizeFII // FII and options both use lot size 1
}
