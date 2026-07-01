#!/usr/bin/env bash
# Restore de backup PostgreSQL (ConsumoEsperto) — uso local/staging apenas.
# Exemplo:
#   ./scripts/restore-postgres-backup.sh backups/database/backup_automatico_20260506_020000.sql.gz consumo_db
set -euo pipefail

DUMP_FILE="${1:-}"
TARGET_DB="${2:-consumo_db}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5439}"
PGUSER="${PGUSER:-consumo}"

if [[ -z "$DUMP_FILE" || ! -f "$DUMP_FILE" ]]; then
  echo "Uso: $0 <arquivo.dump|sql|sql.gz> [nome_banco]"
  exit 1
fi

echo "⚠️  RESTORE destrutivo no banco '$TARGET_DB' em $PGHOST:$PGPORT"
if [[ "${RESTORE_SKIP_CONFIRM:-}" != "1" ]]; then
  read -r -p "Digite RESTORE para confirmar: " confirm
  if [[ "$confirm" != "RESTORE" ]]; then
    echo "Cancelado."
    exit 1
  fi
fi

createdb -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" "$TARGET_DB" 2>/dev/null || true

if [[ "$DUMP_FILE" == *.gz ]]; then
  gunzip -c "$DUMP_FILE" | psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TARGET_DB" -v ON_ERROR_STOP=1
elif [[ "$DUMP_FILE" == *.dump ]]; then
  pg_restore -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TARGET_DB" --clean --if-exists "$DUMP_FILE"
else
  psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$TARGET_DB" -v ON_ERROR_STOP=1 -f "$DUMP_FILE"
fi

echo "✅ Restore concluído em $TARGET_DB"
