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

  if command -v wget >/dev/null 2>&1; then
    wget -q -O "$dest" "$url"
    return 0
  fi

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$dest"
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
