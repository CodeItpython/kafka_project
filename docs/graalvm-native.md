# GraalVM Native Image

## 현재 저장소 역할 분리

- PostgreSQL
  - `users`
  - `email_codes`
  - `user_profile_history`
  - `chat_rooms`
  - `chat_room_participants`
  - `chat_room_hidden_users`
- MongoDB
  - `chat_messages`
  - 메시지 본문, 첨부 메타데이터, 나에게 삭제 목록, 모두에게 삭제 상태를 저장한다.
- Elasticsearch
  - `chat-messages`
  - MongoDB 메시지를 검색하기 위한 색인이다. 원본 저장소가 아니라 검색 최적화용 read model이다.
- Redis
  - 유저 프로필 목록 캐시
  - 안 읽은 메시지 수
  - 접속 상태, typing 상태 같은 짧게 유지되는 상태 데이터
- MinIO/S3
  - 이미지, GIF, 프로필 이미지 같은 바이너리 파일 본문

즉 현재 구조는 채팅방과 친구/사용자처럼 관계가 중요한 데이터는 PostgreSQL, 계속 쌓이고 유연한 필드가 필요한 채팅 메시지는 MongoDB, 검색은 Elasticsearch, 빠른 상태 조회는 Redis로 나눈다.

## GraalVM 적용 방식

이 프로젝트는 Spring Boot 3 기반이라 GraalVM Native Image 적용이 가능하다. 다만 Kafka, MongoDB, Elasticsearch, S3 SDK, Observability처럼 런타임 동작이 많은 의존성이 섞여 있으므로 기본 배포는 JVM으로 유지하고, `auth-service`만 선택적으로 Native Image로 빌드한다.

기본 JVM 이미지 빌드:

```bash
cd /Users/gunwoo/Documents/KAFKA
IMAGE_TAG=local scripts/docker-build.sh
```

auth-service Native Image 빌드:

```bash
cd /Users/gunwoo/Documents/KAFKA
AUTH_IMAGE_MODE=native IMAGE_TAG=native-local scripts/docker-build.sh
```

Native Image 단독 Gradle 빌드:

```bash
cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:nativeCompile
```

생성 결과:

```text
backend/auth-service/build/native/nativeCompile/auth-service
```

검증 결과:

```text
./gradlew :auth-service:tasks --group build
./gradlew :auth-service:test
./gradlew :auth-service:nativeCompile
```

`nativeCompile`은 로컬 첫 빌드 기준 약 12분 40초가 걸렸고, 생성된 실행 파일 크기는 약 328MB다.

## 왜 discovery-service는 우선 제외했나

`discovery-service`는 Netflix Eureka 서버 역할이다. 로컬 학습용 인프라 성격이 강하고, 실제 사용자 트래픽과 채팅 API는 `auth-service`가 처리한다. Native Image의 이점인 빠른 시작과 낮은 메모리 사용은 API 서버에 먼저 적용하는 것이 효과가 크다.

## 주의할 점

- Native Image 빌드는 JVM jar 빌드보다 오래 걸린다.
- 현재 프로젝트의 첫 Native Image 빌드는 약 12분 이상 걸렸다.
- 네이티브 실행 파일은 빌드한 OS/CPU 아키텍처에 묶인다.
- 런타임 리플렉션을 강하게 쓰는 라이브러리는 추가 Runtime Hints가 필요할 수 있다.
- 현재 Jenkins 기본 CD는 안정성을 위해 JVM 이미지를 사용한다.
- Native Image를 CD에 붙이려면 먼저 `AUTH_IMAGE_MODE=native scripts/docker-build.sh` 빌드와 컨테이너 기동을 로컬에서 검증해야 한다.
