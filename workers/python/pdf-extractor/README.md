# PDF Extractor Worker

Kafka worker for PDF extraction in the async import pipeline.

## Features
- Consumes `pdf-extractor.submitted`
- Downloads PDF via signed `download_url`
- Extracts text/tables with `pdfplumber`
- Cleans output and caps payload size
- Publishes success to `pdf-extractor.extracted`
- Publishes failures to `pdf-extractor.dlq`

## Environment Variables
- `KAFKA_BROKER` (default: `127.0.0.1:9094`)
- `KAFKA_GROUP_ID` (default: `pdf-extractor`)
- `KAFKA_TOPIC_IN` (default: `pdf-extractor.submitted`)
- `KAFKA_TOPIC_OUT` (default: `pdf-extractor.extracted`)
- `KAFKA_TOPIC_DLQ` (default: `pdf-extractor.dlq`)
- `KAFKA_AUTO_OFFSET_RESET` (default: `earliest`)
- `DOWNLOAD_TIMEOUT_SECONDS` (default: `30`)
- `DOWNLOAD_MAX_RETRIES` (default: `3`)
- `MAX_OUTPUT_CHARS` (default: `12000`)
- `HEALTH_PORT` (default: `8080`)
- `SECURITY_SERVICE_URL` (default: `http://localhost:8080`)
- `SECURITY_SERVICE_ID` (optional local credential)
- `SECURITY_SERVICE_SECRET` (optional local credential)

## Local Run
```bash
cd workers/python/pdf-extractor
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
set -a
source .env.local
set +a
python consumer.py
```

If `8080` is already in use, set a different health port:
```bash
export HEALTH_PORT=18080
python consumer.py
```

## Troubleshooting

`%3|...|FAIL|... localhost:9094 ... Connection refused`
- Kafka is not reachable on `KAFKA_BROKER`.
- Ensure broker is up and exposed on that port.
- On macOS, `localhost` may resolve to IPv6 (`::1`) first. Prefer `KAFKA_BROKER=127.0.0.1:9094`.

`HTTP/1.1 403 Forbidden` when downloading from signed URL
- Usually means expired presigned URL (common when replaying old Kafka messages).
- For local development prefer:
  - `KAFKA_AUTO_OFFSET_RESET=latest`
  - new `KAFKA_GROUP_ID` when needed (to avoid replaying stale events).

## Docker
```bash
cd workers/python/pdf-extractor
docker build -t pdf-extractor:local .
docker run --rm -e HEALTH_PORT=8080 -p 8080:8080 pdf-extractor:local
curl -i http://localhost:8080/health
```

Health responses:
- `503 starting` during startup
- `200 ok` when the consumer loop is polling
- `503 fatal` after an unrecoverable worker error

## Tests
```bash
cd workers/python/pdf-extractor
PYTHONPATH=. pytest -q
```
