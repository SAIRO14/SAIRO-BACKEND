#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 IMAGE_ARCHIVE IMAGE_TAG HEALTH_URL" >&2
  exit 2
fi

image_archive="$1"
image_tag="$2"
health_url="$3"
deploy_dir="${SAIRO_DEPLOY_DIR:-/opt/sairo}"
env_file="$deploy_dir/.env.app"
compose_file="$deploy_dir/compose.app.yaml"
caddy_file="$deploy_dir/Caddyfile"
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

for required_file in \
  "$image_archive" \
  "$env_file" \
  "$compose_file" \
  "$caddy_file" \
  "$script_dir/compose.app.yaml" \
  "$script_dir/Caddyfile"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Required file not found: $required_file" >&2
    exit 1
  fi
done

if [[ -z "$health_url" ]]; then
  echo "HEALTH_URL must not be empty." >&2
  exit 1
fi

previous_container="$(docker compose --env-file "$env_file" -f "$compose_file" ps -q backend 2>/dev/null || true)"
previous_image=""
if [[ -n "$previous_container" ]]; then
  previous_image="$(docker inspect --format '{{.Config.Image}}' "$previous_container")"
fi

compose_backup="$deploy_dir/compose.app.yaml.rollback"
caddy_backup="$deploy_dir/Caddyfile.rollback"
cp "$compose_file" "$compose_backup"
cp "$caddy_file" "$caddy_backup"

rollback() {
  set +e
  echo "Deployment failed; restoring the previous app configuration." >&2
  cp "$compose_backup" "$compose_file"
  cp "$caddy_backup" "$caddy_file"

  if [[ -n "$previous_image" ]] && docker image inspect "$previous_image" >/dev/null 2>&1; then
    APP_IMAGE="$previous_image" docker compose \
      --env-file "$env_file" \
      -f "$compose_file" \
      up -d --no-build backend proxy
  else
    echo "No previous image is available for automatic rollback." >&2
  fi
  set -e
}

rollback_needed=true
trap 'if [[ "$rollback_needed" == true ]]; then rollback; fi' EXIT

install -m 0644 "$script_dir/compose.app.yaml" "$compose_file"
install -m 0644 "$script_dir/Caddyfile" "$caddy_file"

if ! APP_IMAGE="$image_tag" docker compose --env-file "$env_file" -f "$compose_file" config --quiet; then
  exit 1
fi

if ! gzip -dc "$image_archive" | docker load; then
  exit 1
fi

if ! docker image inspect "$image_tag" >/dev/null 2>&1; then
  echo "Loaded archive does not contain $image_tag." >&2
  exit 1
fi

if ! APP_IMAGE="$image_tag" docker compose \
  --env-file "$env_file" \
  -f "$compose_file" \
  up -d --no-build backend; then
  exit 1
fi

backend_healthy=false
for _ in {1..30}; do
  container_id="$(APP_IMAGE="$image_tag" docker compose --env-file "$env_file" -f "$compose_file" ps -q backend)"
  if [[ -z "$container_id" ]]; then
    break
  fi
  health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"

  if [[ "$health_status" == "healthy" ]]; then
    backend_healthy=true
    break
  fi
  if [[ "$health_status" == "unhealthy" || "$health_status" == "exited" ]]; then
    break
  fi
  sleep 5
done

if [[ "$backend_healthy" != true ]]; then
  echo "Backend did not become healthy." >&2
  exit 1
fi

if ! APP_IMAGE="$image_tag" docker compose \
  --env-file "$env_file" \
  -f "$compose_file" \
  up -d --no-build --force-recreate proxy; then
  exit 1
fi

public_healthy=false
for _ in {1..12}; do
  if curl --fail --silent --show-error --max-time 10 "$health_url" >/dev/null; then
    public_healthy=true
    break
  fi
  sleep 5
done

if [[ "$public_healthy" != true ]]; then
  echo "Public health check failed: $health_url" >&2
  exit 1
fi

docker image prune --force >/dev/null
rollback_needed=false
echo "Deployment completed: $image_tag"
