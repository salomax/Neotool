module github.com/salomax/invistus/workers/golang/financial_data/cvm-funds

go 1.24.0

require (
	github.com/caarlos0/env/v10 v10.0.0
	github.com/google/uuid v1.6.0
	github.com/jackc/pgx/v5 v5.5.5
	github.com/salomax/invistus/workflow/golang/shared/health v0.0.0
	github.com/salomax/invistus/workflow/golang/shared/workerpool v0.0.0
)

require (
	github.com/IBM/sarama v1.43.0 // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/eapache/go-resiliency v1.6.0 // indirect
	github.com/eapache/go-xerial-snappy v0.0.0-20230731223053-c322873962e3 // indirect
	github.com/eapache/queue v1.1.0 // indirect
	github.com/golang/snappy v0.0.4 // indirect
	github.com/hashicorp/errwrap v1.0.0 // indirect
	github.com/hashicorp/go-multierror v1.1.1 // indirect
	github.com/hashicorp/go-uuid v1.0.3 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20231201235250-de7065d80cb9 // indirect
	github.com/jackc/puddle/v2 v2.2.1 // indirect
	github.com/jcmturner/aescts/v2 v2.0.0 // indirect
	github.com/jcmturner/dnsutils/v2 v2.0.0 // indirect
	github.com/jcmturner/gofork v1.7.6 // indirect
	github.com/jcmturner/gokrb5/v8 v8.4.4 // indirect
	github.com/jcmturner/rpc/v2 v2.0.3 // indirect
	github.com/klauspost/compress v1.17.7 // indirect
	github.com/pierrec/lz4/v4 v4.1.21 // indirect
	github.com/rcrowley/go-metrics v0.0.0-20201227073835-cf1acfcdf475 // indirect
	golang.org/x/crypto v0.35.0 // indirect
	golang.org/x/net v0.21.0 // indirect
	golang.org/x/sync v0.11.0 // indirect
	golang.org/x/text v0.22.0 // indirect
)

replace (
	github.com/salomax/invistus/workflow/golang/shared/health => ../../shared/health
	github.com/salomax/invistus/workflow/golang/shared/workerpool => ../../shared/workerpool
)
