# syntax=docker/dockerfile:1.7
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --timeout 300 --retries 5 -r requirements.txt

COPY app.py ./
COPY service.py ./
COPY parser.py ./
COPY ocr_client.py ./
COPY main.py ./

EXPOSE 5000

CMD ["python", "app.py"]
