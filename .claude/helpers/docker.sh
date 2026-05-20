#!/bin/bash
# Docker / GHCR helpers: build, push, image existence check
# Source this file; do not execute directly.

HELPERS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$HELPERS_DIR/common.sh"

GHCR_REGISTRY="${GHCR_REGISTRY:-ghcr.io/Jfdz}"

# Check whether <service>:<tag> exists in GHCR using the gh API.
# Returns 0 if found, 1 if not found.
check_image_in_ghcr() {
  local service=$1
  local tag=$2

  echo -e "${BLUE}🔍 Checking GHCR: $GHCR_REGISTRY/$service:$tag${NC}"

  # packages API: list versions for the container package
  local found
  found=$(gh api \
    "orgs/Jfdz/packages/container/$service/versions" \
    --jq ".[] | .metadata.container.tags[] | select(. == \"$tag\")" \
    2>/dev/null | head -1 || true)

  if [ -n "$found" ]; then
    echo -e "${GREEN}✅ Image found${NC}"
    return 0
  fi

  # Fallback: try user-scoped packages endpoint
  found=$(gh api \
    "user/packages/container/$service/versions" \
    --jq ".[] | .metadata.container.tags[] | select(. == \"$tag\")" \
    2>/dev/null | head -1 || true)

  if [ -n "$found" ]; then
    echo -e "${GREEN}✅ Image found (user scope)${NC}"
    return 0
  fi

  echo -e "${RED}❌ Image not found: $GHCR_REGISTRY/$service:$tag${NC}" >&2
  return 1
}

# Build a Docker image and tag it for GHCR.
# Usage: docker_build <service> <tag> [dockerfile] [context_dir]
docker_build() {
  local service=$1
  local tag=$2
  local dockerfile="${3:-Dockerfile}"
  local ctx="${4:-.}"
  local image="$GHCR_REGISTRY/$service:$tag"

  echo -e "${BLUE}🐳 Building $image${NC}"
  docker build -t "$image" -f "$dockerfile" "$ctx"
}

# Push a previously built image to GHCR.
# Usage: docker_push <service> <tag>
docker_push() {
  local service=$1
  local tag=$2
  local image="$GHCR_REGISTRY/$service:$tag"

  echo -e "${BLUE}📤 Pushing $image${NC}"

  # Authenticate if not already logged in
  if ! docker info 2>/dev/null | grep -q "ghcr.io"; then
    echo -e "${CYAN}Authenticating with GHCR...${NC}"
    gh auth token | docker login ghcr.io -u "$(gh api user --jq .login)" --password-stdin
  fi

  docker push "$image"
  echo -e "${GREEN}✅ Pushed $image${NC}"
}

# Convenience: build + push in one call.
docker_build_and_push() {
  local service=$1
  local tag=$2
  local dockerfile="${3:-Dockerfile}"
  local ctx="${4:-.}"
  docker_build "$service" "$tag" "$dockerfile" "$ctx"
  docker_push "$service" "$tag"
}
