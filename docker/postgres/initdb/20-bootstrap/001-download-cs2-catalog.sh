#!/bin/sh
set -eu

ROOT="${NIRO_INITDB_ROOT:-/docker-entrypoint-initdb.d}"
MANIFEST="$ROOT/seed-data/cs2-goods-source-manifest.tsv"
SOURCE_DIR="$ROOT/seed-data"
WORKDIR="/tmp/niro-cs2-seed"

if [ ! -f "$MANIFEST" ]; then
  echo "missing manifest: $MANIFEST" >&2
  exit 1
fi

if [ ! -d "$SOURCE_DIR" ]; then
  echo "missing seed data directory: $SOURCE_DIR" >&2
  exit 1
fi

rm -rf "$WORKDIR"
mkdir -p "$WORKDIR"

prepare_file() {
  dataset="$1"
  url="$2"
  source_name="${url##*/}"
  source_name="${source_name%%\?*}"
  source_path="$SOURCE_DIR/$source_name"
  dest_path="$WORKDIR/${dataset}.json"

  if [ ! -f "$source_path" ]; then
    echo "missing local seed file: $source_path" >&2
    echo "expected file name: $source_name" >&2
    echo "upstream reference: $url" >&2
    exit 1
  fi

  if [ ! -s "$source_path" ]; then
    echo "seed file is empty: $source_path" >&2
    exit 1
  fi

  cp "$source_path" "$dest_path"
}

while IFS="$(printf '\t')" read -r dataset url; do
  case "$dataset" in
    ''|'#'*)
      continue
      ;;
  esac

  echo ">>> preparing ${dataset}.json"
  prepare_file "$dataset" "$url"
done < "$MANIFEST"

echo ">>> cs2 catalog local seed prepared"
