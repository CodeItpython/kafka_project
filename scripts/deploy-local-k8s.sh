#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
NAMESPACE="${K8S_NAMESPACE:-kafka-project}"
IMAGE_TAG="${IMAGE_TAG:-local-$(date +%Y%m%d%H%M%S)}"

cd "$ROOT_DIR"

echo "==> Verifying backend"
(cd backend && ./gradlew test)

echo "==> Verifying frontend"
(cd frontend && npm ci && npm run build)

echo "==> Building local Docker images"
IMAGE_TAG="$IMAGE_TAG" scripts/docker-build.sh

echo "==> Migrating superseded stateful Deployments to StatefulSets (if any)"
# postgres/mongodb/redis/elasticsearch/minio are now StatefulSets backed by PVCs.
# Drop any old same-named Deployment so duplicate pods don't linger.
for svc in postgres mongodb redis elasticsearch minio; do
  kubectl -n "$NAMESPACE" delete deployment "$svc" --ignore-not-found
done

echo "==> Applying Kubernetes manifests"
kubectl apply -k k8s

echo "==> Updating application images to tag ${IMAGE_TAG}"
kubectl -n "$NAMESPACE" set image deployment/discovery-service discovery-service="kafka-discovery-service:${IMAGE_TAG}"
kubectl -n "$NAMESPACE" set image deployment/auth-service auth-service="kafka-auth-service:${IMAGE_TAG}"
kubectl -n "$NAMESPACE" set image deployment/frontend frontend="kafka-frontend:${IMAGE_TAG}"

echo "==> Waiting for rollout"
kubectl -n "$NAMESPACE" rollout status deployment/discovery-service
kubectl -n "$NAMESPACE" rollout status deployment/auth-service
kubectl -n "$NAMESPACE" rollout status deployment/frontend

echo "==> Current pods"
kubectl -n "$NAMESPACE" get pods
