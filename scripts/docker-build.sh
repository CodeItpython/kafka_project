#!/usr/bin/env sh
set -eu

IMAGE_TAG="${IMAGE_TAG:-local}"
AUTH_IMAGE_MODE="${AUTH_IMAGE_MODE:-jvm}"

docker build -f backend/Dockerfile --build-arg SERVICE=discovery-service -t "kafka-discovery-service:${IMAGE_TAG}" -t kafka-discovery-service:local backend

case "$AUTH_IMAGE_MODE" in
  native)
    docker build -f backend/Dockerfile.native -t "kafka-auth-service:${IMAGE_TAG}" -t kafka-auth-service:local backend
    ;;
  jvm)
    docker build -f backend/Dockerfile --build-arg SERVICE=auth-service -t "kafka-auth-service:${IMAGE_TAG}" -t kafka-auth-service:local backend
    ;;
  *)
    echo "AUTH_IMAGE_MODE must be either 'jvm' or 'native'." >&2
    exit 1
    ;;
esac

docker build -t "kafka-frontend:${IMAGE_TAG}" -t kafka-frontend:local frontend
