module github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist

go 1.24.0

require (
	github.com/caarlos0/env/v10 v10.0.0
	github.com/google/uuid v1.6.0
	github.com/jackc/pgx/v5 v5.5.5
	github.com/salomax/invistus/workflow/golang/shared/health v0.0.0
	github.com/salomax/invistus/workflow/golang/shared/workerpool v0.0.0
	golang.org/x/text v0.22.0
)

require (
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20231201235250-de7065d80cb9 // indirect
	github.com/jackc/puddle/v2 v2.2.1 // indirect
	golang.org/x/crypto v0.35.0 // indirect
	golang.org/x/sync v0.11.0 // indirect
)

replace (
	github.com/salomax/invistus/workflow/golang/shared/health => ../../shared/health
	github.com/salomax/invistus/workflow/golang/shared/workerpool => ../../shared/workerpool
)
