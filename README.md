# Kafka Chat Project

Spring Boot + Spring Security + Spring Cloud Netflix Eureka backend, React frontend, Kafka messaging, Redis cache/presence state, MongoDB chat history, Elasticsearch search, Logstash/Kibana observability, Docker, Kubernetes, and Jenkins local CI/CD scaffold.

## Structure

- `backend/auth-service`: JWT authentication and chat API
- `backend/discovery-service`: Netflix Eureka discovery server
- `frontend`: React login and chat page
- `jenkins`: Jenkins image for local CI/CD
- `k8s`: local Kubernetes manifests
- `docs/logs`: daily implementation notes

## Quick Start

Start infrastructure first:

```bash
docker compose up -d postgres mongodb redis elasticsearch logstash kibana kafka
```

```bash
cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

In IntelliJ, running `AuthServiceApplication` directly is enough for local backend development. The local default keeps Eureka registration disabled. Docker Compose and Kubernetes explicitly set `EUREKA_CLIENT_ENABLED=true` when the discovery service is running.

```bash
cd /Users/gunwoo/Documents/KAFKA/frontend
npm install
npm run dev -- --host 127.0.0.1
```

Frontend runs on `http://127.0.0.1:8880` and auth/chat API runs on `http://localhost:8890`.

Development test users are seeded automatically when `app.dev.seed-users=true`.

| Email | Password |
| --- | --- |
| `user@example.com` | `password123` |
| `minji@example.com` | `password123` |
| `junho@example.com` | `password123` |
| `seoyeon@example.com` | `password123` |
| `hyejin@example.com` | `password123` |

## Docker

```bash
docker compose up --build
```

This starts:

- React frontend: `http://localhost:8880`
- Auth service: `http://localhost:8890`
- Eureka dashboard: `http://localhost:8761`
- Kafka broker: `localhost:9092`
- Kafka local development listener: `localhost:29092`
- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Redis: `localhost:6379`
- Elasticsearch: `localhost:9200`
- Logstash TCP JSON input: `localhost:5000`
- Kibana: `http://localhost:5601`

## Kubernetes

Docker Desktop Kubernetes can run the local manifests:

```bash
scripts/docker-build.sh
scripts/k8s-apply.sh
```

Then open:

- Frontend NodePort: `http://localhost:30880`
- Auth NodePort: `http://localhost:30890`
- Kibana NodePort: `http://localhost:30601`

After source changes, use the one-shot local deployment script:

```bash
scripts/deploy-local-k8s.sh
```

## Jenkins

Run Jenkins in safe CI mode:

```bash
docker compose up -d --build jenkins
```

Open:

```text
http://localhost:18081
```

Initial password:

```bash
docker exec kafka-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Use `Jenkinsfile` for backend/frontend verification. To allow Jenkins to build local Docker images and deploy to local Kubernetes, use the explicit deploy override and `Jenkinsfile.local-deploy`:

```bash
docker compose -f docker-compose.yml -f docker-compose.jenkins-deploy.yml up -d --build jenkins
```

This mode mounts Docker socket and kubeconfig, so use it only on a trusted local machine.

## Elasticsearch

Elasticsearch is available locally at:

```text
http://localhost:9200
```

The chat message source of truth is MongoDB. When a user sends a message, the backend publishes a Kafka event. The backend Kafka consumer saves the message to MongoDB and then indexes the same message into the `chat-messages` Elasticsearch index. That means new messages are indexed automatically after the consumer processes the event. Existing MongoDB messages need a reindex job if the Elasticsearch index is deleted or rebuilt.

Useful local checks:

```bash
curl http://localhost:9200/_cat/indices?v
curl "http://localhost:9200/chat-messages/_search?q=content:검색어&pretty"
```

## Redis Cache And Presence

Redis is used for short-lived state that should be fast and disposable:

- chat room list cache: `cache:rooms:{email}:{query}`, TTL 30 seconds
- online user state: `state:online:{email}`, TTL 75 seconds
- typing state: `state:typing:{roomId}:{email}`, TTL 5 seconds

Useful local checks:

```bash
redis-cli keys 'cache:*'
redis-cli keys 'state:*'
```

## ELK Logs

The backend sends structured JSON logs to Logstash over TCP. Logstash writes those events into Elasticsearch indices named `kafka-talk-logs-YYYY.MM.dd`. Kibana can inspect those logs at:

```text
http://localhost:5601
```

Useful local checks:

```bash
curl http://localhost:9200/_cat/indices?v
curl "http://localhost:9200/kafka-talk-logs-*/_search?pretty"
```

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
