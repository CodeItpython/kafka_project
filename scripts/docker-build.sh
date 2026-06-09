#!/usr/bin/env sh
set -eu

docker build -f backend/Dockerfile --build-arg SERVICE=discovery-service -t kafka-discovery-service:local backend
docker build -f backend/Dockerfile --build-arg SERVICE=auth-service -t kafka-auth-service:local backend
docker build -t kafka-frontend:local frontend
