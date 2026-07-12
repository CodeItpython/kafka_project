# 보안 대응: 히스토리 시크릿 유출 (2026-07-12)

## 요약
공개(PUBLIC) 저장소의 **git 히스토리**에 실제 시크릿이 커밋되었다가 이후 삭제되었으나, `git rm`은
blob을 제거하지 않으므로 **여전히 `origin/main` 및 여러 원격 브랜치에서 clone으로 복구 가능**한 상태였다.
현재 HEAD(최신 코드)에는 실제 시크릿이 없다(전부 로컬 플레이스홀더 또는 빈 `${ENV}` 참조).

## 유출된 항목 (값은 이 문서에 기재하지 않음)
| 심각도 | 항목 | 최초 유출 위치(커밋:경로) |
|--------|------|---------------------------|
| CRITICAL | JWT HS256 서명 시크릿 (`app.jwtSecret` / `APP_JWT_SECRET`) | `e0ef82e:src/main/resources/application.properties`, `38c4447:.env` |
| HIGH | Kakao OAuth2 client secret (로테이션된 과거 값 포함) | `38c4447:.env`, `e0ef82e`/`0bdb8d3`/`f81c0da`/`3c18d38:application.properties` |
| MEDIUM | Kakao client id (실제 프로덕션 앱 — 리다이렉트 `kafka-project-1x9o.onrender.com`) | 상동 |
| LOW | 과거 MySQL `spring.datasource.password=root` | `e0ef82e:application.properties` |

## 대응 체크리스트

### 1) 로테이션 — **실제 해결책 (필수, 최우선)**
공개 저장소는 이미 clone/인덱싱되었을 수 있으므로, 히스토리를 지워도 "유출 취소"가 되지 않는다.
유출된 값은 **모두 폐기(compromised)로 간주**하고 재발급한다.

- [ ] **JWT 서명 시크릿 교체**: ≥64바이트 랜덤값 생성(`openssl rand -base64 64`) 후 **모든 서비스**
      (auth/chat/order/shopping/signaling)의 배포 환경변수 `JWT_SECRET` 에 동일하게 주입.
      → 기존/위조 토큰이 전부 무효화된다. 코드/저장소에는 넣지 말 것(현재도 `${JWT_SECRET}` 참조만 있음).
- [ ] **Kakao OAuth2 재발급**: [Kakao Developers 콘솔]에서 client secret 재발급(가능하면 앱 키 재생성).
      배포 환경변수 `KAKAO_CLIENT_ID` / `KAKAO_CLIENT_SECRET` 갱신. (콘솔 작업은 계정 소유자만 가능)
- [ ] 배포 플랫폼(render 등) 환경변수 갱신 후 재배포.

### 2) 히스토리 정리 (2차, 파괴적 — 팀 협의 필요)
- [ ] `scripts/purge-git-secrets.sh` 를 **새 미러 클론**에서 실행(사용법은 스크립트 상단 주석 참고).
- [ ] `git push --force --mirror` 는 팀 협의 후 수동 진행. 모든 협업자는 재작성 후 저장소를 다시 clone.
- 주의: 이미 공개된 값은 rotate가 되어야 실제로 안전해진다. 히스토리 정리는 부차적이다.

### 3) 재발 방지
- [x] `.gitignore` 가 `.env`(및 `.env.*`)를 무시, `.env.example` 만 화이트리스트 — 이미 적용됨.
- [x] `.pre-commit-config.yaml` (gitleaks) 추가 — `pip install pre-commit && pre-commit install` 로 활성화.
- [x] `.github/workflows/gitleaks.yml` (전체 히스토리 스캔) 추가 — GitHub Actions 사용 시 자동.
      Jenkins를 쓰는 경우 파이프라인에 `gitleaks detect --redact` 단계를 추가하는 것을 권장.

## 현재 HEAD 상태(참고 — 유출 아님)
`docker-compose.yml`, 각 서비스 `application.properties`, `k8s/*.yaml` 의 하드코딩 값은 전부
로컬 개발용 플레이스홀더(`local-*-change-me`, `kafka`, `admin`, `kafka-talk-secret`)이거나
빈 `${ENV:-}` 참조다. 다만 이 값들이 **실제 배포로 승격되면 안 되므로**, 비로컬 배포 시에는 반드시
환경변수로 실제 값을 주입한다.
