#!/usr/bin/env bash
set -Eeuo pipefail

runner_state_dir="${RUNNER_STATE_DIR:-/runner}"
runner_mode="${RUNNER_MODE:-run}"

mkdir -p "${runner_state_dir}"
cp -a /opt/actions-runner/. "${runner_state_dir}/"
cd "${runner_state_dir}"

case "${runner_mode}" in
  configure)
    if [[ -f .runner ]]; then
      exit 0
    fi

    : "${GITHUB_REPOSITORY_URL:?GITHUB_REPOSITORY_URL is required}"
    : "${GITHUB_RUNNER_TOKEN:?GITHUB_RUNNER_TOKEN is required}"
    : "${GITHUB_RUNNER_NAME:?GITHUB_RUNNER_NAME is required}"
    : "${GITHUB_RUNNER_LABELS:?GITHUB_RUNNER_LABELS is required}"

    ./config.sh \
      --url "${GITHUB_REPOSITORY_URL}" \
      --token "${GITHUB_RUNNER_TOKEN}" \
      --name "${GITHUB_RUNNER_NAME}" \
      --labels "${GITHUB_RUNNER_LABELS}" \
      --work _work \
      --unattended \
      --replace
    ;;
  run)
    if [[ ! -f .runner ]]; then
      echo "Runner is not configured. Run the configure bootstrap first." >&2
      exit 1
    fi
    exec ./run.sh
    ;;
  *)
    echo "Unsupported RUNNER_MODE: ${runner_mode}" >&2
    exit 2
    ;;
esac
