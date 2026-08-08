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
last_good_file="$deploy_dir/.last-good-image"
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
if [[ -z "$previous_image" && -s "$last_good_file" ]]; then
  read -r previous_image < "$last_good_file"
fi
if [[ -n "$previous_image" ]] && ! docker image inspect "$previous_image" >/dev/null 2>&1; then
  previous_image=""
fi

compose_backup="$deploy_dir/compose.app.yaml.rollback"
caddy_backup="$deploy_dir/Caddyfile.rollback"
cp "$compose_file" "$compose_backup"
cp "$caddy_file" "$caddy_backup"

wait_for_backend() {
  local candidate_image="$1"
  local attempts="$2"
  local container_id
  local health_status

  for ((attempt = 1; attempt <= attempts; attempt++)); do
    container_id="$(APP_IMAGE="$candidate_image" docker compose --env-file "$env_file" -f "$compose_file" ps -q backend)"
    if [[ -z "$container_id" ]]; then
      return 1
    fi

    health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
    if [[ "$health_status" == "healthy" ]]; then
      return 0
    fi
    if [[ "$health_status" == "unhealthy" || "$health_status" == "exited" ]]; then
      return 1
    fi
    sleep 5
  done

  return 1
}

public_health_is_up() {
  local response
  response="$(curl --fail --silent --show-error --max-time 10 "$health_url")" || return 1
  grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<< "$response"
}

wait_for_public_health() {
  local attempts="$1"

  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if public_health_is_up; then
      return 0
    fi
    sleep 5
  done

  return 1
}

record_last_good_image() {
  local candidate_image="$1"
  local temporary_file="$last_good_file.tmp"

  printf '%s\n' "$candidate_image" > "$temporary_file"
  chmod 0600 "$temporary_file"
  mv "$temporary_file" "$last_good_file"
}

rollback() {
  echo "::warning::Deployment failed; restoring the previous app configuration."
  if ! cp "$compose_backup" "$compose_file" || ! cp "$caddy_backup" "$caddy_file"; then
    echo "::error::Failed to restore the previous configuration. Manual recovery is required."
    return 1
  fi

  if [[ -z "$previous_image" ]]; then
    APP_IMAGE="$image_tag" docker compose --env-file "$env_file" -f "$compose_file" stop backend proxy || true
    APP_IMAGE="$image_tag" docker compose --env-file "$env_file" -f "$compose_file" rm -f backend proxy || true
    echo "::error::No previous image is available. Failed containers were stopped; manual recovery is required."
    return 1
  fi

  if ! APP_IMAGE="$previous_image" docker compose \
    --env-file "$env_file" \
    -f "$compose_file" \
    up -d --no-build --force-recreate backend; then
    echo "::error::Failed to start the previous backend image: $previous_image"
    return 1
  fi

  if ! wait_for_backend "$previous_image" 18; then
    echo "::error::The previous backend image did not become healthy: $previous_image"
    return 1
  fi

  if ! APP_IMAGE="$previous_image" docker compose \
    --env-file "$env_file" \
    -f "$compose_file" \
    up -d --no-build --force-recreate proxy; then
    echo "::error::Failed to recreate the proxy during rollback."
    return 1
  fi

  if ! wait_for_public_health 6; then
    echo "::error::Public health check failed after rollback: $health_url"
    return 1
  fi

  record_last_good_image "$previous_image"
  echo "::notice::Rollback completed with image $previous_image."
}

rollback_needed=true
on_exit() {
  local deployment_status=$?
  trap - EXIT INT TERM

  if [[ "$rollback_needed" == true ]]; then
    if ! rollback; then
      echo "::error::Automatic rollback failed. Manual recovery is required."
    fi
  fi

  rm -f "$compose_backup" "$caddy_backup"
  exit "$deployment_status"
}

trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

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

if ! wait_for_backend "$image_tag" 30; then
  echo "Backend did not become healthy." >&2
  exit 1
fi

if ! APP_IMAGE="$image_tag" docker compose \
  --env-file "$env_file" \
  -f "$compose_file" \
  up -d --no-build --force-recreate proxy; then
  exit 1
fi

if ! wait_for_public_health 12; then
  echo "Public health check failed: $health_url" >&2
  exit 1
fi

record_last_good_image "$image_tag"
rollback_needed=false

while IFS= read -r candidate_image; do
  if [[ "$candidate_image" != "$image_tag" && "$candidate_image" != "$previous_image" ]]; then
    docker image rm "$candidate_image" >/dev/null 2>&1 || true
  fi
done < <(docker image ls --filter 'reference=sairo-backend:*' --format '{{.Repository}}:{{.Tag}}')
docker image prune --force >/dev/null || true

echo "Deployment completed: $image_tag"
