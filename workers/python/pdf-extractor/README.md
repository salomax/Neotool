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
- `KAFKA_BROKER` (default: `localhost:9094`)
- `KAFKA_GROUP_ID` (default: `pdf-extractor`)
- `KAFKA_TOPIC_IN` (default: `pdf-extractor.submitted`)
- `KAFKA_TOPIC_OUT` (default: `pdf-extractor.extracted`)
- `KAFKA_TOPIC_DLQ` (default: `pdf-extractor.dlq`)
- `KAFKA_AUTO_OFFSET_RESET` (default: `earliest`)
- `DOWNLOAD_TIMEOUT_SECONDS` (default: `30`)
- `DOWNLOAD_MAX_RETRIES` (default: `3`)
- `MAX_OUTPUT_CHARS` (default: `12000`)

## Local Run
```bash
cd workers/python/pdf-extractor
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python consumer.py
```

## Tests
```bash
cd workers/python/pdf-extractor
pytest -q
```
