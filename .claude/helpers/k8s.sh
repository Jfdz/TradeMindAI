#!/bin/bash
# K8s helpers: context loading, connectivity check, kubectl wrapper
# Source this file; do not execute directly.

HELPERS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HELPERS_DIR/common.sh"

K8S_CONTEXTS_FILE="$HELPERS_DIR/../k8s-contexts.json"

# Load namespace + context for a given environment (staging | production).
# Sets K8S_NAMESPACE and K8S_CONTEXT in the caller's environment.
load_k8s_context() {
  local env=${1:-staging}

  if [ ! -f "$K8S_CONTEXTS_FILE" ]; then
    echo -e "${YELLOW}⚠️  k8s-contexts.json not found; using env defaults${NC}" >&2
    K8S_NAMESPACE="${K8S_NAMESPACE:-trading-saas}"
    K8S_CONTEXT="${K8S_CONTEXT:-}"
    return 0
  fi

  K8S_NAMESPACE=$(jq -r ".contexts.$env.namespace // \"trading-saas\"" "$K8S_CONTEXTS_FILE")
  K8S_CONTEXT=$(jq -r ".contexts.$env.context // \"\"" "$K8S_CONTEXTS_FILE")
  export K8S_NAMESPACE K8S_CONTEXT
}

# kubectl wrapper: injects --context and -n when set.
kube() {
  local args=()
  if [ -n "${K8S_CONTEXT:-}" ]; then
    args+=(--context "$K8S_CONTEXT")
  fi
  kubectl "${args[@]}" "$@"
}

# Verify cluster reachable; exit 1 if not.
check_k8s_connectivity() {
  echo -e "${BLUE}🔌 Checking Kubernetes connectivity...${NC}"
  if ! kubectl cluster-info &>/dev/null; then
    echo -e "${RED}❌ Cannot connect to Kubernetes cluster${NC}" >&2
    return 1
  fi
  echo -e "${GREEN}✅ Cluster reachable${NC}"
}

# Return the health endpoint path for a service from k8s-contexts.json.
# Falls back to /health when file or key missing.
get_health_path() {
  local service=$1
  if [ -f "$K8S_CONTEXTS_FILE" ]; then
    jq -r ".services[\"$service\"].healthPath // \"/health\"" "$K8S_CONTEXTS_FILE"
  else
    echo "/health"
  fi
}

# Return the internal port for a service from k8s-contexts.json.
get_service_port() {
  local service=$1
  if [ -f "$K8S_CONTEXTS_FILE" ]; then
    jq -r ".services[\"$service\"].port // \"80\"" "$K8S_CONTEXTS_FILE"
  else
    echo "80"
  fi
}
