# Kafka Auth Project

Spring Boot + Spring Security + Spring Cloud Netflix Eureka backend and React frontend scaffold.

## Structure

- `backend/auth-service`: JWT authentication API
- `backend/discovery-service`: Netflix Eureka discovery server
- `frontend`: React login page
- `docs/logs`: daily implementation notes

## Quick Start

```bash
cd backend
gradle :discovery-service:bootRun
```

```bash
cd backend
gradle :auth-service:bootRun
```

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:8880`, auth API on `http://localhost:8890`, and Eureka on `http://localhost:8761`.

## Docker

```bash
docker compose up --build
```

This starts:

- React frontend: `http://localhost:8880`
- Auth service: `http://localhost:8890`
- Eureka dashboard: `http://localhost:8761`
- Kafka broker: `localhost:9092`

## Kubernetes

Docker Desktop Kubernetes can run the local manifests:

```bash
scripts/docker-build.sh
scripts/k8s-apply.sh
```

Then open:

- Frontend NodePort: `http://localhost:30880`
- Auth NodePort: `http://localhost:30890`

## Work Logging

Use this after each meaningful work step:

```bash
scripts/worklog.sh "작업 요약"
```

The repository uses `.githooks/pre-commit` to block commits that do not include an entry under `docs/logs/`.

## Kakao OAuth Setup

1. Go to Kakao Developers and create an application.
2. Add Web platform site domain: `http://localhost:8880`.
3. Enable Kakao Login.
4. Add redirect URI: `http://localhost:8890/oauth2/callback/kakao`.
5. Copy REST API key into `KAKAO_CLIENT_ID`.
6. Create and copy client secret into `KAKAO_CLIENT_SECRET` if client secret is enabled.

The current API includes a guide endpoint at `GET /api/auth/oauth/kakao/guide`. Full OAuth callback handling can be enabled after the Kakao app keys are registered.
