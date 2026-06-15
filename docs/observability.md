# Observability

## ELK

- Elasticsearch
  - `chat-messages` 인덱스: MongoDB 채팅 메시지의 검색용 색인
  - `kafka-talk-logs-*` 인덱스: Logstash가 받은 백엔드 JSON 로그
- Logstash
  - auth-service의 Logback TCP JSON 로그를 수신한다.
  - 수신한 로그에 `environment` 필드를 붙여 Elasticsearch로 보낸다.
- Kibana
  - Elasticsearch에 저장된 로그와 색인을 조회하는 UI다.

Docker Compose 접속:

```text
Elasticsearch: http://localhost:9200
Logstash monitoring: http://localhost:9600
Kibana: http://localhost:5601
```

Kubernetes 접속:

```text
Kibana: http://localhost:30601
```

확인 명령:

```bash
curl 'http://localhost:9200/_cat/indices?v'
curl 'http://localhost:9200/kafka-talk-logs-*/_search?pretty'
curl 'http://localhost:9200/chat-messages/_search?pretty'
```

## Prometheus

Prometheus는 auth-service의 `/actuator/prometheus`를 10초마다 수집한다.

Docker Compose:

```text
http://localhost:9090
```

Kubernetes:

```text
http://localhost:30909
```

유용한 쿼리:

```promql
up
http_server_requests_seconds_count
kafka_talk_kafka_publish_duration_seconds_count
kafka_talk_kafka_consume_duration_seconds_count
kafka_talk_elasticsearch_index_duration_seconds_count
kafka_talk_search_duration_seconds_count
kafka_talk_websocket_broadcast_duration_seconds_count
```

## Tempo

Tempo는 auth-service가 OTLP로 보낸 trace를 저장한다.

Docker Compose:

```text
Tempo API: http://localhost:3200
OTLP HTTP: http://localhost:4318
OTLP gRPC: http://localhost:4317
```

Kubernetes에서는 auth-service가 내부 주소 `http://tempo:4318/v1/traces`로 trace를 보낸다.

## Grafana

Grafana는 Prometheus metrics와 Tempo trace를 같이 보는 UI다.

Docker Compose:

```text
http://localhost:3000
admin / admin
```

Kubernetes:

```text
http://localhost:30300
admin / admin
```

Datasource는 자동 등록된다.

- `Kafka Talk Prometheus`
- `Kafka Talk Tempo`

Grafana에서 보는 순서:

1. `Explore`로 이동한다.
2. Metrics는 `Kafka Talk Prometheus`를 선택한다.
3. Trace는 `Kafka Talk Tempo`를 선택한다.
4. HTTP 요청, Kafka publish/consume, Elasticsearch index/search, WebSocket broadcast 지연을 확인한다.
