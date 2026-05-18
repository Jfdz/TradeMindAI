#!/bin/bash
# Fase 2: K8s Read-Only Debugging
# Auto-ejecuta diagnósticos sin hacer cambios
# Analiza automáticamente logs, eventos, recursos

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../helpers/common.sh
source "$SCRIPT_DIR/../helpers/common.sh"
source "$SCRIPT_DIR/../helpers/k8s.sh"

# Configuration
NAMESPACE="${K8S_NAMESPACE:-trading-saas}"
LOG_DIR="./.claude/debug-logs"
CONTEXT="${K8S_CONTEXT:-}"

mkdir -p "$LOG_DIR"
AUDIT_LOG="$LOG_DIR/k8s-debug.jsonl"

# Helper functions
log_event() {
  local event_type=$1
  local status=$2
  local details=$3

  local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  local json=$(cat <<EOF
{
  "timestamp": "$timestamp",
  "event": "$event_type",
  "status": "$status",
  "details": "$details",
  "namespace": "$NAMESPACE"
}
EOF
)
  echo "$json" >> "$AUDIT_LOG"
}

# Check kubectl connectivity
check_k8s_connectivity() {
  echo -e "${BLUE}🔌 Checking Kubernetes connectivity...${NC}"

  if ! kubectl cluster-info &>/dev/null; then
    echo -e "${RED}❌ Cannot connect to Kubernetes cluster${NC}"
    log_event "k8s_connection" "failed" "Cannot connect to cluster"
    return 1
  fi

  local cluster=$(kubectl cluster-info | grep 'Kubernetes master' | cut -d'/' -f3)
  echo -e "${GREEN}✅ Connected to: $cluster${NC}"
  log_event "k8s_connection" "success" "Connected to cluster"
  return 0
}

# Diagnose pod issues
diagnose_pod() {
  local pod_name=$1
  local namespace=${2:-$NAMESPACE}

  echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🔍 Diagnosing Pod: $pod_name${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}\n"

  # 1. Pod status
  echo -e "${BLUE}1️⃣  Pod Status${NC}"
  kubectl get pod "$pod_name" -n "$namespace" -o wide 2>/dev/null || {
    echo -e "${RED}Pod not found${NC}"
    return 1
  }

  # 2. Pod description
  echo -e "\n${BLUE}2️⃣  Pod Details${NC}"
  kubectl describe pod "$pod_name" -n "$namespace" 2>/dev/null | head -50

  # 3. Recent events
  echo -e "\n${BLUE}3️⃣  Recent Events${NC}"
  kubectl get events -n "$namespace" \
    --field-selector involvedObject.name="$pod_name" \
    --sort-by='.lastTimestamp' 2>/dev/null | tail -10 || echo "No recent events"

  # 4. Container logs
  echo -e "\n${BLUE}4️⃣  Container Logs (Last 100 lines)${NC}"
  kubectl logs "$pod_name" -n "$namespace" --tail=100 2>/dev/null || {
    echo -e "${YELLOW}Current logs unavailable, checking previous logs...${NC}"
    kubectl logs "$pod_name" -n "$namespace" --previous --tail=100 2>/dev/null || echo "No logs available"
  }

  # 5. Resource usage
  echo -e "\n${BLUE}5️⃣  Resource Usage${NC}"
  kubectl top pod "$pod_name" -n "$namespace" 2>/dev/null || echo "Metrics unavailable"

  # 6. Analysis
  echo -e "\n${MAGENTA}📊 Analysis${NC}"
  analyze_pod_status "$pod_name" "$namespace"

  log_event "pod_diagnosed" "complete" "Pod: $pod_name"
}

# Analyze pod status and suggest fixes
analyze_pod_status() {
  local pod_name=$1
  local namespace=${2:-$NAMESPACE}

  local pod_status=$(kubectl get pod "$pod_name" -n "$namespace" \
    -o jsonpath='{.status.phase}' 2>/dev/null)

  local container_status=$(kubectl get pod "$pod_name" -n "$namespace" \
    -o jsonpath='{.status.containerStatuses[0].state}' 2>/dev/null)

  echo "Status: $pod_status"

  case "$pod_status" in
    "CrashLoopBackOff"|"Error"|"Failed")
      echo -e "${RED}❌ Pod is in error state${NC}"

      # Check for OOMKilled
      if echo "$container_status" | grep -q "OOMKilled"; then
        echo -e "${YELLOW}💾 Likely cause: Out of Memory (OOMKilled)${NC}"
        echo -e "${CYAN}Suggestion: Increase memory requests/limits in pod spec${NC}"
      fi

      # Check logs for common errors
      local logs=$(kubectl logs "$pod_name" -n "$namespace" --tail=50 2>/dev/null || echo "")
      if echo "$logs" | grep -qi "connection refused"; then
        echo -e "${YELLOW}🔌 Issue: Connection refused${NC}"
        echo -e "${CYAN}Suggestion: Check if dependent service is running${NC}"
      fi

      if echo "$logs" | grep -qi "permission denied"; then
        echo -e "${YELLOW}🔐 Issue: Permission denied${NC}"
        echo -e "${CYAN}Suggestion: Check RBAC and service account permissions${NC}"
      fi
      ;;
    "Pending")
      echo -e "${YELLOW}⏳ Pod is pending${NC}"

      # Check for resource constraints
      local conditions=$(kubectl get pod "$pod_name" -n "$namespace" \
        -o jsonpath='{.status.conditions[*].reason}' 2>/dev/null)

      if echo "$conditions" | grep -q "Insufficient"; then
        echo -e "${YELLOW}💾 Likely cause: Insufficient resources${NC}"
        echo -e "${CYAN}Suggestion: Check cluster resource availability${NC}"
      fi
      ;;
    "Running")
      echo -e "${GREEN}✅ Pod is running${NC}"
      ;;
  esac
}

# Diagnose deployment
diagnose_deployment() {
  local deployment_name=$1
  local namespace=${2:-$NAMESPACE}

  echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🔍 Diagnosing Deployment: $deployment_name${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}\n"

  # 1. Deployment status
  echo -e "${BLUE}1️⃣  Deployment Status${NC}"
  kubectl get deployment "$deployment_name" -n "$namespace" -o wide 2>/dev/null || {
    echo -e "${RED}Deployment not found${NC}"
    return 1
  }

  # 2. Replica status
  echo -e "\n${BLUE}2️⃣  Replica Sets${NC}"
  kubectl get replicasets -n "$namespace" \
    -l app="$deployment_name" \
    -o wide 2>/dev/null

  # 3. Pod status for this deployment
  echo -e "\n${BLUE}3️⃣  Pods for this Deployment${NC}"
  kubectl get pods -n "$namespace" \
    -l app="$deployment_name" \
    -o wide 2>/dev/null

  # 4. Recent events
  echo -e "\n${BLUE}4️⃣  Recent Events${NC}"
  kubectl get events -n "$namespace" \
    --field-selector involvedObject.name="$deployment_name" \
    --sort-by='.lastTimestamp' 2>/dev/null | tail -10 || echo "No recent events"

  # 5. Failed pods (if any)
  echo -e "\n${BLUE}5️⃣  Failed Pods (if any)${NC}"
  local failed_pods=$(kubectl get pods -n "$namespace" \
    -l app="$deployment_name" \
    --field-selector=status.phase!=Running \
    -o jsonpath='{.items[*].metadata.name}' 2>/dev/null)

  if [ -n "$failed_pods" ]; then
    for pod in $failed_pods; do
      echo -e "${RED}  • $pod${NC}"
      diagnose_pod "$pod" "$namespace"
    done
  else
    echo -e "${GREEN}All pods running${NC}"
  fi

  log_event "deployment_diagnosed" "complete" "Deployment: $deployment_name"
}

# Diagnose all services in namespace
diagnose_namespace() {
  local namespace=${1:-$NAMESPACE}

  echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🔍 Diagnosing Namespace: $namespace${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}\n"

  # 1. Deployments status
  echo -e "${BLUE}1️⃣  Deployments${NC}"
  kubectl get deployments -n "$namespace" -o wide 2>/dev/null || echo "No deployments"

  # 2. Services
  echo -e "\n${BLUE}2️⃣  Services${NC}"
  kubectl get services -n "$namespace" -o wide 2>/dev/null || echo "No services"

  # 3. Pod summary
  echo -e "\n${BLUE}3️⃣  Pods Summary${NC}"
  kubectl get pods -n "$namespace" -o wide 2>/dev/null || echo "No pods"

  # 4. PVC status
  echo -e "\n${BLUE}4️⃣  PersistentVolumeClaims${NC}"
  kubectl get pvc -n "$namespace" 2>/dev/null || echo "No PVCs"

  # 5. Recent events
  echo -e "\n${BLUE}5️⃣  Recent Namespace Events${NC}"
  kubectl get events -n "$namespace" \
    --sort-by='.lastTimestamp' 2>/dev/null | tail -20 || echo "No events"

  # 6. Resource utilization
  echo -e "\n${BLUE}6️⃣  Resource Utilization${NC}"
  kubectl top nodes 2>/dev/null || echo "Metrics unavailable"

  log_event "namespace_diagnosed" "complete" "Namespace: $namespace"
}

# Monitor pod logs in real-time
monitor_logs() {
  local pod_name=$1
  local namespace=${2:-$NAMESPACE}
  local follow=${3:-true}

  echo -e "${CYAN}📋 Streaming logs for $pod_name${NC}"
  echo -e "${YELLOW}(Press Ctrl+C to stop)${NC}\n"

  if [ "$follow" = "true" ]; then
    kubectl logs "$pod_name" -n "$namespace" -f 2>/dev/null || {
      echo -e "${RED}Cannot stream logs, trying one-time read...${NC}"
      kubectl logs "$pod_name" -n "$namespace" --tail=100 2>/dev/null
    }
  else
    kubectl logs "$pod_name" -n "$namespace" --tail=100 2>/dev/null
  fi
}

# Check service connectivity
check_service_connectivity() {
  local service_name=$1
  local namespace=${2:-$NAMESPACE}

  echo -e "\n${CYAN}═══════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🔗 Checking Service Connectivity: $service_name${NC}"
  echo -e "${CYAN}═══════════════════════════════════════════════════════════${NC}\n"

  # 1. Service details
  echo -e "${BLUE}1️⃣  Service Details${NC}"
  kubectl get service "$service_name" -n "$namespace" 2>/dev/null || {
    echo -e "${RED}Service not found${NC}"
    return 1
  }

  # 2. Endpoints
  echo -e "\n${BLUE}2️⃣  Endpoints${NC}"
  kubectl get endpoints "$service_name" -n "$namespace" 2>/dev/null || echo "No endpoints"

  # 3. Backing pods
  echo -e "\n${BLUE}3️⃣  Backing Pods${NC}"
  local selector=$(kubectl get service "$service_name" -n "$namespace" \
    -o jsonpath='{.spec.selector.app}' 2>/dev/null)

  if [ -n "$selector" ]; then
    kubectl get pods -n "$namespace" -l app="$selector" -o wide 2>/dev/null
  else
    echo "Could not determine backing pods"
  fi

  log_event "service_checked" "complete" "Service: $service_name"
}

# Main entry point
main() {
  local command=${1:-}
  local target=${2:-}
  local namespace=${3:-$NAMESPACE}

  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}K8s Debugging Agent (Read-Only)${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  check_k8s_connectivity || exit 1

  case "$command" in
    "pod")
      diagnose_pod "$target" "$namespace"
      ;;
    "deployment")
      diagnose_deployment "$target" "$namespace"
      ;;
    "namespace")
      diagnose_namespace "$target"
      ;;
    "logs")
      monitor_logs "$target" "$namespace"
      ;;
    "service")
      check_service_connectivity "$target" "$namespace"
      ;;
    "all")
      diagnose_namespace "$namespace"
      ;;
    *)
      echo -e "${CYAN}Usage: $0 <command> [target] [namespace]${NC}"
      echo -e "\nCommands:"
      echo -e "  pod <name>         - Diagnose specific pod"
      echo -e "  deployment <name>  - Diagnose deployment"
      echo -e "  namespace [ns]     - Diagnose entire namespace"
      echo -e "  logs <pod>         - Stream pod logs"
      echo -e "  service <name>     - Check service connectivity"
      echo -e "  all                - Full namespace diagnosis"
      echo -e "\nExample: $0 pod ai-engine-7d9f7c5b8 trading-saas"
      exit 1
      ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  main "$@"
fi
