# OpenAI GPT 대화 요약

## 기능 흐름

1. 사용자가 채팅방에서 `GPT 요약` 버튼을 누른다.
2. 프론트엔드가 `POST /api/chat/rooms/{roomId}/summary`를 호출한다.
3. 백엔드는 사용자가 해당 채팅방을 볼 권한이 있는지 확인한다.
4. MongoDB `chat_messages`에서 최근 메시지 80개를 조회한다.
5. 나에게 삭제된 메시지와 모두에게 삭제된 메시지를 제외한다.
6. 텍스트 메시지만 transcript로 만들어 OpenAI Responses API에 전달한다.
7. 요약 결과를 화면의 `대화 요약` 카드에 표시한다.

## 설정값

`backend/auth-service/src/main/resources/application.properties`는 다음 환경변수를 읽는다.

```properties
app.openai.api-key=${OPENAI_API_KEY:}
app.openai.model=${OPENAI_MODEL:gpt-5.2}
app.openai.responses-url=${OPENAI_RESPONSES_URL:https://api.openai.com/v1/responses}
```

## 로컬 실행

```bash
export OPENAI_API_KEY='sk-...'
export OPENAI_MODEL='gpt-5.2'

cd /Users/gunwoo/Documents/KAFKA/backend
./gradlew :auth-service:bootRun
```

## Docker Compose

`.env`에 다음 값을 넣고 `docker compose`를 실행한다.

```env
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-5.2
OPENAI_RESPONSES_URL=https://api.openai.com/v1/responses
```

```bash
docker compose up -d --build auth-service frontend
```

## Kubernetes

`auth-secrets` Secret에 `openai-api-key`를 넣는다.

```bash
kubectl -n kafka-project create secret generic auth-secrets \
  --from-literal=jwt-secret='change-this-local-kubernetes-secret-change-this' \
  --from-literal=openai-api-key='sk-...' \
  --dry-run=client -o yaml | kubectl apply -f -
```

`k8s/auth-service.yaml`은 이 값을 `OPENAI_API_KEY` 환경변수로 주입한다.

## ChatGPT Pro와 API

ChatGPT Pro 구독은 ChatGPT 웹/앱 사용권이고, OpenAI API Platform은 별도 billing이다. Pro 계정이어도 API를 쓰려면 `platform.openai.com`에서 API key와 결제 설정을 따로 구성해야 한다.
