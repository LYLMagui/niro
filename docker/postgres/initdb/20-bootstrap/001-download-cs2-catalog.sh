#!/bin/sh
set -eu

ROOT="${NIRO_INITDB_ROOT:-/docker-entrypoint-initdb.d}"
MANIFEST="$ROOT/seed-data/cs2-goods-source-manifest.tsv"
WORKDIR="/tmp/niro-cs2-seed"

if [ ! -f "$MANIFEST" ]; then
  echo "missing manifest: $MANIFEST" >&2
  exit 1
fi

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"

download_file() {
  url="$1"
  dest="$2"
  connect_timeout="${NIRO_INITDB_CONNECT_TIMEOUT:-10}"
  download_timeout="${NIRO_INITDB_DOWNLOAD_TIMEOUT:-300}"
  download_retries="${NIRO_INITDB_DOWNLOAD_RETRIES:-3}"

  if command -v curl >/dev/null 2>&1; then
    curl -fL \
      --http1.1 \
      --connect-timeout "$connect_timeout" \
      --max-time "$download_timeout" \
      --retry "$download_retries" \
      --retry-delay 2 \
      --retry-all-errors \
      -o "$dest" \
      "$url"
    return 0
  fi

  if command -v wget >/dev/null 2>&1; then
    wget -q \
      -T "$download_timeout" \
      --tries="$download_retries" \
      -O "$dest" \
      "$url"
    return 0
  fi

  echo "wget or curl is required to bootstrap cs2 goods data" >&2
  exit 1
}

while IFS="$(printf '\t')" read -r dataset url; do
  case "$dataset" in
    ''|'#'*)
      continue
      ;;
  esac

  dest="$WORKDIR/${dataset}.json"
  echo ">>> downloading ${dataset}"
  download_file "$url" "$dest"

  if [ ! -s "$dest" ]; then
    echo "downloaded file is empty: $dest" >&2
    exit 1
  fi
done < "$MANIFEST"

echo ">>> cs2 catalog download completed"
