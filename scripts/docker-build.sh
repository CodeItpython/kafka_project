#!/usr/bin/env sh
set -eu

IMAGE_TAG="${IMAGE_TAG:-local}"
AUTH_IMAGE_MODE="${AUTH_IMAGE_MODE:-jvm}"
BACKEND_IMAGE_MODE="${BACKEND_IMAGE_MODE:-local-jar}"
FRONTEND_IMAGE_MODE="${FRONTEND_IMAGE_MODE:-local-dist}"

build_backend_jvm_image() {
  service="$1"
  image_name="$2"

  case "$BACKEND_IMAGE_MODE" in
    local-jar)
      if ! ls "backend/${service}/build/libs/"*.jar >/dev/null 2>&1; then
        (cd backend && ./gradlew ":${service}:bootJar")
      fi
      docker build -f backend/Dockerfile.runtime --build-arg SERVICE="$service" -t "${image_name}:${IMAGE_TAG}" -t "${image_name}:local" backend
      ;;
    source-docker)
      docker build -f backend/Dockerfile --build-arg SERVICE="$service" -t "${image_name}:${IMAGE_TAG}" -t "${image_name}:local" backend
      ;;
    *)
      echo "BACKEND_IMAGE_MODE must be either 'local-jar' or 'source-docker'." >&2
      exit 1
      ;;
  esac
}

build_frontend_image() {
  case "$FRONTEND_IMAGE_MODE" in
    local-dist)
      if [ ! -f frontend/dist/index.html ]; then
        (cd frontend && npm ci && npm run build)
      fi
      docker build -f frontend/Dockerfile.runtime -t "kafka-frontend:${IMAGE_TAG}" -t kafka-frontend:local frontend
      ;;
    source-docker)
      docker build -t "kafka-frontend:${IMAGE_TAG}" -t kafka-frontend:local frontend
      ;;
    *)
      echo "FRONTEND_IMAGE_MODE must be either 'local-dist' or 'source-docker'." >&2
      exit 1
      ;;
  esac
}

build_backend_jvm_image discovery-service kafka-discovery-service

case "$AUTH_IMAGE_MODE" in
  native)
    docker build -f backend/Dockerfile.native -t "kafka-auth-service:${IMAGE_TAG}" -t kafka-auth-service:local backend
    ;;
  jvm)
    build_backend_jvm_image auth-service kafka-auth-service
    ;;
  *)
    echo "AUTH_IMAGE_MODE must be either 'jvm' or 'native'." >&2
    exit 1
    ;;
esac

build_backend_jvm_image news-service kafka-news-service

build_frontend_image
