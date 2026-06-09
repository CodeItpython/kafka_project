#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NAMESPACE="${K8S_NAMESPACE:-kafka-project}"

cd "$ROOT_DIR"

echo "==> Verifying backend"
(cd backend && ./gradlew test)

echo "==> Verifying frontend"
(cd frontend && npm ci && npm run build)

echo "==> Building local Docker images"
scripts/docker-build.sh

echo "==> Applying Kubernetes manifests"
kubectl apply -k k8s

echo "==> Restarting application deployments"
kubectl -n "$NAMESPACE" rollout restart deployment/discovery-service
kubectl -n "$NAMESPACE" rollout restart deployment/auth-service
kubectl -n "$NAMESPACE" rollout restart deployment/frontend

echo "==> Waiting for rollout"
kubectl -n "$NAMESPACE" rollout status deployment/discovery-service
kubectl -n "$NAMESPACE" rollout status deployment/auth-service
kubectl -n "$NAMESPACE" rollout status deployment/frontend

echo "==> Current pods"
kubectl -n "$NAMESPACE" get pods
