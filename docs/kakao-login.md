# Kakao Login Setup

## Kakao Developers 등록 순서

1. Kakao Developers에 접속한다.
   - https://developers.kakao.com/
2. 상단 메뉴에서 `내 애플리케이션`을 선택한다.
3. `애플리케이션 추가하기`를 누른다.
4. 앱 이름과 사업자명을 입력한다.
5. 생성된 앱의 `앱 키` 메뉴에서 `REST API 키`를 확인한다.
   - 이 값이 백엔드의 `KAKAO_CLIENT_ID`로 들어간다.
6. `제품 설정 > 카카오 로그인`으로 이동한다.
7. `활성화 설정`을 `ON`으로 바꾼다.
8. `Redirect URI`에 로컬 콜백 주소를 추가한다.
   - 로컬 백엔드 직접 실행: `http://localhost:8890/oauth2/callback/kakao`
   - 프론트 프록시를 거치는 방식으로 바꾸면 별도 callback 주소를 추가해야 한다.
9. `제품 설정 > 카카오 로그인 > 동의항목`으로 이동한다.
10. 필요한 항목을 설정한다.
    - 닉네임: 필수 또는 선택
    - 카카오계정 이메일: 선택 또는 검수 후 필수
11. `보안 > Client Secret`을 사용한다면 `코드 생성` 후 활성화한다.
    - 이 값은 `KAKAO_CLIENT_SECRET`으로 넣는다.
    - Client Secret은 노출되면 안 되는 서버 전용 값이다.
12. `앱 설정 > 플랫폼`으로 이동해 Web 플랫폼을 등록한다.
    - Site domain: `http://localhost:8880`
    - 백엔드를 브라우저에서 직접 호출한다면 `http://localhost:8890`도 추가한다.

## Spring properties 연결 방식

`backend/auth-service/src/main/resources/application.properties`는 실제 값을 직접 쓰지 않고 환경변수를 참조한다.

```properties
app.kakao.client-id=${KAKAO_CLIENT_ID:}
app.kakao.client-secret=${KAKAO_CLIENT_SECRET:}
app.kakao.redirect-uri=${KAKAO_REDIRECT_URI:http://localhost:8890/oauth2/callback/kakao}
```

이 방식은 GitHub에 민감정보가 올라가는 것을 막기 위한 기본 패턴이다. 실제 키는 `.env`, IntelliJ Run Configuration의 Environment variables, Jenkins credentials, Kubernetes Secret 중 하나로 주입한다.

## 로컬 `.env` 사용

루트의 `.env.example`을 `.env`로 복사한 뒤 실제 값을 채운다.

```bash
cp .env.example .env
```

`.gitignore`에는 `.env`와 `.env.*`가 들어 있으므로 실제 키 파일은 커밋되지 않는다. 예시 파일인 `.env.example`만 커밋한다.

Docker Compose로 실행할 때는 루트 `.env`가 자동으로 읽히고, `docker-compose.yml`에서 다음 환경변수를 `auth-service` 컨테이너로 전달한다.

```yaml
KAKAO_CLIENT_ID: ${KAKAO_CLIENT_ID:-}
KAKAO_CLIENT_SECRET: ${KAKAO_CLIENT_SECRET:-}
KAKAO_REDIRECT_URI: ${KAKAO_REDIRECT_URI:-http://localhost:8890/oauth2/callback/kakao}
```

IntelliJ에서 직접 `AuthServiceApplication`을 실행한다면 Run Configuration의 `Environment variables`에 같은 값을 넣는다.

## 암호화해서 보관하는 방법

`.env`는 로컬에서만 쓰고 Git에 올리지 않는 것이 기본이다. 팀에서 암호화된 비밀값을 저장해야 한다면 다음 중 하나를 쓴다.

- `sops + age`: `.env.enc`처럼 암호화된 파일만 Git에 올리고, 로컬에서 복호화해 `.env`를 만든다.
- `git-crypt`: 권한 있는 사람만 특정 파일을 복호화한다.
- Jenkins Credentials: Jenkins job 안에서는 credentials로 주입하고 파일로 남기지 않는다.
- Kubernetes Secret: 배포 시 `Secret`으로 주입한다.

간단한 로컬 예시는 다음과 같다.

```bash
openssl enc -aes-256-cbc -salt -pbkdf2 -in .env -out .env.enc
openssl enc -d -aes-256-cbc -pbkdf2 -in .env.enc -out .env
```

단, `openssl` 방식은 비밀번호 공유와 관리가 수동이라 팀 프로젝트에서는 `sops + age`가 더 안전하고 추적 가능하다.

## 구현 개념

- Kakao Login은 OAuth 2.0 Authorization Code Flow를 사용한다.
- 브라우저가 Kakao 인증 페이지로 이동한다.
- 사용자가 동의하면 Kakao가 `redirect_uri`로 `code`를 붙여 돌려준다.
- 백엔드는 `code`를 Kakao token API에 보내 access token을 받는다.
- 백엔드는 Kakao user info API로 사용자 식별자와 이메일/닉네임을 가져온다.
- 내부 `UserAccount`를 찾거나 생성한 뒤 이 프로젝트의 JWT를 발급한다.
- 이후 프론트는 기존 일반 로그인과 똑같이 JWT로 API를 호출한다.

## 현재 프로젝트 엔드포인트

- OAuth 시작: `GET /api/auth/oauth/kakao/authorize`
- Kakao Redirect URI: `GET /oauth2/callback/kakao`
- 프론트 복귀 주소: `http://localhost:8880#access_token=...`

프론트는 URL fragment의 `access_token`을 읽어 자기 origin의 `localStorage`에 저장한다. 백엔드 callback origin은 `localhost:8890`이고 프론트 origin은 `localhost:8880`이므로, 백엔드 callback HTML에서 바로 `localStorage`를 저장하면 프론트에서 읽을 수 없다.

## 로컬 테스트

1. Docker 의존 서비스가 떠 있어야 한다.

```bash
docker compose up -d postgres mongodb elasticsearch kafka
```

2. 루트 `.env`에 Kakao 키를 넣는다.

```bash
KAKAO_CLIENT_ID=...
KAKAO_CLIENT_SECRET=...
KAKAO_REDIRECT_URI=http://localhost:8890/oauth2/callback/kakao
```

3. 백엔드를 실행한다.

```bash
cd backend
set -a; source ../.env; set +a
./gradlew :auth-service:bootRun --args='--eureka.client.enabled=false'
```

4. 프론트를 실행한다.

```bash
cd frontend
npm run dev -- --host 0.0.0.0 --port 8880
```

5. 브라우저에서 `http://localhost:8880` 접속 후 `카카오로 로그인`을 누른다.
