#!/usr/bin/env sh
set -eu

MESSAGE="${1:-작업 내용을 입력하세요.}"
TODAY="$(date +%Y-%m-%d)"
LOG_DIR="docs/logs"
LOG_FILE="${LOG_DIR}/${TODAY}.md"

mkdir -p "${LOG_DIR}"

if [ ! -f "${LOG_FILE}" ]; then
  {
    echo "# ${TODAY} 작업 로그"
    echo
    echo "## 작업 내용"
    echo
  } > "${LOG_FILE}"
fi

{
  echo "- $(date '+%H:%M') ${MESSAGE}"
} >> "${LOG_FILE}"

echo "Logged: ${LOG_FILE}"
