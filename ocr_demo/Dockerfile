FROM python:3.11-slim

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN pip install -r requirements.txt \
    && pip install opencv-python-headless

COPY app.py ./
COPY service.py ./
COPY parser.py ./
COPY ocr_client.py ./
COPY main.py ./

EXPOSE 5000

CMD ["python", "app.py"]
