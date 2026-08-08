#!/usr/bin/env bash
set -Eeuo pipefail

deploy_dir="${SAIRO_DEPLOY_DIR:-/opt/sairo}"
backup_dir="${SAIRO_BACKUP_DIR:-$deploy_dir/backups}"
retention_days="${SAIRO_BACKUP_RETENTION_DAYS:-7}"
env_file="$deploy_dir/.env.db"
compose_file="$deploy_dir/compose.db.yaml"

if [[ ! "$retention_days" =~ ^[0-9]+$ ]]; then
  echo "SAIRO_BACKUP_RETENTION_DAYS must be a non-negative integer." >&2
  exit 1
fi

for required_file in "$env_file" "$compose_file"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Required file not found: $required_file" >&2
    exit 1
  fi
done

install -d -m 0700 "$backup_dir"
exec 9>"$backup_dir/.backup.lock"
if ! flock --nonblock 9; then
  echo "Another PostgreSQL backup is already running." >&2
  exit 1
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="$backup_dir/sairo-$timestamp.dump"
temporary_file="$backup_file.tmp"
trap 'rm -f "$temporary_file"' EXIT

docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
  sh -c 'exec pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom --compress=6' \
  > "$temporary_file"

docker compose --env-file "$env_file" -f "$compose_file" exec -T postgres \
  pg_restore --list < "$temporary_file" >/dev/null

chmod 0600 "$temporary_file"
mv "$temporary_file" "$backup_file"
find "$backup_dir" -type f -name 'sairo-*.dump' -mtime "+$retention_days" -delete

echo "PostgreSQL backup completed: $backup_file"
