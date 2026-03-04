# Upload e Processamento Assíncrono — OFX e CSV

Resumo rápido:
- Os dois fluxos começam em `importTransactionsFile` e criam um `ImportJob` em `PENDING`.
- O arquivo é salvo no namespace `transaction-imports` do assets service.
- O processamento é assíncrono via Kafka (`ImportFileRequestedEvent`).
- A diferença principal entre OFX e CSV está no parser (`OFXParser` vs `CsvNubankParser`) e no `source` (`OFX` vs `CSV_NUBANK`).

## OFX

```mermaid
sequenceDiagram
    autonumber
    actor C as Client (Web/Bruno)
    participant R as Apollo Router
    participant B as Banking GraphQL
    participant J as ImportJobService
    participant A as Assets Service
    participant S as S3/MinIO
    participant K as Kafka
    participant X as ImportFileConsumer
    participant P as OFXParser
    participant I as ImportProcessor
    participant D as Postgres

    C->>R: mutation importTransactionsFile(fileBase64, fileName=.ofx, source=OFX)
    R->>B: /importTransactionsFile
    B->>J: initiateFileImport(...)
    J->>D: valida conta + hash + cria ImportJob(PENDING)
    J->>A: createAssetUpload(namespace=transaction-imports)
    A->>S: gera URL presignada (PUT)
    A-->>J: assetId + uploadUrl
    J->>S: PUT bytes OFX
    J->>A: confirmAssetUpload(assetId)
    J->>K: publish ImportFileRequestedEvent(jobId, assetId, OFX)
    J-->>B: ImportJob(PENDING)
    B-->>R: response com jobId/status
    R-->>C: response

    K-->>X: ImportFileRequestedEvent
    X->>D: job => PROCESSING
    X->>A: getAsset(assetId) (downloadUrl)
    X->>S: GET bytes
    X->>P: parse(bytes)
    P-->>X: ParsedTransaction[]
    X->>I: process(job, rows)
    I->>D: dedup L1/L1b/L2 + inserts
    I-->>X: imported/skipped/duplicates/errors

    alt possui duplicatas cross-source
        X->>D: status => REVIEW_REQUIRED + reviewPayload
    else sem duplicatas
        X->>D: status => COMPLETED
    end

    X->>K: publish ImportJobCompletedEvent
    X->>A: deleteAsset(assetId) (best-effort)
```

## CSV (Nubank)

```mermaid
sequenceDiagram
    autonumber
    actor C as Client (Web/Bruno)
    participant R as Apollo Router
    participant B as Banking GraphQL
    participant J as ImportJobService
    participant A as Assets Service
    participant S as S3/MinIO
    participant K as Kafka
    participant X as ImportFileConsumer
    participant P as CsvNubankParser
    participant I as ImportProcessor
    participant D as Postgres

    C->>R: mutation importTransactionsFile(fileBase64, fileName=.csv, source=CSV_NUBANK)
    R->>B: /importTransactionsFile
    B->>J: initiateFileImport(...)
    J->>D: valida conta + hash + cria ImportJob(PENDING)
    J->>A: createAssetUpload(namespace=transaction-imports)
    A->>S: gera URL presignada (PUT)
    A-->>J: assetId + uploadUrl
    J->>S: PUT bytes CSV
    J->>A: confirmAssetUpload(assetId)
    J->>K: publish ImportFileRequestedEvent(jobId, assetId, CSV_NUBANK)
    J-->>B: ImportJob(PENDING)
    B-->>R: response com jobId/status
    R-->>C: response

    K-->>X: ImportFileRequestedEvent
    X->>D: job => PROCESSING
    X->>A: getAsset(assetId) (downloadUrl)
    X->>S: GET bytes
    X->>P: parse(bytes)
    P-->>X: ParsedTransaction[]
    X->>I: process(job, rows)
    I->>D: dedup L1/L1b/L2 + inserts
    I-->>X: imported/skipped/duplicates/errors

    alt possui duplicatas cross-source
        X->>D: status => REVIEW_REQUIRED + reviewPayload
    else sem duplicatas
        X->>D: status => COMPLETED
    end

    X->>K: publish ImportJobCompletedEvent
    X->>A: deleteAsset(assetId) (best-effort)
```
