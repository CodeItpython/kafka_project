#!/usr/bin/env sh
set -eu

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
SCRIPT_PATH="${ROOT_DIR}/performance/k6/kafka-talk-load.js"
RESULT_DIR="${ROOT_DIR}/performance/k6/results"

mkdir -p "${RESULT_DIR}"
cd "${ROOT_DIR}"

PROFILE="${PROFILE:-smoke}"
TEST_USER_PASSWORD="${TEST_USER_PASSWORD:-password123}"
MESSAGE_PREFIX="${MESSAGE_PREFIX:-k6-load}"
THINK_TIME_SECONDS="${THINK_TIME_SECONDS:-1}"

if command -v k6 >/dev/null 2>&1; then
  BASE_URL="${BASE_URL:-http://localhost:8890}"
  BASE_URL="${BASE_URL}" \
  PROFILE="${PROFILE}" \
  TEST_USER_PASSWORD="${TEST_USER_PASSWORD}" \
  MESSAGE_PREFIX="${MESSAGE_PREFIX}" \
  THINK_TIME_SECONDS="${THINK_TIME_SECONDS}" \
  TEST_USERS="${TEST_USERS:-}" \
  k6 run "${SCRIPT_PATH}"
  exit 0
fi

BASE_URL="${BASE_URL:-http://host.docker.internal:8890}"

docker run --rm \
  -e BASE_URL="${BASE_URL}" \
  -e PROFILE="${PROFILE}" \
  -e TEST_USER_PASSWORD="${TEST_USER_PASSWORD}" \
  -e MESSAGE_PREFIX="${MESSAGE_PREFIX}" \
  -e THINK_TIME_SECONDS="${THINK_TIME_SECONDS}" \
  -e TEST_USERS="${TEST_USERS:-}" \
  -v "${ROOT_DIR}:/workspace" \
  -w /workspace \
  grafana/k6:0.56.0 run /workspace/performance/k6/kafka-talk-load.js
