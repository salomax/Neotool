module github.com/salomax/invistus/workers/golang/financial_data/b3-cotahist

go 1.25.0

require (
	github.com/IBM/sarama v1.43.0
	github.com/caarlos0/env/v10 v10.0.0
	github.com/go-rod/rod v0.116.2
	github.com/google/uuid v1.6.0
	github.com/jackc/pgx/v5 v5.9.0
	github.com/salomax/invistus/workflow/golang/shared/health v0.0.0
	golang.org/x/text v0.29.0
	golang.org/x/time v0.15.0
)

require (
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/eapache/go-resiliency v1.6.0 // indirect
	github.com/eapache/go-xerial-snappy v0.0.0-20230731223053-c322873962e3 // indirect
	github.com/eapache/queue v1.1.0 // indirect
	github.com/golang/snappy v0.0.4 // indirect
	github.com/hashicorp/errwrap v1.0.0 // indirect
	github.com/hashicorp/go-multierror v1.1.1 // indirect
	github.com/hashicorp/go-uuid v1.0.3 // indirect
	github.com/jackc/pgpassfile v1.0.0 // indirect
	github.com/jackc/pgservicefile v0.0.0-20240606120523-5a60cdf6a761 // indirect
	github.com/jackc/puddle/v2 v2.2.2 // indirect
	github.com/jcmturner/aescts/v2 v2.0.0 // indirect
	github.com/jcmturner/dnsutils/v2 v2.0.0 // indirect
	github.com/jcmturner/gofork v1.7.6 // indirect
	github.com/jcmturner/gokrb5/v8 v8.4.4 // indirect
	github.com/jcmturner/rpc/v2 v2.0.3 // indirect
	github.com/klauspost/compress v1.17.7 // indirect
	github.com/pierrec/lz4/v4 v4.1.21 // indirect
	github.com/rcrowley/go-metrics v0.0.0-20201227073835-cf1acfcdf475 // indirect
	github.com/ysmood/fetchup v0.2.3 // indirect
	github.com/ysmood/goob v0.4.0 // indirect
	github.com/ysmood/got v0.40.0 // indirect
	github.com/ysmood/gson v0.7.3 // indirect
	github.com/ysmood/leakless v0.9.0 // indirect
	golang.org/x/crypto v0.35.0 // indirect
	golang.org/x/net v0.21.0 // indirect
	golang.org/x/sync v0.17.0 // indirect
)

replace (
	github.com/salomax/invistus/workflow/golang/shared/health => ../../shared/health
	github.com/salomax/invistus/workflow/golang/shared/workerpool => ../../shared/workerpool
)
