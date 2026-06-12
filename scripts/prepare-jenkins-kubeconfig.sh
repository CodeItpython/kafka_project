#!/usr/bin/env sh
set -eu

SOURCE_KUBECONFIG="${KUBECONFIG:-$HOME/.kube/config}"
TARGET_KUBECONFIG="${JENKINS_KUBECONFIG_COPY:-/tmp/kafka-talk-kubeconfig}"
TARGET_CONTEXT="${K8S_CONTEXT:-docker-desktop}"

if [ ! -f "$SOURCE_KUBECONFIG" ]; then
    echo "Kubernetes config file was not found: $SOURCE_KUBECONFIG"
    echo "Enable Docker Desktop Kubernetes and mount ~/.kube into the Jenkins container."
    exit 1
fi

cp "$SOURCE_KUBECONFIG" "$TARGET_KUBECONFIG"
chmod 600 "$TARGET_KUBECONFIG" 2>/dev/null || true
export KUBECONFIG="$TARGET_KUBECONFIG"

if kubectl config get-contexts "$TARGET_CONTEXT" >/dev/null 2>&1; then
    kubectl config use-context "$TARGET_CONTEXT" >/dev/null
fi

if [ "${KAFKA_JENKINS_K8S_HOST_REWRITE:-true}" = "true" ]; then
    current_server="$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}' 2>/dev/null || true)"
    rewritten_server="${KAFKA_JENKINS_K8S_SERVER:-}"
    if [ -z "$rewritten_server" ]; then
        case "$current_server" in
            https://127.0.0.1:*|https://localhost:*)
                server_port="${current_server##*:}"
                rewritten_server="https://host.docker.internal:${server_port}"
                ;;
            *)
                rewritten_server="$current_server"
                ;;
        esac
    fi

    if [ -z "$rewritten_server" ]; then
        echo "Unable to determine Kubernetes API server from kubeconfig."
        exit 1
    fi

    kubectl config unset "clusters.${TARGET_CONTEXT}.certificate-authority-data" >/dev/null 2>&1 || true
    kubectl config unset "clusters.${TARGET_CONTEXT}.certificate-authority" >/dev/null 2>&1 || true
    kubectl config set-cluster "$TARGET_CONTEXT" \
        --server="$rewritten_server" \
        --insecure-skip-tls-verify=true >/dev/null
fi

current_context="$(kubectl config current-context 2>/dev/null || true)"
if [ -z "$current_context" ]; then
    echo "Local Kubernetes context is not configured."
    echo "Enable Docker Desktop Kubernetes, then run: kubectl config use-context docker-desktop"
    exit 1
fi

echo "Prepared Kubernetes context: $current_context"
