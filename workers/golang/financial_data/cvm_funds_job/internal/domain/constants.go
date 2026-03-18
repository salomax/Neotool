package domain

const (
	// SourceCVM is the data source identifier stored in investment_asset.source
	// and asset_daily_price.source.
	SourceCVM = "CVM"

	// JobNameCatalog is the key used in import_job_control for fund catalog runs.
	JobNameCatalog = "cvm_catalog"

	// JobNameNAV is the key used in import_job_control for daily NAV runs.
	JobNameNAV = "cvm_nav"
)
