# Job Spec: CVM — Investment Funds (Fundos de Investimento)

> **Service**: `workers/golang/financial_data/cvm_funds_job`
> **Goal**: Import the investment fund catalog (PGBL, VGBL, Multimercado, Renda Fixa, Ações, Cambial) and daily NAV (cota) data from CVM's open data portal into the `financialdata` database.
> **Effort**: Medium
> **Run frequency**: Catalog weekly (Sunday 02:00 UTC); NAV daily (02:00 UTC)
> **Depends on**: Migration `V1_15` (shared `investment_asset` + `asset_daily_price` tables from spec 02)
> **Pattern reference**: `bacen_ifdata_job` + `indicators_job` — same config, retry, hash, load control, and pgxpool batch patterns. Uses `workers/golang/shared/workerpool`.

---

## Context

The CVM (Comissão de Valores Mobiliários) is Brazil's securities regulator and publishes free open data for all registered investment funds. This covers PGBL, VGBL, Fundos de Renda Fixa, Multimercado, Ações, and Cambial.

Funds do not have B3 tickers. They are uniquely identified by **CNPJ**.

The two source files are large:
- `cad_fi.csv` — ~50 MB, ~60,000 funds
- `inf_diario_fi_YYYYMM.zip` — ~5–15 MB compressed, ~1.5M rows per month when extracted

Both must be processed as streams — never fully loaded into memory. Goroutines and the shared `WorkerPool` are used to saturate DB write throughput while keeping the memory footprint bounded.

---

## Data source

**Publisher**: CVM — `dados.cvm.gov.br`
**Auth**: None — fully public

### File 1: Fund registration catalog
```
URL:    https://dados.cvm.gov.br/dados/FI/CAD/DADOS/cad_fi.csv
Format: CSV (semicolon-separated, UTF-8) — plain, no ZIP
Update: Monthly (or on fund events)
Size:   ~50 MB, ~60,000 funds
```

**Key columns**:

| Column | Description |
|--------|-------------|
| `CNPJ_FUNDO` | Fund CNPJ — primary key |
| `DENOM_SOCIAL` | Official fund name |
| `CLASSE` | ANBIMA class: `Fundo de Ações`, `Renda Fixa`, `Multimercado`, `Cambial` |
| `TIPO_FUNDO` | Sub-type: `PGBL`, `VGBL`, `FIC`, `Master`, etc. |
| `SITUACAO` | `EM FUNCIONAMENTO NORMAL` (active), `CANCELADO`, etc. |
| `DT_INI_ATIV` | Activation date |
| `DT_FIM_ATIV` | End date (if closed) |
| `ADMIN` | Administrator name |
| `GESTOR` | Manager name |

### File 2: Daily NAV (Informes Diários)
```
URL:    https://dados.cvm.gov.br/dados/FI/DOC/INF_DIARIO/DADOS/inf_diario_fi_{YYYYMM}.zip
Format: ZIP → CSV (semicolon-separated, UTF-8)
Update: Daily (current month updated incrementally; previous month finalized by the 10th)
Size:   ~5–15 MB compressed (~40–60 MB extracted); ~1.5M rows/month
```

**Key columns**:

| Column | Description |
|--------|-------------|
| `CNPJ_FUNDO` | Fund CNPJ |
| `DT_COMPTC` | NAV date `YYYY-MM-DD` |
| `VL_TOTAL` | Total fund net assets (patrimônio líquido) |
| `VL_QUOTA` | NAV per quota (cota) — portfolio "price" |
| `VL_CAPTC_DIA` | Daily inflow |
| `VL_RESG_DIA` | Daily redemption |
| `NR_COTST` | Number of shareholders |

---

## Database additions

### Schema changes on top of `V1_15`

**Add `content_hash` columns** (coordinate with spec 02 — may already be in `V1_15`):

```sql
-- V1_16__investment_asset_content_hash.sql
ALTER TABLE financial_data.investment_asset
    ADD COLUMN IF NOT EXISTS content_hash TEXT;

ALTER TABLE financial_data.asset_daily_price
    ADD COLUMN IF NOT EXISTS content_hash TEXT;
```

The existing `import_job_control` table (from spec 02) is reused as-is.

---

## Job directory structure

```
workers/golang/financial_data/cvm_funds_job/
├── cmd/
│   └── cronjob/
│       └── main.go
├── internal/
│   ├── application/
│   │   └── config.go
│   ├── domain/
│   │   └── models.go
│   ├── processor/
│   │   ├── catalog_ingester.go      # producer + WorkerPool at chunk level
│   │   ├── catalog_chunk_task.go    # WorkItem — upserts one chunk of fund records
│   │   ├── nav_dispatcher.go        # WorkerPool at month level
│   │   └── nav_month_task.go        # WorkItem — downloads, streams, upserts one month
│   ├── infrastructure/
│   │   ├── database/
│   │   │   ├── postgres.go
│   │   │   ├── asset_repository.go
│   │   │   ├── nav_repository.go
│   │   │   └── job_control_repository.go
│   │   └── http/
│   │       └── cvm_client.go
│   └── shared/
│       ├── hash.go
│       └── retry.go
└── go.mod
```

---

## Activities

### Activity 1 — Config

**File**: `internal/application/config.go`

Follow `indicators_job` exactly. Key additions are the two worker counts and chunk sizes.

```go
package application

import (
    "fmt"
    "strings"
    "github.com/caarlos0/env/v10"
)

type Config struct {
    Database    DatabaseConfig
    Job         JobConfig
    CVM         CVMConfig
    HealthCheck HealthCheckConfig
}

type DatabaseConfig struct {
    Host     string `env:"POSTGRES_HOST"     envDefault:"localhost"`
    Port     int    `env:"POSTGRES_PORT"     envDefault:"5432"`
    Database string `env:"POSTGRES_DB"       envDefault:"neotool_db"`
    User     string `env:"POSTGRES_USER"     envDefault:"neotool"`
    Password string `env:"POSTGRES_PASSWORD,required"`
    PoolMin  int    `env:"POSTGRES_POOL_MIN" envDefault:"2"`
    PoolMax  int    `env:"POSTGRES_POOL_MAX" envDefault:"10"`
    SSLMode  string `env:"POSTGRES_SSL_MODE" envDefault:"prefer"`
}

type JobConfig struct {
    // Catalog: parallelism at chunk level — how many chunks upserted concurrently
    CatalogWorkers           int `env:"CVM_CATALOG_WORKERS"            envDefault:"4"`
    CatalogQueueBuffer       int `env:"CVM_CATALOG_QUEUE_BUFFER"       envDefault:"2"`
    CatalogChunkSize         int `env:"CVM_CATALOG_CHUNK_SIZE"         envDefault:"500"`

    // NAV: parallelism at month level — how many month files processed concurrently
    // Keep low (2–3) to bound memory: each active task holds one extracted CSV in a temp file
    NAVWorkers               int `env:"CVM_NAV_WORKERS"                envDefault:"2"`
    NAVQueueBuffer           int `env:"CVM_NAV_QUEUE_BUFFER"           envDefault:"2"`
    // Within each month task, rows are accumulated into chunks before each DB batch
    NAVChunkSize             int `env:"CVM_NAV_CHUNK_SIZE"             envDefault:"500"`

    // Backfill: max years of NAV history to load on first run
    NAVBackfillYears         int `env:"CVM_NAV_BACKFILL_YEARS"         envDefault:"5"`
}

type CVMConfig struct {
    BaseURL        string `env:"CVM_BASE_URL"         envDefault:"https://dados.cvm.gov.br"`
    TimeoutSeconds int    `env:"CVM_TIMEOUT_SECONDS"  envDefault:"120"`
}

func LoadConfig() (*Config, error) {
    cfg := &Config{}
    if err := env.Parse(cfg); err != nil {
        return nil, fmt.Errorf("parsing config: %w", err)
    }
    return cfg, cfg.Validate()
}

func (c *Config) Validate() error {
    var errs []string
    if c.Database.Password == "" {
        errs = append(errs, "POSTGRES_PASSWORD is required")
    }
    if c.Job.CatalogWorkers < 1 {
        errs = append(errs, "CVM_CATALOG_WORKERS must be >= 1")
    }
    if c.Job.NAVWorkers < 1 || c.Job.NAVWorkers > 8 {
        errs = append(errs, "CVM_NAV_WORKERS must be between 1 and 8")
    }
    if c.Job.CatalogChunkSize < 100 || c.Job.CatalogChunkSize > 2000 {
        errs = append(errs, "CVM_CATALOG_CHUNK_SIZE must be between 100 and 2000")
    }
    if c.Job.NAVChunkSize < 100 || c.Job.NAVChunkSize > 2000 {
        errs = append(errs, "CVM_NAV_CHUNK_SIZE must be between 100 and 2000")
    }
    if c.Job.NAVBackfillYears < 1 || c.Job.NAVBackfillYears > 10 {
        errs = append(errs, "CVM_NAV_BACKFILL_YEARS must be between 1 and 10")
    }
    if len(errs) > 0 {
        return fmt.Errorf("config validation failed: %s", strings.Join(errs, "; "))
    }
    return nil
}
```

---

### Activity 2 — Domain models

**File**: `internal/domain/models.go`

```go
package domain

import (
    "time"
    "github.com/google/uuid"
)

type ChangeType string
const (
    ChangeTypeCreated   ChangeType = "CREATED"
    ChangeTypeUpdated   ChangeType = "UPDATED"
    ChangeTypeUnchanged ChangeType = "UNCHANGED"
)

type RunType string
const (
    RunTypeBackfill    RunType = "BACKFILL"
    RunTypeIncremental RunType = "INCREMENTAL"
)

// FundRecord is one parsed row from cad_fi.csv.
type FundRecord struct {
    CNPJ        string // normalized: 14 raw digits
    Name        string
    Classe      string
    TipoFundo   string
    Manager     string
    IsActive    bool
    AssetType   string // resolved: PGBL | VGBL | MUTUAL_FUND
    ContentHash string // SHA-256 of mutable fields
}

// NavUpsertParam is what gets written to asset_daily_price.
type NavUpsertParam struct {
    AssetID        uuid.UUID
    PriceDate      time.Time
    ClosePrice     float64
    TotalNetAssets float64
    Shareholders   *int
    Source         string
    ContentHash    string
}

// InvestmentAsset is the persisted row.
type InvestmentAsset struct {
    ID          uuid.UUID
    FundCNPJ    string
    Name        string
    AssetType   string
    IsActive    bool
    ContentHash string
}

type JobControlRecord struct {
    JobName       string
    LastDataDate  *time.Time
    LastSuccessAt *time.Time
    Status        string
}
```

---

### Activity 3 — Hash helpers

**File**: `internal/shared/hash.go`

Follow `bacen_ifdata_job` and `ratings_job` exactly.

```go
package shared

import (
    "crypto/sha256"
    "encoding/json"
    "fmt"
    "sort"
    "strings"
)

func ComputeHash(data []byte) string {
    h := sha256.Sum256(data)
    return fmt.Sprintf("%x", h)
}

// ComputeFundHash hashes mutable catalog fields.
// If none change between runs, the DB upsert is skipped entirely.
func ComputeFundHash(cnpj, name, classe, tipoFundo, manager string, isActive bool) string {
    fields := map[string]interface{}{
        "cnpj":       normStr(cnpj),
        "name":       normStr(name),
        "classe":     normStr(classe),
        "tipo_fundo": normStr(tipoFundo),
        "manager":    normStr(manager),
        "is_active":  isActive,
    }
    return ComputeHash(mustSortedJSON(fields))
}

// ComputeNavHash hashes the price fields.
// Prevents re-writing a price row when the same monthly file is re-downloaded.
func ComputeNavHash(cnpj, navDate string, navPerQuota float64) string {
    canonical := fmt.Sprintf("%s|%s|%.8f", normStr(cnpj), navDate, navPerQuota)
    return ComputeHash([]byte(canonical))
}

func normStr(s string) string {
    s = strings.TrimSpace(s)
    if strings.EqualFold(s, "null") {
        return ""
    }
    return s
}

func mustSortedJSON(fields map[string]interface{}) []byte {
    keys := make([]string, 0, len(fields))
    for k := range fields {
        keys = append(keys, k)
    }
    sort.Strings(keys)
    ordered := make([]interface{}, 0, len(keys)*2)
    for _, k := range keys {
        ordered = append(ordered, k, fields[k])
    }
    b, _ := json.Marshal(ordered)
    return b
}
```

---

### Activity 4 — CVM HTTP client

**File**: `internal/infrastructure/http/cvm_client.go`

```go
package http

import (
    "context"
    "fmt"
    "io"
    "net/http"
    "os"
    "time"
)

type CVMClient struct {
    baseURL string
    http    *http.Client
}

func NewCVMClient(baseURL string, timeoutSec int) *CVMClient {
    return &CVMClient{
        baseURL: baseURL,
        http:    &http.Client{Timeout: time.Duration(timeoutSec) * time.Second},
    }
}

// StreamFundCatalog returns an io.ReadCloser for the catalog CSV.
// Caller must close the response body.
// The response is NOT buffered — suitable for streaming with encoding/csv.
func (c *CVMClient) StreamFundCatalog(ctx context.Context) (io.ReadCloser, error) {
    return c.stream(ctx, "/dados/FI/CAD/DADOS/cad_fi.csv")
}

// DownloadNavToTempFile downloads a NAV month ZIP to a temp file and returns the path + cleanup func.
//
// Why a temp file?
// archive/zip.NewReader requires an io.ReaderAt (seekable), which an HTTP response body is not.
// Writing to disk avoids loading the entire ZIP into memory, keeping the peak memory footprint
// bounded to one chunk of NavChunkSize rows regardless of file size.
//
// The caller must invoke cleanup() (which calls os.Remove) when done.
func (c *CVMClient) DownloadNavToTempFile(ctx context.Context, yearMonth string) (path string, cleanup func(), err error) {
    url := fmt.Sprintf("/dados/FI/DOC/INF_DIARIO/DADOS/inf_diario_fi_%s.zip", yearMonth)

    body, err := c.stream(ctx, url)
    if err != nil {
        return "", nil, err
    }
    defer body.Close()

    tmp, err := os.CreateTemp("", "cvm-nav-*.zip")
    if err != nil {
        return "", nil, fmt.Errorf("creating temp file: %w", err)
    }

    if _, err = io.Copy(tmp, body); err != nil {
        _ = os.Remove(tmp.Name())
        return "", nil, fmt.Errorf("writing temp file: %w", err)
    }
    if err = tmp.Close(); err != nil {
        _ = os.Remove(tmp.Name())
        return "", nil, fmt.Errorf("closing temp file: %w", err)
    }

    return tmp.Name(), func() { _ = os.Remove(tmp.Name()) }, nil
}

func (c *CVMClient) stream(ctx context.Context, path string) (io.ReadCloser, error) {
    req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.baseURL+path, nil)
    if err != nil {
        return nil, err
    }
    resp, err := c.http.Do(req)
    if err != nil {
        return nil, fmt.Errorf("GET %s: %w", path, err)
    }
    if resp.StatusCode != http.StatusOK {
        _ = resp.Body.Close()
        return nil, fmt.Errorf("GET %s: status %d", path, resp.StatusCode)
    }
    return resp.Body, nil
}
```

---

### Activity 5 — Catalog ingester + chunk task

**Design**: one producer goroutine streams CSV → accumulates chunks → submits `CatalogChunkTask` to `WorkerPool`. Each `CatalogChunkTask` owns its slice and nil's it on completion to release memory.

```
StreamFundCatalog() ──► [csv.Reader] ──► accumulate chunk
                                              │  chunk full?
                                              ▼
                                      pool.Submit(CatalogChunkTask{records: chunk})
                                      chunk = make([]FundRecord, 0, chunkSize)  ← new alloc; old chunk owned by task
                                              │
                          WorkerPool (CVM_CATALOG_WORKERS goroutines)
                              ▼
                          CatalogChunkTask.Process()
                              ├── hash check per record (skip UNCHANGED)
                              ├── pgx.Batch upsert (only CREATED + UPDATED)
                              └── t.records = nil  ← release; GC collects backing array
```

**File**: `internal/processor/catalog_ingester.go`

```go
package processor

import (
    "context"
    "encoding/csv"
    "io"
    "log/slog"
    "time"

    "github.com/salomax/invistus/workers/golang/shared/workerpool"
)

const (
    jobNameCatalog = "cvm-fund-catalog"
    sourceCVM      = "CVM"
)

type CatalogIngester struct {
    client     CVMClientIface
    assetRepo  AssetRepositoryIface
    jobControl JobControlRepositoryIface
    chunkSize  int
    workers    int
    queueBuf   int
    log        *slog.Logger
}

type CatalogStats struct {
    Total     int
    Created   int
    Updated   int
    Unchanged int
    Errors    int
}

func (ci *CatalogIngester) Run(ctx context.Context) (CatalogStats, error) {
    ci.log.Info("catalog ingester started")
    start := time.Now()

    body, err := ci.client.StreamFundCatalog(ctx)
    if err != nil {
        return CatalogStats{}, fmt.Errorf("streaming catalog: %w", err)
    }
    defer body.Close()

    // Pre-load all existing content hashes into memory for O(1) change detection.
    // ~60K entries × ~100 bytes each ≈ 6 MB — acceptable one-time cost.
    existingHashes, err := ci.assetRepo.FindAllFundHashes(ctx)
    if err != nil {
        return CatalogStats{}, fmt.Errorf("loading existing hashes: %w", err)
    }

    // Aggregated stats collected via a channel from worker tasks.
    statsCh := make(chan chunkStats, ci.workers*ci.queueBuf)

    pool := workerpool.NewWorkerPool(ci.workers, ci.queueBuf, ci.log)
    pool.Start(ctx, func(ctx context.Context, item workerpool.WorkItem) error {
        return item.Process(ctx)
    })

    // Producer: stream CSV, accumulate chunks, submit tasks.
    go func() {
        defer pool.Shutdown()

        r := csv.NewReader(body)
        r.Comma = ';'
        r.LazyQuotes = true

        // Skip header
        if _, err := r.Read(); err != nil {
            ci.log.Error("reading CSV header", "error", err)
            return
        }

        chunk := make([]domain.FundRecord, 0, ci.chunkSize)
        for {
            row, err := r.Read()
            if err == io.EOF {
                break
            }
            if err != nil {
                ci.log.Warn("skipping malformed CSV row", "error", err)
                continue
            }

            rec := parseCatalogRow(row)
            chunk = append(chunk, rec)

            if len(chunk) >= ci.chunkSize {
                // Transfer ownership to task — allocate fresh slice for next chunk.
                task := newCatalogChunkTask(chunk, existingHashes, ci.assetRepo, statsCh, ci.log)
                if submitErr := pool.Submit(ctx, task); submitErr != nil {
                    ci.log.Error("submitting chunk task", "error", submitErr)
                }
                chunk = make([]domain.FundRecord, 0, ci.chunkSize) // fresh alloc; old slice owned by task
            }
        }

        // Flush the last (possibly partial) chunk.
        if len(chunk) > 0 {
            task := newCatalogChunkTask(chunk, existingHashes, ci.assetRepo, statsCh, ci.log)
            _ = pool.Submit(ctx, task)
        }
    }()

    pool.Wait()
    close(statsCh)

    // Aggregate stats from all tasks.
    var total CatalogStats
    for s := range statsCh {
        total.Total += s.total
        total.Created += s.created
        total.Updated += s.updated
        total.Unchanged += s.unchanged
        total.Errors += s.errors
    }

    ci.log.Info("catalog ingester done",
        "created", total.Created,
        "updated", total.Updated,
        "unchanged", total.Unchanged,
        "errors", total.Errors,
        "duration_ms", time.Since(start).Milliseconds(),
    )
    return total, nil
}
```

**File**: `internal/processor/catalog_chunk_task.go`

```go
package processor

import (
    "context"
    "log/slog"
)

type chunkStats struct {
    total, created, updated, unchanged, errors int
}

// CatalogChunkTask implements workerpool.WorkItem.
// It owns its records slice and nil's it after processing to release memory.
type CatalogChunkTask struct {
    records        []domain.FundRecord
    existingHashes map[string]string   // cnpj → current content_hash (read-only shared map)
    assetRepo      AssetRepositoryIface
    statsCh        chan<- chunkStats
    log            *slog.Logger
}

func newCatalogChunkTask(
    records []domain.FundRecord,
    existingHashes map[string]string,
    assetRepo AssetRepositoryIface,
    statsCh chan<- chunkStats,
    log *slog.Logger,
) *CatalogChunkTask {
    return &CatalogChunkTask{
        records:        records,
        existingHashes: existingHashes,
        assetRepo:      assetRepo,
        statsCh:        statsCh,
        log:            log,
    }
}

func (t *CatalogChunkTask) Process(ctx context.Context) error {
    // Explicit release on exit — backing array is GC-eligible after this returns.
    defer func() { t.records = nil }()

    stats := chunkStats{total: len(t.records)}

    // Separate into CHANGED (needs upsert) vs UNCHANGED (skip).
    var toUpsert []domain.FundRecord
    for _, rec := range t.records {
        existing, found := t.existingHashes[rec.CNPJ]
        if found && existing == rec.ContentHash {
            stats.unchanged++
            continue
        }
        toUpsert = append(toUpsert, rec)
        if found {
            stats.updated++
        } else {
            stats.created++
        }
    }

    if len(toUpsert) > 0 {
        if err := t.assetRepo.UpsertFundBatch(ctx, toUpsert); err != nil {
            t.log.Error("upserting fund chunk", "error", err, "count", len(toUpsert))
            stats.errors += len(toUpsert)
            t.statsCh <- stats
            return err
        }
    }

    t.statsCh <- stats
    return nil
}
```

---

### Activity 6 — NAV dispatcher + month task

**Design**: one task per month. Each `NAVMonthTask` downloads its ZIP to a **temp file on disk** (so `archive/zip` can seek), then streams CSV rows through a fixed-size in-memory chunk buffer. After each chunk is committed to DB, the buffer is reset in place (`chunk = chunk[:0]`) — no new allocations per batch.

```
NAVDispatcher
    buildMonthList()  →  [Jan, Feb, Mar, ...]
    pool.Submit(NAVMonthTask{yearMonth: Jan})
    pool.Submit(NAVMonthTask{yearMonth: Feb})
    ...

WorkerPool (CVM_NAV_WORKERS goroutines — default 2)
    NAVMonthTask.Process()
    ├── DownloadNavToTempFile()   ← writes ZIP to disk; body not held in memory
    ├── zip.OpenReader(tmpPath)   ← seeks in file; no full-file buffer
    ├── csv.NewReader(zipEntry)   ← streaming CSV reader
    ├── loop rows:
    │     parse row → NavUpsertParam
    │     append to chunk  (cap = NAVChunkSize)
    │     if len(chunk) == NAVChunkSize:
    │         UpsertNavBatch(chunk)   ← pgx.Batch commit
    │         chunk = chunk[:0]       ← reset len; reuse backing array (no alloc)
    │
    ├── flush remaining rows
    ├── cleanup()                ← os.Remove(tmpPath)
    └── jobControl.MarkSuccess()

Memory per active task:
    temp file (disk, not RAM) + one chunk buffer = NAVChunkSize × ~80 bytes ≈ 40 KB
    Peak for 2 workers: ~80 KB heap + 2 temp files on disk
```

**File**: `internal/processor/nav_dispatcher.go`

```go
package processor

import (
    "context"
    "log/slog"
    "time"

    "github.com/salomax/invistus/workers/golang/shared/workerpool"
)

const jobNameNAV = "cvm-fund-nav"

type NAVDispatcher struct {
    client        CVMClientIface
    assetRepo     AssetRepositoryIface
    navRepo       NavRepositoryIface
    jobControl    JobControlRepositoryIface
    backfillYears int
    workers       int
    queueBuf      int
    chunkSize     int
    log           *slog.Logger
}

type NAVStats struct {
    MonthsProcessed int
    Inserted        int
    Unchanged       int
    MonthsFailed    int
}

func (d *NAVDispatcher) Run(ctx context.Context) (NAVStats, error) {
    lastDate, runType := d.resolveRunType(ctx)
    months := d.buildMonthList(lastDate)

    d.log.Info("NAV dispatcher started",
        "run_type", runType,
        "months", len(months),
        "workers", d.workers,
    )

    // Load CNPJ → asset_id map once. Shared read-only across all month tasks.
    // ~60K entries × ~36 bytes UUID ≈ ~2 MB — acceptable.
    assetMap, err := d.assetRepo.FindAllActiveFundIDs(ctx)
    if err != nil {
        return NAVStats{}, fmt.Errorf("loading asset map: %w", err)
    }
    d.log.Info("asset map loaded", "count", len(assetMap))

    statsCh := make(chan navMonthStats, len(months))

    pool := workerpool.NewWorkerPool(d.workers, d.queueBuf, d.log)
    pool.Start(ctx, func(ctx context.Context, item workerpool.WorkItem) error {
        return item.Process(ctx)
    })

    for _, ym := range months {
        task := &NAVMonthTask{
            yearMonth:  ym,
            client:     d.client,
            assetMap:   assetMap,
            navRepo:    d.navRepo,
            jobControl: d.jobControl,
            chunkSize:  d.chunkSize,
            statsCh:    statsCh,
            log:        d.log.With("year_month", ym.String()),
        }
        if err := pool.Submit(ctx, task); err != nil {
            return NAVStats{}, err
        }
    }

    pool.Shutdown()
    pool.Wait()
    close(statsCh)

    var total NAVStats
    for s := range statsCh {
        total.MonthsProcessed++
        total.Inserted += s.inserted
        total.Unchanged += s.unchanged
        if s.failed {
            total.MonthsFailed++
        }
    }

    d.log.Info("NAV dispatcher done",
        "months_ok", total.MonthsProcessed-total.MonthsFailed,
        "months_failed", total.MonthsFailed,
        "inserted", total.Inserted,
        "unchanged", total.Unchanged,
    )
    return total, nil
}

func (d *NAVDispatcher) resolveRunType(ctx context.Context) (*time.Time, domain.RunType) {
    last, err := d.jobControl.FindLastDataDate(ctx, jobNameNAV)
    if err != nil || last == nil {
        return nil, domain.RunTypeBackfill
    }
    return last, domain.RunTypeIncremental
}

func (d *NAVDispatcher) buildMonthList(lastDate *time.Time) []YearMonth {
    now := time.Now()
    current := NewYearMonth(now.Year(), now.Month())

    var start YearMonth
    if lastDate == nil {
        start = current.AddMonths(-d.backfillYears * 12)
    } else {
        start = NewYearMonth(lastDate.Year(), lastDate.Month()).AddMonths(1)
    }

    var months []YearMonth
    for ym := start; !ym.After(current); ym = ym.AddMonths(1) {
        months = append(months, ym)
    }
    return months
}
```

**File**: `internal/processor/nav_month_task.go`

```go
package processor

import (
    "archive/zip"
    "context"
    "encoding/csv"
    "io"
    "log/slog"
    "strings"
    "time"

    "github.com/google/uuid"
)

type navMonthStats struct {
    yearMonth string
    inserted  int
    unchanged int
    failed    bool
}

// NAVMonthTask implements workerpool.WorkItem.
// Each task is responsible for one month file: download → stream → batch upsert → cleanup.
type NAVMonthTask struct {
    yearMonth  YearMonth
    client     CVMClientIface
    assetMap   map[string]uuid.UUID  // read-only; shared across all tasks (no lock needed)
    navRepo    NavRepositoryIface
    jobControl JobControlRepositoryIface
    chunkSize  int
    statsCh    chan<- navMonthStats
    log        *slog.Logger
}

func (t *NAVMonthTask) Process(ctx context.Context) error {
    start := time.Now()
    stats := navMonthStats{yearMonth: t.yearMonth.String()}

    defer func() {
        t.statsCh <- stats
        t.log.Info("month task done",
            "inserted", stats.inserted,
            "unchanged", stats.unchanged,
            "failed", stats.failed,
            "duration_ms", time.Since(start).Milliseconds(),
        )
    }()

    // --- Step 1: Download ZIP to temp file ---
    // Avoids loading the full ZIP into memory; archive/zip needs seekable input.
    tmpPath, cleanup, err := t.client.DownloadNavToTempFile(ctx, t.yearMonth.String())
    if err != nil {
        t.log.Error("downloading NAV file", "error", err)
        stats.failed = true
        return err
    }
    defer cleanup() // os.Remove(tmpPath) — runs even on error

    // --- Step 2: Open ZIP and locate the CSV entry ---
    zipReader, err := zip.OpenReader(tmpPath)
    if err != nil {
        t.log.Error("opening ZIP", "error", err)
        stats.failed = true
        return err
    }
    defer zipReader.Close()

    var csvEntry *zip.File
    for _, f := range zipReader.File {
        if strings.HasSuffix(strings.ToLower(f.Name), ".csv") {
            csvEntry = f
            break
        }
    }
    if csvEntry == nil {
        err = fmt.Errorf("no CSV found in ZIP for %s", t.yearMonth)
        stats.failed = true
        return err
    }

    rc, err := csvEntry.Open()
    if err != nil {
        stats.failed = true
        return err
    }
    defer rc.Close()

    // --- Step 3: Stream CSV rows → batch upsert ---
    // chunk reuses its backing array across iterations (chunk[:0]) — zero extra allocs per batch.
    chunk := make([]domain.NavUpsertParam, 0, t.chunkSize)

    r := csv.NewReader(rc)
    r.Comma = ';'
    r.LazyQuotes = true

    if _, err := r.Read(); err != nil { // skip header
        stats.failed = true
        return err
    }

    for {
        row, err := r.Read()
        if err == io.EOF {
            break
        }
        if err != nil {
            t.log.Warn("skipping malformed NAV row", "error", err)
            continue
        }

        cnpj := normalizeCNPJ(col(row, navColIdx, "CNPJ_FUNDO"))
        assetID, ok := t.assetMap[cnpj]
        if !ok {
            continue // fund not in catalog — skip silently
        }

        navDate, navPerQuota := parseNavRow(row, navColIdx)
        hash := shared.ComputeNavHash(cnpj, navDate.Format("2006-01-02"), navPerQuota)

        chunk = append(chunk, domain.NavUpsertParam{
            AssetID:     assetID,
            PriceDate:   navDate,
            ClosePrice:  navPerQuota,
            Source:      sourceCVM,
            ContentHash: hash,
        })

        if len(chunk) >= t.chunkSize {
            inserted, unchanged, upsertErr := t.navRepo.UpsertNavBatch(ctx, chunk)
            if upsertErr != nil {
                t.log.Error("upsert batch failed", "error", upsertErr)
                stats.failed = true
                return upsertErr
            }
            stats.inserted += inserted
            stats.unchanged += unchanged

            // Reset in place — backing array reused, no allocation.
            chunk = chunk[:0]
        }
    }

    // Flush remaining rows
    if len(chunk) > 0 {
        inserted, unchanged, upsertErr := t.navRepo.UpsertNavBatch(ctx, chunk)
        if upsertErr != nil {
            stats.failed = true
            return upsertErr
        }
        stats.inserted += inserted
        stats.unchanged += unchanged
        chunk = nil // release backing array
    }

    // --- Step 4: Mark success ---
    lastDay := t.yearMonth.LastDay()
    if err := t.jobControl.MarkSuccess(ctx, jobNameNAV, lastDay); err != nil {
        t.log.Warn("failed to mark success in job control", "error", err)
    }

    return nil
}
```

---

### Activity 7 — NAV repository

**File**: `internal/infrastructure/database/nav_repository.go`

```go
package database

const upsertNavSQL = `
    INSERT INTO financial_data.asset_daily_price
        (id, asset_id, price_date, close_price, source, content_hash, created_at)
    VALUES
        (gen_random_uuid(), $1, $2, $3, $4, $5, NOW())
    ON CONFLICT (asset_id, price_date)
    DO UPDATE SET
        close_price  = EXCLUDED.close_price,
        content_hash = EXCLUDED.content_hash
    WHERE financial_data.asset_daily_price.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// UpsertNavBatch submits a pgx.Batch for the given params.
// Returns counts of rows actually written vs skipped (hash match).
func (r *NavRepository) UpsertNavBatch(
    ctx context.Context,
    params []domain.NavUpsertParam,
) (inserted int, unchanged int, err error) {
    batch := &pgx.Batch{}
    for _, p := range params {
        batch.Queue(upsertNavSQL, p.AssetID, p.PriceDate, p.ClosePrice, p.Source, p.ContentHash)
    }

    br := r.pool.SendBatch(ctx, batch)
    defer br.Close()

    for range params {
        tag, execErr := br.Exec()
        if execErr != nil {
            return inserted, unchanged, execErr
        }
        if tag.RowsAffected() > 0 {
            inserted++
        } else {
            unchanged++
        }
    }
    return inserted, unchanged, nil
}
```

---

### Activity 8 — Asset repository additions

**File**: `internal/infrastructure/database/asset_repository.go`

```go
// FindAllFundHashes returns cnpj → content_hash for all CVM assets.
// Used by CatalogIngester to detect changes before submitting chunk tasks.
const findAllFundHashesSQL = `
    SELECT fund_cnpj, content_hash
    FROM financial_data.investment_asset
    WHERE source = 'CVM'
      AND fund_cnpj IS NOT NULL
`

// UpsertFundBatch inserts/updates a batch of fund assets.
// Only executes for records where content_hash differs (guarded in the task layer;
// the SQL ON CONFLICT clause is the DB-level safety net).
const upsertFundSQL = `
    INSERT INTO financial_data.investment_asset
        (id, name, asset_type, fund_cnpj, is_active, source, content_hash, currency_code,
         lot_size, price_factor, created_at, updated_at)
    VALUES
        (gen_random_uuid(), $1, $2, $3, $4, $5, $6, 'BRL', 1, 1, NOW(), NOW())
    ON CONFLICT (fund_cnpj)
    DO UPDATE SET
        name         = EXCLUDED.name,
        asset_type   = EXCLUDED.asset_type,
        is_active    = EXCLUDED.is_active,
        content_hash = EXCLUDED.content_hash,
        updated_at   = NOW()
    WHERE financial_data.investment_asset.content_hash IS DISTINCT FROM EXCLUDED.content_hash
`

// FindAllActiveFundIDs returns cnpj → uuid for active CVM funds.
// Loaded once by NAVDispatcher; shared read-only across all NAVMonthTasks.
const findAllActiveFundIDsSQL = `
    SELECT fund_cnpj, id
    FROM financial_data.investment_asset
    WHERE source = 'CVM'
      AND fund_cnpj IS NOT NULL
`
```

---

### Activity 9 — Main entrypoint

**File**: `cmd/cronjob/main.go`

```go
package main

import (
    "context"
    "log/slog"
    "os"
    "os/signal"
    "syscall"
    "time"
)

func main() {
    log := slog.New(slog.NewJSONHandler(os.Stdout, nil))

    cfg, err := application.LoadConfig()
    if err != nil {
        log.Error("failed to load config", "error", err)
        os.Exit(1)
    }

    pool, err := database.NewPool(cfg.Database)
    if err != nil {
        log.Error("failed to connect to database", "error", err)
        os.Exit(1)
    }
    defer pool.Close()

    ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
    defer cancel()

    client     := http.NewCVMClient(cfg.CVM.BaseURL, cfg.CVM.TimeoutSeconds)
    assetRepo  := database.NewAssetRepository(pool)
    navRepo    := database.NewNavRepository(pool)
    jobControl := database.NewJobControlRepository(pool)

    // Step 1: Catalog
    catalog := processor.NewCatalogIngester(client, assetRepo, jobControl,
        cfg.Job.CatalogChunkSize, cfg.Job.CatalogWorkers, cfg.Job.CatalogQueueBuffer, log)

    log.Info("starting catalog ingestion")
    t0 := time.Now()
    catalogStats, err := catalog.Run(ctx)
    if err != nil {
        log.Error("catalog ingestion failed", "error", err, "duration_ms", time.Since(t0).Milliseconds())
        // Continue to NAV — a partial catalog is still useful
    } else {
        log.Info("catalog ingestion complete",
            "created", catalogStats.Created,
            "updated", catalogStats.Updated,
            "unchanged", catalogStats.Unchanged,
            "errors", catalogStats.Errors,
            "duration_ms", time.Since(t0).Milliseconds(),
        )
    }

    // Step 2: NAV
    nav := processor.NewNAVDispatcher(client, assetRepo, navRepo, jobControl,
        cfg.Job.NAVBackfillYears, cfg.Job.NAVWorkers, cfg.Job.NAVQueueBuffer, cfg.Job.NAVChunkSize, log)

    log.Info("starting NAV ingestion")
    t0 = time.Now()
    navStats, err := nav.Run(ctx)
    if err != nil {
        log.Error("NAV ingestion failed", "error", err, "duration_ms", time.Since(t0).Milliseconds())
        os.Exit(1)
    }
    log.Info("NAV ingestion complete",
        "months_ok", navStats.MonthsProcessed-navStats.MonthsFailed,
        "months_failed", navStats.MonthsFailed,
        "inserted", navStats.Inserted,
        "unchanged", navStats.Unchanged,
        "duration_ms", time.Since(t0).Milliseconds(),
    )
}
```

---

## Memory model summary

| Stage | Peak heap memory | Notes |
|-------|-----------------|-------|
| Catalog hash preload | ~6 MB | 60K × (CNPJ string + hash string) loaded once |
| Catalog chunk (per worker) | ~100 KB | `chunkSize × sizeof(FundRecord)` |
| Catalog peak | ~6 MB + workers × 100 KB | e.g. 4 workers = ~6.4 MB total |
| NAV asset map | ~2 MB | 60K × UUID loaded once, shared across tasks |
| NAV chunk (per worker) | ~40 KB | `chunkSize × sizeof(NavUpsertParam)`, reused in place |
| NAV ZIP (per worker) | 0 bytes heap | Written to disk as temp file |
| NAV peak | ~2 MB + workers × 40 KB | e.g. 2 workers = ~2.1 MB total heap |

**Memory release patterns used**:

| Location | Technique | Why |
|----------|-----------|-----|
| `CatalogChunkTask.Process()` | `defer func() { t.records = nil }()` | Task owns the slice; setting to nil releases the backing array for GC |
| After `pool.Submit(chunk)` | `chunk = make([]FundRecord, 0, chunkSize)` | New allocation — old backing array exclusively owned by the task |
| NAV row batch in `NAVMonthTask` | `chunk = chunk[:0]` | Reuses backing array (no allocation per batch); safe because task owns the slice |
| After last NAV flush | `chunk = nil` | Releases backing array |
| ZIP temp file | `defer cleanup()` (= `os.Remove`) | Disk space released when task exits |

---

## Idempotency summary

| Layer | Guard |
|-------|-------|
| Fund catalog (task layer) | `existingHashes[cnpj] == rec.ContentHash` → skip task submission |
| Fund catalog (DB layer) | `ON CONFLICT (fund_cnpj) DO UPDATE WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash` |
| NAV price (DB layer) | `ON CONFLICT (asset_id, price_date) DO UPDATE WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash` |
| Month re-download | Same `VL_QUOTA` value produces same hash → DB update skipped |
| Full re-run | Both layers combined make any re-run zero-cost for unchanged data |

---

## Backfill strategy

1. **First deploy** (`last_data_date = NULL`): `buildMonthList` fetches from `(today - CVM_NAV_BACKFILL_YEARS)` to today. Default = 5 years = 60 months.
2. **Manual full backfill**: set `UPDATE import_job_control SET last_data_date = NULL WHERE job_name = 'cvm-fund-nav'` and re-run.
3. **Subsequent runs**: incremental — only months after `last_data_date` processed.
4. **Catalog**: always full download; hash guards make it O(unchanged records) = near-zero DB writes on repeat runs.

---

## File change summary

| File | Change |
|------|--------|
| `workers/golang/financial_data/cvm_funds_job/cmd/cronjob/main.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/application/config.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/domain/models.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/shared/hash.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/shared/retry.go` | **Create** (copy from `bacen_ifdata_job`) |
| `workers/golang/financial_data/cvm_funds_job/internal/processor/catalog_ingester.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/processor/catalog_chunk_task.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/processor/nav_dispatcher.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/processor/nav_month_task.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/infrastructure/http/cvm_client.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/infrastructure/database/asset_repository.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/infrastructure/database/nav_repository.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/internal/infrastructure/database/job_control_repository.go` | **Create** |
| `workers/golang/financial_data/cvm_funds_job/go.mod` | **Create** |
| `db/migration/V1_16__investment_asset_content_hash.sql` | **Create** |

---

## Acceptance criteria

- [ ] Job lives at `workers/golang/financial_data/cvm_funds_job/` — same layout as `bacen_ifdata_job`
- [ ] `CVM_CATALOG_WORKERS` and `CVM_NAV_WORKERS` env vars control concurrency
- [ ] `CVM_CATALOG_CHUNK_SIZE` and `CVM_NAV_CHUNK_SIZE` control batch size
- [ ] Catalog CSV streamed row-by-row — never fully loaded into memory
- [ ] NAV ZIP written to temp file (not memory); temp file deleted after task completes
- [ ] NAV chunk buffer reused in place (`chunk[:0]`) inside month task — zero allocations per batch
- [ ] `CatalogChunkTask.records` set to `nil` on exit — backing array released to GC
- [ ] `ComputeFundHash` change detection: UNCHANGED records never reach the DB
- [ ] `ComputeNavHash` change detection: same quota same day → DB update skipped
- [ ] Both upserts guarded by `WHERE content_hash IS DISTINCT FROM EXCLUDED.content_hash`
- [ ] First run loads up to `CVM_NAV_BACKFILL_YEARS` (default 5) of history
- [ ] Subsequent runs process only months after `last_data_date`
- [ ] Each month's `last_data_date` updated after success; failed months do not advance it
- [ ] Graceful shutdown on SIGINT/SIGTERM via `signal.NotifyContext`
- [ ] Structured JSON logging with `duration_ms` for catalog and each month task

## Out of scope

- Fund portfolio composition (carteira de fundos)
- FIDCs, FIPs
- Fee structures (taxa de administração)
- ANBIMA sub-classification beyond `CLASSE`
- Real-time NAV
