#!/usr/bin/env sh
set -eu

NAMESPACE="${K8S_NAMESPACE:-kafka-project}"

# One-time migration: postgres/mongodb/redis/elasticsearch/minio moved from
# Deployment to StatefulSet (now backed by PVCs). Remove any superseded
# Deployment of the same name so we don't end up running duplicate pods.
# The old pods used ephemeral storage, so nothing durable is lost.
for svc in postgres mongodb redis elasticsearch minio; do
  kubectl -n "$NAMESPACE" delete deployment "$svc" --ignore-not-found
done

kubectl apply -k k8s
kubectl -n "$NAMESPACE" get pods
