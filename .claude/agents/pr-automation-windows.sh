#!/bin/bash
# PR Automation Simplified para Windows
# Sin --json flag (compatible con versiones viejas de gh)

set -euo pipefail

REPO_OWNER="Jfdz"
REPO_NAME="TradeMindAI"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}🚀 PR Automation (Windows Compatible)${NC}"
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

BRANCH=$(git rev-parse --abbrev-ref HEAD)

echo -e "${CYAN}Branch: $BRANCH${NC}\n"

if [ "$BRANCH" = "main" ] || [ "$BRANCH" = "develop" ]; then
  echo -e "${YELLOW}Main/develop branch - skipping PR creation${NC}"
  exit 0
fi

# Get existing PR or create new
echo -e "${BLUE}1️⃣  Buscando PR existente o creando nueva...${NC}"

PR_LIST=$(gh pr list --head "$BRANCH" --base develop 2>/dev/null || echo "")

if [ -n "$PR_LIST" ]; then
  # Extract PR number from simple list output
  PR_NUMBER=$(echo "$PR_LIST" | head -1 | awk '{print $1}' | grep -oP '#\K[0-9]+' || echo "")

  if [ -z "$PR_NUMBER" ]; then
    # Try alternative parsing
    PR_NUMBER=$(echo "$PR_LIST" | head -1 | cut -d' ' -f1 | sed 's/#//')
  fi

  if [ -n "$PR_NUMBER" ]; then
    echo -e "${GREEN}✅ PR encontrada: #$PR_NUMBER${NC}"
    echo -e "${CYAN}   https://github.com/$REPO_OWNER/$REPO_NAME/pull/$PR_NUMBER${NC}"
  fi
else
  # Create new PR
  echo -e "${CYAN}Creando PR nueva...${NC}"

  TITLE="feat: $(git log -1 --pretty=%s)"

  if gh pr create --title "$TITLE" --base develop; then
    echo -e "${GREEN}✅ PR creada${NC}"

    # Get new PR number
    sleep 1
    PR_LIST=$(gh pr list --head "$BRANCH" --base develop 2>/dev/null || echo "")
    PR_NUMBER=$(echo "$PR_LIST" | head -1 | cut -d' ' -f1 | sed 's/#//')

    echo -e "${CYAN}   https://github.com/$REPO_OWNER/$REPO_NAME/pull/$PR_NUMBER${NC}"
  else
    echo -e "${RED}❌ Failed to create PR${NC}"
    exit 1
  fi
fi

if [ -z "$PR_NUMBER" ]; then
  echo -e "${RED}❌ Could not determine PR number${NC}"
  exit 1
fi

# Monitor PR
echo -e "\n${BLUE}2️⃣  Monitoreando tests en GitHub Actions...${NC}"
echo -e "${CYAN}   PR: #$PR_NUMBER${NC}\n"

MAX_WAIT=1200  # 20 minutos
ELAPSED=0
CHECK_INTERVAL=30

while [ $ELAPSED -lt $MAX_WAIT ]; do
  # Check PR status
  PR_STATUS=$(gh pr view "$PR_NUMBER" 2>/dev/null | grep -i "status\|check" | head -5 || echo "pending")

  if echo "$PR_STATUS" | grep -q "All checks passed\|✓"; then
    echo -e "\n${GREEN}✅ All checks PASSED${NC}"
    break
  elif echo "$PR_STATUS" | grep -q "Some checks failed\|✗"; then
    echo -e "\n${RED}❌ Some checks FAILED${NC}"
    exit 1
  fi

  printf "\r${CYAN}⏳ Esperando checks... ${ELAPSED}s/${MAX_WAIT}s${NC}"

  sleep $CHECK_INTERVAL
  ELAPSED=$((ELAPSED + CHECK_INTERVAL))
done

if [ $ELAPSED -ge $MAX_WAIT ]; then
  echo -e "\n${YELLOW}⚠️  Timeout esperando checks${NC}"
  exit 1
fi

# Merge PR
echo -e "\n${BLUE}3️⃣  Mergeando PR #$PR_NUMBER a develop...${NC}"

if gh pr merge "$PR_NUMBER" --squash --delete-branch; then
  echo -e "${GREEN}✅ PR #$PR_NUMBER mergeada a develop${NC}"
else
  echo -e "${YELLOW}⚠️  Could not auto-merge${NC}"
  echo -e "${CYAN}   Merging manualmente en GitHub${NC}"
  exit 0
fi

# Create PR to main
echo -e "\n${BLUE}4️⃣  Creando PR de develop → main...${NC}"

if gh pr create --title "Release: develop → main" --base main; then
  echo -e "${GREEN}✅ PR creada: develop → main${NC}"

  # Get main PR
  sleep 1
  MAIN_PR=$(gh pr list --head develop --base main 2>/dev/null | head -1 | cut -d' ' -f1 | sed 's/#//')

  if [ -n "$MAIN_PR" ]; then
    echo -e "${CYAN}   https://github.com/$REPO_OWNER/$REPO_NAME/pull/$MAIN_PR${NC}"

    # Monitor main PR
    echo -e "\n${BLUE}5️⃣  Monitoreando release-orchestrator...${NC}"

    # Wait for checks
    ELAPSED=0
    while [ $ELAPSED -lt $MAX_WAIT ]; do
      MAIN_STATUS=$(gh pr view "$MAIN_PR" 2>/dev/null | grep -i "status\|check" | head -5 || echo "pending")

      if echo "$MAIN_STATUS" | grep -q "All checks passed\|✓"; then
        echo -e "\n${GREEN}✅ Release checks PASSED${NC}"
        break
      elif echo "$MAIN_STATUS" | grep -q "Some checks failed\|✗"; then
        echo -e "\n${RED}❌ Release checks FAILED${NC}"
        exit 1
      fi

      printf "\r${CYAN}⏳ Esperando release checks... ${ELAPSED}s/${MAX_WAIT}s${NC}"

      sleep $CHECK_INTERVAL
      ELAPSED=$((ELAPSED + CHECK_INTERVAL))
    done

    # Merge to main
    echo -e "\n${BLUE}6️⃣  Mergeando PR #$MAIN_PR a main...${NC}"

    if gh pr merge "$MAIN_PR" --merge; then
      echo -e "${GREEN}✅ PR #$MAIN_PR mergeada a main${NC}"
      echo -e "${MAGENTA}🚀 release-orchestrator.yml se disparará automáticamente${NC}"
    fi
  fi
else
  echo -e "${YELLOW}⚠️  Could not create PR to main${NC}"
  exit 0
fi

echo -e "\n${MAGENTA}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ AUTOMATION COMPLETE${NC}"
echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"
