#!/usr/bin/env bash
set -Eeuo pipefail

if [[ $EUID -eq 0 ]]; then
  echo "Run this script as the non-root deployment user." >&2
  exit 1
fi

repo_url="${GITHUB_REPOSITORY_URL:-https://github.com/SAIRO14/SAIRO-BACKEND}"
runner_name="${GITHUB_RUNNER_NAME:-sairo-app}"
runner_labels="${GITHUB_RUNNER_LABELS:-sairo-deploy}"
runner_version="${GITHUB_RUNNER_VERSION:-2.336.0}"
runner_sha256="${GITHUB_RUNNER_SHA256:-04cf0be1aff4c3ec3554466c39124ca250e3effd8873bb7e8d68535aa9505d5d}"
install_dir="${GITHUB_RUNNER_INSTALL_DIR:-/opt/actions-runner}"

sudo install -d -m 0750 -o "$USER" -g "$USER" "$install_dir"
cd "$install_dir"

if [[ -f .runner ]]; then
  echo "GitHub Actions runner is already configured in $install_dir."
  sudo ./svc.sh start
  sudo ./svc.sh status
  exit 0
fi

: "${GITHUB_RUNNER_TOKEN:?Set a short-lived GitHub runner registration token}"

archive="$(mktemp)"
trap 'rm -f "$archive"' EXIT

download_url="https://github.com/actions/runner/releases/download/v${runner_version}/actions-runner-linux-x64-${runner_version}.tar.gz"
curl --fail --location --silent --show-error "$download_url" --output "$archive"
echo "$runner_sha256  $archive" | sha256sum --check --status
tar -xzf "$archive"

./config.sh \
  --unattended \
  --url "$repo_url" \
  --token "$GITHUB_RUNNER_TOKEN" \
  --name "$runner_name" \
  --labels "$runner_labels" \
  --work _work \
  --replace

unset GITHUB_RUNNER_TOKEN
sudo ./svc.sh install "$USER"
sudo ./svc.sh start
sudo ./svc.sh status
