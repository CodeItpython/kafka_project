#!/usr/bin/env sh
set -eu

kubectl apply -k k8s
kubectl -n kafka-project get pods
