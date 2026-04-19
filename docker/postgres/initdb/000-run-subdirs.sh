#!/bin/sh
set -eu

ROOT="/docker-entrypoint-initdb.d"
export NIRO_INITDB_ROOT="$ROOT"

run_sql() {
  file="$1"
  echo ">>> running sql: ${file}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
}

run_shell() {
  file="$1"
  echo ">>> running shell: ${file}"
  sh "$file"
}

run_dir() {
  dir="$1"
  [ -d "$dir" ] || return 0

  for file in "$dir"/*; do
    [ -e "$file" ] || continue
    case "$file" in
      *.sql)
        run_sql "$file"
        ;;
      *.sh)
        run_shell "$file"
        ;;
      *)
        echo ">>> skipping unsupported init file: ${file}"
        ;;
    esac
  done
}

run_dir "$ROOT/00-schema"
run_dir "$ROOT/10-seed"
run_dir "$ROOT/20-bootstrap"

echo ">>> niro initdb completed"
