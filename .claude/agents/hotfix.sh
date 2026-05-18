#!/bin/bash
# Fase 3: Hotfix Support
# Hotfixes con validaciones obligatorias, health checks, auto-rollback

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# Configuration
REPO_OWNER="Jfdz"
REPO_NAME="TradeMindAI"
NAMESPACE="${K8S_NAMESPACE:-trading-saas}"
GHCR_REGISTRY="ghcr.io/$REPO_OWNER"
LOG_DIR="./.claude/debug-logs"
ROLLOUT_TIMEOUT=300  # 5 minutes

mkdir -p "$LOG_DIR"
AUDIT_LOG="$LOG_DIR/hotfix.jsonl"
ROLLBACK_LOG="$LOG_DIR/rollback.jsonl"

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

# Validation 1: Check if image exists in GHCR
validate_image_exists() {
  local service=$1
  local image_tag=$2

  echo -e "${BLUE}1️⃣  Validating image exists in GHCR...${NC}"

  local image_uri="$GHCR_REGISTRY/$service:$image_tag"

  if gh api \
    "/repos/$REPO_OWNER/$REPO_NAME/container/images" \
    --jq '.[] | select(.name == "'$service'") | .versions[] | select(.tags[] | select(. == "'$image_tag'"))' \
    &>/dev/null; then

    echo -e "${GREEN}✅ Image found: $image_uri${NC}"
    log_event "image_validation" "passed" "Image: $image_uri"
    return 0
  else
    echo -e "${RED}❌ Image NOT found: $image_uri${NC}"
    log_event "image_validation" "failed" "Image not found: $image_uri"
    return 1
  fi
}

# Validation 2: Dry-run deployment
validate_dry_run() {
  local deployment=$1
  local namespace=$2
  local image=$3

  echo -e "\n${BLUE}2️⃣  Running dry-run...${NC}"

  if kubectl set image deployment/"$deployment" \
    "$deployment"="$image" \
    -n "$namespace" \
    --dry-run=client \
    -o yaml &>/dev/null; then

    echo -e "${GREEN}✅ Dry-run successful${NC}"
    log_event "dry_run_validation" "passed" "Deployment: $deployment"
    return 0
  else
    echo -e "${RED}❌ Dry-run failed${NC}"
    log_event "dry_run_validation" "failed" "Deployment: $deployment"
    return 1
  fi
}

# Validation 3: Health check endpoint
validate_health_check() {
  local service=$1
  local namespace=$2
  local health_endpoint=$3

  echo -e "\n${BLUE}3️⃣  Setting up health check...${NC}"

  # Port-forward to service
  local service_port=$(kubectl get service "$service" -n "$namespace" \
    -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "80")

  # Get a running pod
  local pod=$(kubectl get pod -n "$namespace" -l app="$service" \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

  if [ -z "$pod" ]; then
    echo -e "${YELLOW}⚠️  No running pod found for health check${NC}"
    return 0
  fi

  echo -e "${CYAN}Health endpoint: $health_endpoint:$service_port${NC}"
  # Health check will be done post-deployment
  return 0
}

# Perform health check post-deployment
post_deployment_health_check() {
  local deployment=$1
  local namespace=$2
  local health_endpoint=${3:-/health}

  echo -e "\n${BLUE}🏥 Running post-deployment health checks...${NC}"

  local max_attempts=10
  local attempt=1
  local wait_seconds=5

  # Get a pod for this deployment
  local pod=$(kubectl get pod -n "$namespace" -l app="$deployment" \
    --sort-by=.metadata.creationTimestamp \
    -o jsonpath='{.items[-1].metadata.name}' 2>/dev/null)

  if [ -z "$pod" ]; then
    echo -e "${RED}❌ No pod found for health check${NC}"
    return 1
  fi

  echo -e "${CYAN}Testing pod: $pod${NC}"

  while [ $attempt -le $max_attempts ]; do
    echo -ne "\r${YELLOW}⏳ Health check attempt $attempt/$max_attempts${NC}"

    # Try to curl health endpoint within pod
    if kubectl exec -n "$namespace" "$pod" -- \
      wget -q -O- "http://localhost:8080$health_endpoint" &>/dev/null; then

      echo -e "\n${GREEN}✅ Health check PASSED${NC}"
      log_event "health_check" "passed" "Pod: $pod"
      return 0
    fi

    if [ $attempt -lt $max_attempts ]; then
      sleep $wait_seconds
    fi
    ((attempt++))
  done

  echo -e "\n${RED}❌ Health check FAILED after $max_attempts attempts${NC}"
  log_event "health_check" "failed" "Pod: $pod"
  return 1
}

# Perform hotfix deployment
perform_hotfix_deployment() {
  local deployment=$1
  local namespace=$2
  local new_image=$3

  echo -e "\n${MAGENTA}🚀 Performing deployment...${NC}"

  # Update image
  if kubectl set image deployment/"$deployment" \
    "$deployment"="$new_image" \
    -n "$namespace"; then

    echo -e "${GREEN}✅ Image updated${NC}"
    log_event "image_updated" "success" "Deployment: $deployment, Image: $new_image"
  else
    echo -e "${RED}❌ Failed to update image${NC}"
    log_event "image_update_failed" "failed" "Deployment: $deployment"
    return 1
  fi

  # Wait for rollout
  echo -e "${CYAN}Waiting for rollout (timeout: ${ROLLOUT_TIMEOUT}s)...${NC}"

  if kubectl rollout status deployment/"$deployment" \
    -n "$namespace" \
    --timeout="${ROLLOUT_TIMEOUT}s"; then

    echo -e "${GREEN}✅ Rollout completed${NC}"
    log_event "rollout_success" "success" "Deployment: $deployment"
    return 0
  else
    echo -e "${RED}❌ Rollout failed${NC}"
    log_event "rollout_failed" "failed" "Deployment: $deployment"
    return 1
  fi
}

# Automatic rollback
perform_rollback() {
  local deployment=$1
  local namespace=$2

  echo -e "\n${RED}⚠️  ROLLING BACK...${NC}"

  if kubectl rollout undo deployment/"$deployment" -n "$namespace"; then
    echo -e "${GREEN}✅ Rollback initiated${NC}"

    # Wait for rollback
    if kubectl rollout status deployment/"$deployment" \
      -n "$namespace" \
      --timeout="${ROLLOUT_TIMEOUT}s"; then

      echo -e "${GREEN}✅ Rollback completed${NC}"
      echo -e "${YELLOW}Previous version is now live${NC}"

      log_event "rollback_success" "success" "Deployment: $deployment rolled back"
      return 0
    fi
  fi

  echo -e "${RED}❌ Rollback failed${NC}"
  log_event "rollback_failed" "failed" "Deployment: $deployment"
  return 1
}

# Send Slack notification
send_slack_notification() {
  local message=$1
  local status=${2:-info}

  if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
    return 0  # Skip if no webhook configured
  fi

  local color="good"
  case "$status" in
    "error") color="danger" ;;
    "warning") color="warning" ;;
  esac

  local payload=$(cat <<EOF
{
  "attachments": [
    {
      "color": "$color",
      "title": "Hotfix Alert",
      "text": "$message",
      "fields": [
        {
          "title": "Namespace",
          "value": "$NAMESPACE",
          "short": true
        },
        {
          "title": "Timestamp",
          "value": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
          "short": true
        }
      ]
    }
  ]
}
EOF
)

  curl -X POST -H 'Content-type: application/json' \
    --data "$payload" \
    "$SLACK_WEBHOOK_URL" 2>/dev/null || true
}

# Full hotfix workflow
perform_full_hotfix() {
  local deployment=$1
  local service=$2
  local new_version=$3
  local namespace=${4:-$NAMESPACE}

  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🔥 HOTFIX WORKFLOW${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  echo -e "${CYAN}Target: $deployment (namespace: $namespace)${NC}"
  echo -e "${CYAN}New version: $new_version${NC}\n"

  log_event "hotfix_started" "initiated" "Deployment: $deployment, Version: $new_version"
  send_slack_notification "🔥 Hotfix initiated: $deployment → $new_version" "warning"

  # Validation 1: Image exists
  if ! validate_image_exists "$service" "$new_version"; then
    echo -e "${RED}❌ Hotfix aborted: Image validation failed${NC}"
    send_slack_notification "❌ Hotfix failed: Image not found in GHCR" "error"
    return 1
  fi

  # Validation 2: Dry-run
  if ! validate_dry_run "$deployment" "$namespace" "$GHCR_REGISTRY/$service:$new_version"; then
    echo -e "${RED}❌ Hotfix aborted: Dry-run failed${NC}"
    send_slack_notification "❌ Hotfix failed: Dry-run validation failed" "error"
    return 1
  fi

  # Validation 3: Health check setup
  validate_health_check "$service" "$namespace" "/health"

  # Perform deployment
  if ! perform_hotfix_deployment "$deployment" "$namespace" "$GHCR_REGISTRY/$service:$new_version"; then
    echo -e "${RED}❌ Deployment failed, performing automatic rollback${NC}"
    send_slack_notification "❌ Deployment failed for $deployment, rolling back..." "error"

    if perform_rollback "$deployment" "$namespace"; then
      echo -e "${YELLOW}Previous version restored${NC}"
      send_slack_notification "✅ Rollback successful for $deployment" "warning"
    fi
    return 1
  fi

  # Post-deployment health check
  if ! post_deployment_health_check "$deployment" "$namespace"; then
    echo -e "${RED}❌ Health check failed, performing automatic rollback${NC}"
    send_slack_notification "❌ Health check failed for $deployment, rolling back..." "error"

    if perform_rollback "$deployment" "$namespace"; then
      echo -e "${YELLOW}Previous version restored${NC}"
      send_slack_notification "✅ Rollback successful for $deployment" "warning"
    fi
    return 1
  fi

  # Success!
  echo -e "\n${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${GREEN}✅ HOTFIX SUCCESSFUL${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  log_event "hotfix_success" "complete" "Deployment: $deployment is now running $new_version"
  send_slack_notification "✅ Hotfix successful: $deployment now running $new_version" "good"

  return 0
}

# Main entry point
main() {
  local command=${1:-}
  local deployment=${2:-}
  local service=${3:-}
  local version=${4:-}

  case "$command" in
    "deploy"|"hotfix")
      if [ -z "$deployment" ] || [ -z "$service" ] || [ -z "$version" ]; then
        echo -e "${RED}Usage: $0 hotfix <deployment> <service> <version>${NC}"
        echo -e "Example: $0 hotfix trading-core trading-core-service v1.2.1${NC}"
        exit 1
      fi
      perform_full_hotfix "$deployment" "$service" "$version"
      ;;
    "rollback")
      if [ -z "$deployment" ]; then
        echo -e "${RED}Usage: $0 rollback <deployment>${NC}"
        exit 1
      fi
      perform_rollback "$deployment" "$NAMESPACE"
      ;;
    "validate-image")
      if [ -z "$service" ] || [ -z "$version" ]; then
        echo -e "${RED}Usage: $0 validate-image <service> <version>${NC}"
        exit 1
      fi
      validate_image_exists "$service" "$version"
      ;;
    *)
      echo -e "${CYAN}Usage: $0 <command> [args]${NC}"
      echo -e "\nCommands:"
      echo -e "  hotfix <deployment> <service> <version>   - Full hotfix workflow"
      echo -e "  rollback <deployment>                      - Rollback to previous version"
      echo -e "  validate-image <service> <version>         - Check if image exists"
      exit 1
      ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  main "$@"
fi
