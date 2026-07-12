#!/usr/bin/env bash
#
# purge-git-secrets.sh — 공개 저장소 히스토리에서 유출된 시크릿을 제거(레닥션)한다.
#
# ⚠️  경고: 이 스크립트는 git 히스토리를 재작성한다(모든 커밋 SHA가 바뀜).
#     - 반드시 "새로 mirror clone 한 사본"에서 실행할 것. 작업 중인 클론에서 실행하지 말 것.
#     - 실행 후 force-push 는 이 스크립트가 하지 않는다. 팀과 협의 후 수동으로 진행할 것
#       (다른 협업자/동시 작업 세션은 재작성 후 반드시 다시 clone 해야 함).
#     - 저장소가 이미 공개되어 clone/인덱싱되었을 수 있으므로, 히스토리 정리만으로는
#       "유출 취소"가 되지 않는다. 실제 대응은 키 로테이션(docs/SECURITY.md)이다.
#
# 사전 준비:
#   1) git-filter-repo 설치:  pip install git-filter-repo   (또는 brew install git-filter-repo)
#   2) 미러 클론:            git clone --mirror https://github.com/CodeItpython/kafka_project.git kafka_project.git
#   3) 그 디렉터리에서 이 스크립트 실행:  CONFIRM=yes bash /path/to/purge-git-secrets.sh
#
set -euo pipefail

if [[ "${CONFIRM:-}" != "yes" ]]; then
  echo "히스토리를 재작성하는 파괴적 작업입니다. 미러 클론에서 CONFIRM=yes 로 다시 실행하세요." >&2
  echo "예:  CONFIRM=yes bash $0" >&2
  exit 2
fi

if ! command -v git-filter-repo >/dev/null 2>&1; then
  echo "git-filter-repo 가 필요합니다:  pip install git-filter-repo" >&2
  exit 1
fi

# 실제 시크릿 값을 이 파일에 하드코딩하지 않는다. 정규식으로 '키=값' 의 값 부분만
# 히스토리 전체에서 __REDACTED__ 로 치환한다(로테이션된 과거 값들도 함께 정리됨).
REPLACE_FILE="$(mktemp)"
trap 'rm -f "$REPLACE_FILE"' EXIT
cat > "$REPLACE_FILE" <<'EOF'
regex:app\.jwtSecret=\S+==>app.jwtSecret=__REDACTED__
regex:app\.jwt\.secret=\S+==>app.jwt.secret=__REDACTED__
regex:APP_JWT_SECRET=\S+==>APP_JWT_SECRET=__REDACTED__
regex:client-secret=\S+==>client-secret=__REDACTED__
regex:client-id=\S+==>client-id=__REDACTED__
regex:KAKAO_CLIENT_SECRET=\S+==>KAKAO_CLIENT_SECRET=__REDACTED__
regex:KAKAO_CLIENT_ID=\S+==>KAKAO_CLIENT_ID=__REDACTED__
regex:spring\.datasource\.password=root==>spring.datasource.password=__REDACTED__
EOF

echo "[1/2] .env 파일을 히스토리에서 완전히 제거(.env.example 은 유지)"
git filter-repo --invert-paths --path .env --force

echo "[2/2] 남은 파일(application.properties 등)의 시크릿 값 레닥션"
git filter-repo --replace-text "$REPLACE_FILE" --force

cat <<'DONE'

완료(로컬 미러 재작성). 다음 단계는 팀 협의 후 수동으로:
  git push --force --mirror https://github.com/CodeItpython/kafka_project.git
그리고 모든 협업자는 저장소를 다시 clone 해야 합니다.

다시 강조: 이미 공개된 시크릿은 로테이션이 실제 대응입니다(docs/SECURITY.md 참고).
DONE
