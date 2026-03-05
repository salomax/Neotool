FROM python:3.12-slim

WORKDIR /app

COPY workers/python/pdf-extractor/requirements.txt ./requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

COPY workers/python/pdf-extractor/ ./

CMD ["python", "consumer.py"]
