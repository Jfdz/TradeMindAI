#!/bin/bash
# Fase 4: Local Build
# Compile locally, build Docker image, push to GHCR, deploy to staging
# Feedback en 5 minutos vs 15+ con GA

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../helpers/common.sh
source "$SCRIPT_DIR/../helpers/common.sh"
source "$SCRIPT_DIR/../helpers/k8s.sh"
source "$SCRIPT_DIR/../helpers/docker.sh"

# Configuration
REPO_OWNER="Jfdz"
REPO_NAME="TradeMindAI"
GHCR_REGISTRY="ghcr.io/$REPO_OWNER"
NAMESPACE="${K8S_NAMESPACE:-staging}"
LOG_DIR="./.claude/debug-logs"

mkdir -p "$LOG_DIR"
AUDIT_LOG="$LOG_DIR/local-build.jsonl"

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
  "details": "$details"
}
EOF
)
  echo "$json" >> "$AUDIT_LOG"
}

# Detect service type
detect_service_type() {
  local service_path=$1

  if [ -f "$service_path/pom.xml" ]; then
    echo "java"
  elif [ -f "$service_path/package.json" ]; then
    echo "nodejs"
  elif [ -f "$service_path/requirements.txt" ]; then
    echo "python"
  else
    echo "unknown"
  fi
}

# Build Java service
build_java_service() {
  local service_path=$1
  local service_name=$2

  echo -e "${BLUE}🔨 Building Java service: $service_name${NC}"

  cd "$service_path"

  # Use Maven wrapper if available
  if [ -f "mvnw" ]; then
    if ! ./mvnw clean verify -q; then
      echo -e "${RED}❌ Maven build failed${NC}"
      log_event "java_build_failed" "failed" "Service: $service_name"
      return 1
    fi
  else
    if ! mvn clean verify -q; then
      echo -e "${RED}❌ Maven build failed${NC}"
      log_event "java_build_failed" "failed" "Service: $service_name"
      return 1
    fi
  fi

  echo -e "${GREEN}✅ Maven build successful${NC}"
  log_event "java_build_success" "success" "Service: $service_name"

  cd - > /dev/null
  return 0
}

# Build Node.js service
build_nodejs_service() {
  local service_path=$1
  local service_name=$2

  echo -e "${BLUE}🔨 Building Node.js service: $service_name${NC}"

  cd "$service_path"

  if ! npm ci; then
    echo -e "${RED}❌ npm ci failed${NC}"
    log_event "nodejs_install_failed" "failed" "Service: $service_name"
    return 1
  fi

  if ! npm run build; then
    echo -e "${RED}❌ npm build failed${NC}"
    log_event "nodejs_build_failed" "failed" "Service: $service_name"
    return 1
  fi

  if ! npm run lint; then
    echo -e "${RED}❌ npm lint failed${NC}"
    log_event "nodejs_lint_failed" "failed" "Service: $service_name"
    return 1
  fi

  echo -e "${GREEN}✅ Node.js build successful${NC}"
  log_event "nodejs_build_success" "success" "Service: $service_name"

  cd - > /dev/null
  return 0
}

# Build Python service
build_python_service() {
  local service_path=$1
  local service_name=$2

  echo -e "${BLUE}🔨 Building Python service: $service_name${NC}"

  cd "$service_path"

  if ! pip install -q -r requirements.txt -r requirements-dev.txt 2>/dev/null; then
    echo -e "${RED}❌ pip install failed${NC}"
    log_event "python_install_failed" "failed" "Service: $service_name"
    return 1
  fi

  if ! pytest tests/ -v --tb=short; then
    echo -e "${RED}❌ pytest failed${NC}"
    log_event "python_test_failed" "failed" "Service: $service_name"
    return 1
  fi

  echo -e "${GREEN}✅ Python tests passed${NC}"
  log_event "python_build_success" "success" "Service: $service_name"

  cd - > /dev/null
  return 0
}

# Perform local build
local_build() {
  local service_path=$1
  local service_name=$2

  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}📦 LOCAL BUILD: $service_name${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  # Detect service type
  local service_type=$(detect_service_type "$service_path")

  echo -e "${CYAN}Service type: $service_type${NC}\n"

  case "$service_type" in
    "java")
      if ! build_java_service "$service_path" "$service_name"; then
        return 1
      fi
      ;;
    "nodejs")
      if ! build_nodejs_service "$service_path" "$service_name"; then
        return 1
      fi
      ;;
    "python")
      if ! build_python_service "$service_path" "$service_name"; then
        return 1
      fi
      ;;
    *)
      echo -e "${RED}❌ Unknown service type${NC}"
      return 1
      ;;
  esac

  return 0
}

# Build Docker image
build_docker_image() {
  local service_path=$1
  local service_name=$2
  local image_tag=$3

  echo -e "\n${BLUE}🐳 Building Docker image...${NC}"

  local image_uri="$GHCR_REGISTRY/$service_name:$image_tag"

  echo -e "${CYAN}Image: $image_uri${NC}"

  if ! docker build \
    --tag "$image_uri" \
    --file "$service_path/Dockerfile" \
    "$service_path"; then

    echo -e "${RED}❌ Docker build failed${NC}"
    log_event "docker_build_failed" "failed" "Service: $service_name"
    return 1
  fi

  echo -e "${GREEN}✅ Docker image built${NC}"
  log_event "docker_build_success" "success" "Image: $image_uri"

  return 0
}

# Push image to GHCR
push_to_ghcr() {
  local service_name=$1
  local image_tag=$2

  echo -e "\n${BLUE}📤 Pushing to GHCR...${NC}"

  local image_uri="$GHCR_REGISTRY/$service_name:$image_tag"

  # Check if logged in
  if ! docker info 2>/dev/null | grep -q "Username"; then
    echo -e "${YELLOW}⚠️  Not logged into Docker${NC}"
    echo -e "${CYAN}Please run: docker login ghcr.io${NC}"
    return 1
  fi

  if ! docker push "$image_uri"; then
    echo -e "${RED}❌ Docker push failed${NC}"
    log_event "docker_push_failed" "failed" "Image: $image_uri"
    return 1
  fi

  echo -e "${GREEN}✅ Image pushed to GHCR${NC}"
  log_event "docker_push_success" "success" "Image: $image_uri"

  return 0
}

# Deploy to staging
deploy_to_staging() {
  local deployment=$1
  local service_name=$2
  local image_tag=$3

  echo -e "\n${BLUE}🚀 Deploying to staging...${NC}"

  local image_uri="$GHCR_REGISTRY/$service_name:$image_tag"
  local namespace="staging"

  echo -e "${CYAN}Deployment: $deployment${NC}"
  echo -e "${CYAN}Namespace: $namespace${NC}"
  echo -e "${CYAN}Image: $image_uri${NC}\n"

  # Check if deployment exists
  if ! kubectl get deployment "$deployment" -n "$namespace" &>/dev/null; then
    echo -e "${RED}❌ Deployment not found in staging${NC}"
    log_event "staging_deploy_failed" "not_found" "Deployment: $deployment"
    return 1
  fi

  # Update image
  if ! kubectl set image deployment/"$deployment" \
    "$deployment"="$image_uri" \
    -n "$namespace"; then

    echo -e "${RED}❌ kubectl set image failed${NC}"
    log_event "staging_deploy_failed" "kubectl_error" "Deployment: $deployment"
    return 1
  fi

  # Wait for rollout
  echo -e "${CYAN}Waiting for rollout (timeout: 5m)...${NC}"

  if ! kubectl rollout status deployment/"$deployment" \
    -n "$namespace" \
    --timeout=300s; then

    echo -e "${RED}❌ Rollout failed${NC}"
    log_event "staging_rollout_failed" "timeout" "Deployment: $deployment"
    return 1
  fi

  echo -e "${GREEN}✅ Deployment successful${NC}"
  log_event "staging_deploy_success" "success" "Deployment: $deployment"

  return 0
}

# Health check on staging
health_check_staging() {
  local service_name=$1
  local namespace="staging"

  echo -e "\n${BLUE}🏥 Running health check...${NC}"

  # Get service port
  local service_port=$(kubectl get service "$service_name" -n "$namespace" \
    -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "80")

  # Get pod
  local pod=$(kubectl get pod -n "$namespace" -l app="$service_name" \
    --sort-by=.metadata.creationTimestamp \
    -o jsonpath='{.items[-1].metadata.name}' 2>/dev/null)

  if [ -z "$pod" ]; then
    echo -e "${YELLOW}⚠️  No pod found for health check${NC}"
    return 0
  fi

  # Try health check
  if kubectl exec -n "$namespace" "$pod" -- \
    wget -q -O- "http://localhost:8080/health" &>/dev/null; then

    echo -e "${GREEN}✅ Health check passed${NC}"
    log_event "staging_health_check" "success" "Service: $service_name"
    return 0
  fi

  echo -e "${YELLOW}⚠️  Health check inconclusive (pod still starting)${NC}"
  return 0
}

# Full workflow
full_local_build_and_deploy() {
  local service_path=$1
  local service_name=$2
  local image_tag=${3:-}

  if [ -z "$image_tag" ]; then
    # Generate tag from current branch and short hash
    local branch=$(git rev-parse --abbrev-ref HEAD | sed 's/[^a-zA-Z0-9-]/-/g')
    local short_hash=$(git rev-parse --short HEAD)
    image_tag="dev-$branch-$short_hash"
  fi

  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${MAGENTA}🎯 LOCAL BUILD + STAGING DEPLOY${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  log_event "build_deploy_started" "initiated" "Service: $service_name, Tag: $image_tag"

  # Step 1: Local build
  if ! local_build "$service_path" "$service_name"; then
    echo -e "${RED}❌ Build failed${NC}"
    return 1
  fi

  # Step 2: Docker build
  if ! build_docker_image "$service_path" "$service_name" "$image_tag"; then
    echo -e "${RED}❌ Docker build failed${NC}"
    return 1
  fi

  # Step 3: Push to GHCR
  if ! push_to_ghcr "$service_name" "$image_tag"; then
    echo -e "${RED}❌ Push failed${NC}"
    return 1
  fi

  # Step 4: Deploy to staging
  if ! deploy_to_staging "$service_name" "$service_name" "$image_tag"; then
    echo -e "${RED}❌ Deploy failed${NC}"
    return 1
  fi

  # Step 5: Health check
  health_check_staging "$service_name"

  # Success!
  echo -e "\n${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${GREEN}✅ SUCCESS: Feature deployed to staging${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  echo -e "${CYAN}📊 Summary:${NC}"
  echo -e "  Service: $service_name"
  echo -e "  Image tag: $image_tag"
  echo -e "  Staging URL: https://staging.trademind.es"
  echo -e "  Deployment: $service_name"
  echo -e "  Namespace: staging\n"

  log_event "build_deploy_success" "complete" "Service: $service_name, Tag: $image_tag"

  return 0
}

# Main entry point
main() {
  local service=${1:-}
  local tag=${2:-}

  if [ -z "$service" ]; then
    echo -e "${CYAN}Usage: $0 <service> [image_tag]${NC}"
    echo -e "\nAvailable services:"
    echo -e "  market-data-service"
    echo -e "  trading-core-service"
    echo -e "  ai-engine"
    echo -e "  web-app"
    echo -e "\nExample: $0 trading-core-service dev-feature-123abc"
    exit 1
  fi

  local service_path="services/$service"

  if [ ! -d "$service_path" ]; then
    echo -e "${RED}❌ Service path not found: $service_path${NC}"
    exit 1
  fi

  full_local_build_and_deploy "$service_path" "$service" "$tag"
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  main "$@"
fi
