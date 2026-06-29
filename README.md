# Kafka Talk

Spring Boot, React, Kafka, MongoDB, Elasticsearch, Redis, ELK, Docker, Kubernetes, Jenkins를 이용한 로컬 채팅 프로젝트입니다.

카카오톡처럼 로그인 후 친구 목록, 개인 채팅방, 그룹 채팅방, 메시지 검색, 자동완성, 첨부파일, 삭제 기능을 실험하는 구조입니다.

## 프로젝트 구조

- `backend/auth-service`: 로그인, JWT, 카카오 OAuth, 채팅 API, WebSocket, Kafka consumer/producer
- `backend/discovery-service`: Netflix Eureka discovery server
- `frontend`: React 기반 로그인/채팅 화면
- `k8s`: Docker Desktop Kubernetes에 올릴 매니페스트
- `jenkins`: 로컬 Jenkins CI/CD 이미지와 초기 Job 구성
- `logstash/pipeline`: Logstash 수집 파이프라인
- `docs/logs`: 날짜별 작업 기록

## 로컬 개발 실행

먼저 인프라 컨테이너를 실행합니다.

```bash
docker compose up -d postgres mongodb redis elasticsearch logstash kibana kafka
```

백엔드는 IntelliJ에서 `AuthServiceApplication`을 실행하면 됩니다. 명령어로 실행하려면 아래처럼 실행합니다.

```bash
cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

로컬 기본 설정은 Eureka 등록을 끕니다. Docker Compose나 Kubernetes 배포에서는 discovery-service가 같이 뜨므로 `EUREKA_CLIENT_ENABLED=true`를 명시합니다.

프론트엔드는 아래처럼 실행합니다.

```bash
cd /Users/gunwoo/Documents/KAFKA/frontend
npm install
npm run dev -- --host 127.0.0.1
```

접속 주소는 다음과 같습니다.

- 프론트엔드: `http://127.0.0.1:8880`
- 백엔드 API: `http://localhost:8890`
- Vite 프록시: 프론트의 `/api`, `/ws` 요청을 백엔드 `8890`으로 전달

## 테스트 계정

개발 환경에서는 `app.dev.seed-users=true`일 때 테스트 계정이 자동 생성됩니다.

| 이메일 | 비밀번호 |
| --- | --- |
| `user@example.com` | `password123` |
| `minji@example.com` | `password123` |
| `junho@example.com` | `password123` |
| `seoyeon@example.com` | `password123` |
| `hyejin@example.com` | `password123` |

## Docker Compose

전체 서비스를 Docker Compose로 실행하려면 아래 명령을 사용합니다.

```bash
docker compose up --build
```

주요 포트는 다음과 같습니다.

- React/Nginx 프론트엔드: `http://localhost:8880`
- Auth service: `http://localhost:8890`
- Eureka dashboard: `http://localhost:8761`
- Kafka broker: `localhost:9092`
- Kafka 로컬 개발 listener: `localhost:29092`
- PostgreSQL: `localhost:5432`
- MongoDB: `localhost:27017`
- Redis: `localhost:6379`
- Elasticsearch: `http://localhost:9200`
- Logstash TCP JSON input: `localhost:5001`
- Logstash 컨테이너 내부 주소: `logstash:5000`
- Kibana: `http://localhost:5601`
- Prometheus: `http://localhost:9090`
- Tempo: `http://localhost:3200`
- Grafana: `http://localhost:3000`

macOS에서는 5000번 포트를 AirPlay Receiver가 잡는 경우가 많습니다. 그래서 로컬 PC에서 Logstash로 직접 보낼 때는 `localhost:5001`을 쓰고, Docker/Kubernetes 내부 통신에서는 `logstash:5000`을 씁니다.

## ELK 로그 확인

백엔드는 `logstash` Spring profile에서 구조화 JSON 로그를 Logstash로 보냅니다.

```bash
cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun --args='--spring.profiles.active=logstash --eureka.client.enabled=false'
```

모든 백엔드 HTTP 응답에는 `X-Request-Id`가 포함됩니다. 같은 값이 Logstash/Elasticsearch 로그의 `requestId` 필드로 저장되므로, 브라우저 요청 하나를 Kibana에서 그대로 추적할 수 있습니다.

유용한 확인 명령은 다음과 같습니다.

```bash
curl 'http://localhost:9200/_cat/indices?v'
curl 'http://localhost:9200/kafka-talk-logs-*/_search?pretty'
curl 'http://localhost:9600'
```

Kibana 접속 주소:

```text
http://localhost:5601
```

## Elasticsearch 검색

채팅 메시지의 원본 저장소는 MongoDB입니다. 메시지를 보내면 백엔드가 Kafka 이벤트를 발행하고, Kafka consumer가 메시지를 MongoDB에 저장한 뒤 `chat-messages` Elasticsearch 인덱스에도 색인합니다.

`chat-messages` 인덱스의 `content`, `roomName`, `senderName` 필드는 `search_as_you_type`으로 매핑합니다. 이 방식은 사용자가 검색어를 다 입력하기 전에도 prefix 기반 후보를 빠르게 찾기 위해 Elasticsearch가 `_2gram`, `_3gram`, `_index_prefix` 보조 필드를 자동으로 만드는 구조입니다.

즉 새 메시지는 consumer가 처리한 뒤 자동으로 검색 대상이 됩니다. 기존 인덱스가 옛 `text` 매핑이면 백엔드 시작 시 검색 인덱스를 재생성하고 MongoDB의 메시지 원본을 다시 색인합니다. 메시지 원본은 MongoDB에 남아 있으므로 검색 인덱스는 언제든 재구성 가능한 읽기 모델입니다.

확인 명령:

```bash
curl 'http://localhost:9200/_cat/indices?v'
curl 'http://localhost:9200/chat-messages/_mapping?pretty'
curl 'http://localhost:9200/chat-messages/_search?pretty'
```

## Debezium Outbox Pattern

채팅 메시지 전송은 이제 `KafkaTemplate.send()`를 컨트롤러 요청 흐름에서 바로 호출하지 않습니다. 백엔드는 같은 트랜잭션 안에서 PostgreSQL `outbox_events` 테이블에 이벤트를 먼저 저장하고, outbox relay가 이 테이블의 `PENDING` 이벤트를 Kafka `chat-messages` 토픽으로 발행합니다.

흐름:

```text
사용자 메시지 전송
-> PostgreSQL outbox_events 저장
-> outbox relay가 Kafka 발행
-> Kafka consumer가 MongoDB 원본 저장
-> Elasticsearch 검색 색인
-> Redis unread 증가
-> WebSocket broadcast
```

이 구조를 쓰는 이유는 API 요청 중 Kafka가 잠깐 불안정해도 메시지 이벤트를 DB에 먼저 남겨두고 재시도할 수 있기 때문입니다. 운영에서는 Debezium Outbox Event Router가 읽기 쉬운 기본 컬럼명인 `id`, `aggregatetype`, `aggregateid`, `type`, `payload`, `timestamp`를 사용합니다. 현재 로컬 개발에서는 별도 Kafka Connect 없이 앱 내부 relay가 outbox를 발행합니다.

주요 설정:

```properties
app.outbox.relay.enabled=true
app.outbox.relay.fixed-delay-ms=1000
app.outbox.relay.batch-size=20
app.outbox.relay.max-attempts=10
app.outbox.relay.send-timeout-ms=5000
```

Debezium Connect를 붙여서 CDC 방식으로 발행할 때는 앱 내부 relay 중복 발행을 막기 위해 `APP_OUTBOX_RELAY_ENABLED=false`로 둡니다.

상태 확인 SQL:

```sql
select id, aggregatetype, aggregateid, type, status, attempts, created_at, published_at
from outbox_events
order by created_at desc;
```

## Redis 캐시와 상태 관리

Redis는 빠르게 사라져도 되는 상태를 저장합니다.

- 채팅방 목록 캐시: `cache:rooms:{email}:{query}`, TTL 30초
- 유저 프로필 목록 캐시: `cache:profiles:{query}`, TTL 5분
- 온라인 상태: `state:online:{email}`, TTL 75초
- 입력 중 상태: `state:typing:{roomId}:{email}`, TTL 5초
- 안 읽은 메시지 수: `state:unread:{email}:{roomId}`

메시지가 Kafka consumer를 통해 MongoDB에 저장되면, 백엔드는 수신자별 unread 카운터를 Redis에서 증가시킵니다.
사용자가 채팅방을 열어 메시지 목록을 조회하면 해당 방의 unread 카운터를 삭제해서 읽음 처리합니다.

## 채팅 읽음 상태

안 읽은 메시지 수는 Redis처럼 휘발되어도 되는 빠른 상태로 관리하지만, "언제까지 읽었는지"는 PostgreSQL `chat_room_read_states` 테이블에 저장합니다. 각 채팅방과 사용자 조합마다 `last_read_at`을 남기고, 메시지 응답에는 상대 사용자가 해당 메시지 시각 이후까지 읽었는지 계산한 `readCount`, `deliveryStatus`를 포함합니다.

흐름:

```text
사용자가 채팅방 입장 또는 읽음 처리
-> PostgreSQL chat_room_read_states upsert
-> Redis state:unread:{email}:{roomId} 삭제
-> /topic/rooms/{roomId}/read-receipts WebSocket broadcast
-> 프론트엔드가 내 메시지의 전송됨/읽음 표시 갱신
```

읽음 상태는 장기 이력에 가까우므로 PostgreSQL에 보관하고, unread count는 화면 목록을 빠르게 그리기 위한 캐시성 상태이므로 Redis에 둡니다.

## 채팅방 고정과 알림 끄기

채팅방별 사용자 설정은 PostgreSQL `chat_room_user_preferences` 테이블에 저장합니다.

- `pinned`: 내 채팅방 목록에서 해당 방을 위로 고정합니다.
- `muted`: 새 메시지가 와도 내 알림 목록과 푸시 알림 생성 대상에서 제외합니다.

사용 API:

```http
PATCH /api/chat/rooms/{roomId}/preferences
Content-Type: application/json

{
  "pinned": true,
  "muted": false
}
```

응답의 `ChatRoomResponse`에는 `pinned`, `muted`가 포함됩니다. 프론트엔드는 이 값을 기준으로 방 목록을 정렬하고, 알림 아이콘 상태를 표시합니다. unread count는 메시지를 놓치지 않도록 그대로 증가하고, 알림 생성만 끕니다.

## 메시지 리액션

메시지 리액션은 채팅 메시지 원본인 MongoDB `chat_messages` 도큐먼트 안에 저장합니다. 리액션은 메시지와 생명주기가 같고, 메시지를 조회할 때 항상 같이 필요한 작은 부가 상태라 별도 PostgreSQL 테이블보다 MongoDB nested map이 현재 구조에 더 단순합니다.

흐름:

```text
사용자가 메시지 리액션 클릭
-> MongoDB chat_messages.reactionEmailsByEmoji 갱신
-> 같은 이모지를 다시 누르면 내 이메일 제거
-> /topic/rooms/{roomId} WebSocket broadcast
-> 프론트엔드가 같은 messageId를 교체해 리액션 배지 갱신
```

응답 메시지에는 `reactions`가 포함됩니다. 각 항목은 `emoji`, `count`, `reactedByMe`, `reactorEmails`를 담습니다.

## 답장 메시지

답장 기능은 원본 메시지 전체를 다시 참조하지 않고, 전송 시점의 원본 메시지 스냅샷을 새 메시지에 저장합니다. 원본 메시지가 나중에 삭제되거나 수정 개념이 생겨도 답장 말풍선에는 당시 사용자가 본 `replyToSenderName`, `replyToContent`가 안정적으로 남습니다.

흐름:

```text
사용자가 메시지에서 답장 선택
-> 프론트 composer에 답장 미리보기 표시
-> 메시지 전송 시 replyToMessageId 포함
-> 백엔드가 같은 채팅방 메시지인지 검증
-> Kafka outbox payload에 답장 스냅샷 저장
-> Kafka consumer가 MongoDB chat_messages에 답장 필드 저장
-> WebSocket으로 답장 메시지 broadcast
```

답장 스냅샷은 MongoDB 메시지 도큐먼트의 `replyToMessageId`, `replyToSenderName`, `replyToContent` 필드에 저장합니다.

확인 명령:

```bash
redis-cli keys 'cache:*'
redis-cli keys 'state:*'
redis-cli get 'state:unread:user@example.com:ROOM_ID'
```

## 알림과 FCM Push

채팅 메시지가 Kafka consumer에서 MongoDB에 저장되고 WebSocket으로 broadcast된 뒤, 수신자별 알림이 PostgreSQL `notifications` 테이블에 저장됩니다. 접속 중인 브라우저는 `/topic/notifications/{userHash}` WebSocket topic으로 실시간 알림을 받고, 브라우저 Push 권한과 Firebase 설정이 있으면 FCM으로 오프라인 Push도 받을 수 있습니다.

알림 흐름:

```text
Kafka chat-messages consume
-> MongoDB 메시지 저장
-> Redis unread count 증가
-> PostgreSQL notifications 저장
-> WebSocket 알림 전송
-> FCM Web Push 전송
```

로컬 개발에서는 FCM 설정이 없어도 `notifications` 테이블과 WebSocket 알림은 동작합니다. Firebase 프로젝트를 붙일 때는 프론트엔드 `.env`에 Web SDK 값을 넣고, 백엔드에는 Firebase Admin SDK 서비스 계정을 secret으로 넣습니다.

프론트엔드 `.env` 예시:

```properties
VITE_FIREBASE_API_KEY=
VITE_FIREBASE_AUTH_DOMAIN=
VITE_FIREBASE_PROJECT_ID=
VITE_FIREBASE_STORAGE_BUCKET=
VITE_FIREBASE_MESSAGING_SENDER_ID=
VITE_FIREBASE_APP_ID=
VITE_FIREBASE_VAPID_KEY=
```

백엔드/Kubernetes secret 예시:

```yaml
stringData:
  fcm-enabled: "true"
  fcm-project-id: "firebase-project-id"
  fcm-service-account-json: '{"type":"service_account",...}'
  fcm-dry-run: "false"
```

주요 API:

```bash
GET /api/notifications
GET /api/notifications/subscription
PATCH /api/notifications/read-all
POST /api/notifications/push-tokens
DELETE /api/notifications/push-tokens
```

## Testcontainers 통합 테스트

`auth-service`는 PostgreSQL, MongoDB, Elasticsearch, Redis, Kafka를 실제 Docker 컨테이너로 띄우는 통합 테스트를 포함합니다. 이 테스트는 주요 인프라 연결과 저장/검색/메시지 발행 경로가 깨지지 않았는지 확인합니다.

실행 명령:

```bash
cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:test --tests com.kafka.auth.infra.InfrastructureIntegrationTest
```

검증 범위:

- PostgreSQL: 사용자 계정 저장/조회
- MongoDB: 채팅 메시지 원본 저장/조회
- Elasticsearch: 검색 문서 색인/조회
- Redis: 상태 key/value 저장/조회
- Kafka: `chat-messages` 토픽 메시지 발행

Docker Desktop 29 계열은 docker-java 기본 API 버전인 `1.32`로 `/info`를 호출하면 빈 Docker 정보를 돌려줄 수 있습니다. 그래서 테스트 태스크는 `api.version=1.54`와 Docker Desktop socket 경로를 명시해 Testcontainers가 실제 Docker daemon에 붙도록 설정합니다.

## GPT 대화 요약

채팅방 안의 `GPT 요약` 버튼은 최근 텍스트 메시지를 백엔드에서 모은 뒤 OpenAI Responses API로 요약을 요청합니다.

주의할 점:

- ChatGPT Pro 구독과 OpenAI API Platform 과금은 별도입니다.
- Pro 계정이어도 API를 쓰려면 `platform.openai.com`에서 API billing과 API key를 설정해야 합니다.
- API key는 Git에 커밋하지 않고 `.env`, IntelliJ 환경변수, Kubernetes Secret으로만 주입합니다.

로컬 IDE 실행:

```bash
export OPENAI_API_KEY='sk-...'
export OPENAI_MODEL='gpt-5.2'

cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

Docker Compose 실행:

```bash
export OPENAI_API_KEY='sk-...'
docker compose up -d --build auth-service frontend
```

Kubernetes 실행:

```bash
kubectl -n kafka-project create secret generic auth-secrets \
  --from-literal=jwt-secret='change-this-local-kubernetes-secret-change-this' \
  --from-literal=openai-api-key='sk-...' \
  --dry-run=client -o yaml | kubectl apply -f -
```

주요 API:

- `POST /api/chat/rooms/{roomId}/summary`

응답:

```json
{
  "summary": "대화 요약 내용",
  "model": "gpt-5.2",
  "generatedAt": "2026-06-15T00:00:00Z",
  "messageCount": 20
}
```

## 프로필과 상태메시지

사용자는 자신의 이름, 상태메시지, 프로필 이미지를 수정할 수 있습니다. 친구 목록에서는 다른 사용자의 프로필 이미지와 상태메시지를 볼 수 있고, 친구를 선택하면 공개 프로필과 최근 프로필 변경 이력을 확인할 수 있습니다.

주요 API:

- `GET /api/users/me/profile`: 내 프로필과 최근 히스토리 조회
- `PATCH /api/users/me/profile`: 이름/상태메시지 수정
- `POST /api/users/me/profile-image`: 프로필 이미지 업로드
- `GET /api/users/{userId}/profile`: 다른 사용자의 공개 프로필과 히스토리 조회
- `GET /api/users/profile-images/{fileName}`: 프로필 이미지 파일 조회

현재 프로필 이미지는 로컬 `uploads/profiles`에 저장합니다. 이후 MinIO/S3 단계에서 이 저장소를 객체 스토리지로 교체합니다.

## 카카오 로그인 설정

현재 카카오 로그인이 안 된다면 먼저 백엔드에서 아래 응답을 확인합니다.

```bash
curl -i http://localhost:8890/api/auth/oauth/kakao/authorize
```

`KAKAO_CLIENT_ID가 설정되어 있지 않습니다.`가 나오면 Kakao Developers 문제가 아니라 로컬 백엔드 실행 환경에 키가 들어가지 않은 상태입니다.

Kakao Developers에서는 다음 값을 등록합니다.

- Web 플랫폼 사이트 도메인: `http://localhost:8880`
- Redirect URI: `http://localhost:8890/oauth2/callback/kakao`
- REST API 키: 백엔드의 `KAKAO_CLIENT_ID`
- Client Secret을 활성화했다면: 백엔드의 `KAKAO_CLIENT_SECRET`

로컬에서 키를 Git에 올리지 않고 쓰려면 `.env.local` 같은 파일을 만들고 직접 source 한 뒤 실행합니다. `.env.local`은 Git에 커밋하지 않습니다.

```bash
export KAKAO_CLIENT_ID='REST_API_KEY'
export KAKAO_CLIENT_SECRET='CLIENT_SECRET'
export KAKAO_REDIRECT_URI='http://localhost:8890/oauth2/callback/kakao'

cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

Docker Compose로 auth-service까지 띄우는 경우에는 같은 환경변수를 shell에 export한 뒤 `docker compose up --build`를 실행하면 `docker-compose.yml`이 값을 넘겨줍니다.

## Kubernetes

Docker Desktop Kubernetes는 Docker Desktop 안에서 로컬 단일 노드 Kubernetes 클러스터를 켜는 기능입니다. Kafka 컨테이너 안에 Kubernetes나 ELK를 넣는다는 의미가 아닙니다.

현재 구조는 다음처럼 역할별 컨테이너/Pod를 나눕니다.

- Kafka: 메시지 브로커
- PostgreSQL: 유저/인증 데이터
- MongoDB: 채팅 원본 메시지
- Elasticsearch: 채팅 검색/로그 검색 인덱스
- Logstash: 로그 수집/가공
- Kibana: Elasticsearch 시각화 UI
- MinIO: 이미지/GIF/프로필 이미지 같은 바이너리 파일 저장
- Prometheus: Spring Boot metrics 수집
- Grafana: metrics/traces 대시보드
- Tempo: OpenTelemetry trace 저장
- Spring Boot: API/WebSocket/Kafka 처리
- React: 웹 UI

Docker Desktop Kubernetes를 켠 뒤 context를 확인합니다.

```bash
kubectl config get-contexts
kubectl config use-context docker-desktop
kubectl cluster-info
```

이미지를 빌드하고 Kubernetes에 적용합니다.

```bash
scripts/docker-build.sh
scripts/k8s-apply.sh
```

배포 후 접속 주소:

- 프론트엔드 NodePort: `http://localhost:30880`
- Auth service NodePort: `http://localhost:30890`
- Kibana NodePort: `http://localhost:30601`
- MinIO API NodePort: `http://localhost:30900`
- MinIO Console NodePort: `http://localhost:30901`
- Prometheus NodePort: `http://localhost:30909`
- Grafana NodePort: `http://localhost:30300`

MinIO 기본 로컬 계정:

```text
ID: kafka-talk
PW: kafka-talk-secret
Bucket: kafka-talk-files
```

백엔드는 `APP_STORAGE_TYPE=s3`일 때 MinIO/S3에 파일을 저장합니다. 브라우저에는 여전히 `/api/chat/attachments/{fileName}`, `/api/users/profile-images/{fileName}` URL을 내려주고, 백엔드가 내부에서 MinIO 객체를 읽어 응답합니다.
IDE에서 `:auth-service:bootRun`으로만 실행하면 기본값은 `APP_STORAGE_TYPE=local`이므로 기존처럼 `/Users/gunwoo/Documents/KAFKA/backend/uploads` 아래에 저장됩니다.

## Observability

백엔드는 Spring Boot Actuator, Micrometer Prometheus, Micrometer Tracing, OpenTelemetry OTLP exporter를 사용합니다. ELK는 로그와 검색 확인, Prometheus/Grafana/Tempo는 metrics와 trace 확인에 사용합니다.

각 도구 역할:

- Elasticsearch: 채팅 메시지 검색 색인과 Logstash가 보낸 JSON 로그 저장소
- Logstash: auth-service JSON 로그를 받아 Elasticsearch 인덱스로 전송
- Kibana: Elasticsearch에 저장된 로그와 채팅 색인을 검색하는 UI
- Prometheus: `/actuator/prometheus`를 주기적으로 긁어 metrics 저장
- Tempo: OpenTelemetry trace 저장
- Grafana: Prometheus metrics와 Tempo trace를 한 화면에서 보는 UI

노출되는 주요 엔드포인트:

```text
http://localhost:8890/actuator/health
http://localhost:8890/actuator/prometheus
```

Docker Compose로 전체 관측성 스택을 띄우면 다음 주소를 사용합니다.

```bash
docker compose up -d --build auth-service prometheus tempo grafana
```

```text
Prometheus: http://localhost:9090
Grafana: http://localhost:3000
Tempo: http://localhost:3200
Grafana ID/PW: admin / admin
```

Kubernetes 배포에서는 다음 주소를 사용합니다.

```text
Prometheus: http://localhost:30909
Grafana: http://localhost:30300
Grafana ID/PW: admin / admin
```

Grafana datasource는 자동 등록됩니다.

- `Kafka Talk Prometheus`: metrics 조회
- `Kafka Talk Tempo`: OpenTelemetry trace 조회

커스텀 metrics 이름:

```text
kafka_talk_kafka_publish_duration_seconds
kafka_talk_kafka_consume_duration_seconds
kafka_talk_elasticsearch_index_duration_seconds
kafka_talk_search_duration_seconds
kafka_talk_websocket_broadcast_duration_seconds
```

로컬 IDE에서 auth-service만 실행할 때는 tracing sampling 기본값이 `0.0`입니다. Tempo까지 같이 띄워 trace를 보고 싶으면 다음 환경변수를 주고 실행합니다.

```bash
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
export MANAGEMENT_OTLP_TRACING_ENDPOINT=http://localhost:4318/v1/traces

cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

소스 변경 후 한 번에 로컬 Kubernetes에 반영하려면 다음 스크립트를 사용합니다.

```bash
scripts/deploy-local-k8s.sh
```

## Jenkins CI/CD

Jenkins는 로컬에서 다음 주소로 접속합니다.

```text
http://localhost:18081
```

초기 비밀번호 확인:

```bash
docker exec kafka-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

CI만 사용할 때:

```bash
docker compose up -d --build jenkins
```

로컬 Docker 이미지 빌드와 Kubernetes 배포까지 Jenkins에서 실행하려면 배포 override를 같이 켭니다.

```bash
docker compose -f docker-compose.yml -f docker-compose.jenkins-deploy.yml up -d --build jenkins
```

이 모드는 Jenkins 컨테이너에 Docker socket과 kubeconfig를 마운트합니다. 신뢰할 수 있는 로컬 PC에서만 사용해야 합니다.
Jenkins 컨테이너 안에서는 Docker Desktop Kubernetes의 `localhost` API 주소를 직접 사용할 수 없기 때문에, CD 파이프라인은 `scripts/prepare-jenkins-kubeconfig.sh`로 임시 kubeconfig를 만들고 `host.docker.internal` 주소로 보정합니다.

현재 기본 브랜치 전략:

- `kafka-chat-ci`: `main` 브랜치 push를 감시하고 백엔드/프론트 검증을 수행합니다.
- `kafka-chat-main-ci`: `main` 브랜치 push를 감시하고 백엔드/프론트 검증을 수행합니다.
- `kafka-chat-local-deploy`: `kafka-chat-main-ci` 성공 후 Docker 이미지 빌드와 로컬 Kubernetes 배포를 수행합니다.
- 기능 브랜치 배포가 필요하면 Jenkins 컨테이너 실행 전 `KAFKA_PROJECT_CI_BRANCH`와 `KAFKA_PROJECT_DEPLOY_BRANCH`를 원하는 브랜치로 지정합니다.

CD가 실패할 때 가장 먼저 확인할 것:

```bash
kubectl config get-contexts
kubectl config current-context
docker exec kafka-jenkins kubectl config get-contexts
```

context가 비어 있으면 Jenkins 문제가 아니라 로컬 Kubernetes 클러스터가 아직 준비되지 않은 상태입니다. Docker Desktop 설정에서 Kubernetes를 켜고 `docker-desktop` context가 생긴 뒤 다시 CD를 실행해야 합니다.

## 작업 기록

의미 있는 작업을 한 뒤에는 날짜별 로그에 기록합니다.

```bash
scripts/worklog.sh "작업 요약"
```

이 저장소는 `.githooks/pre-commit`으로 `docs/logs/` 기록 없는 커밋을 막도록 구성되어 있습니다.
