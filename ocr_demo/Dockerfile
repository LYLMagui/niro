FROM python:3.11-slim

ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG NO_PROXY

WORKDIR /app

RUN export HTTP_PROXY=$HTTP_PROXY HTTPS_PROXY=$HTTPS_PROXY NO_PROXY=$NO_PROXY \
    http_proxy=$HTTP_PROXY https_proxy=$HTTPS_PROXY no_proxy=$NO_PROXY \
    && apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt ./
RUN export HTTP_PROXY=$HTTP_PROXY HTTPS_PROXY=$HTTPS_PROXY NO_PROXY=$NO_PROXY \
    http_proxy=$HTTP_PROXY https_proxy=$HTTPS_PROXY no_proxy=$NO_PROXY \
    && pip install -r requirements.txt \
    && pip install opencv-python-headless

COPY app.py ./
COPY service.py ./
COPY parser.py ./
COPY ocr_client.py ./
COPY main.py ./

EXPOSE 5000

CMD ["python", "app.py"]
