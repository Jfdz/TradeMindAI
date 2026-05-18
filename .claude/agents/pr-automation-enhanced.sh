#!/bin/bash
# Fase 1 (Continuación): PR Automation Enhanced
# Crea PRs automáticamente, monitorea tests, mergea cuando pasan

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../helpers/common.sh
source "$SCRIPT_DIR/../helpers/common.sh"

# Configuration
REPO_OWNER="Jfdz"
REPO_NAME="TradeMindAI"
LOG_DIR="./.claude/debug-logs"

mkdir -p "$LOG_DIR"
AUDIT_LOG="$LOG_DIR/pr-automation.jsonl"

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
  "session_id": "${SESSION_ID:-unknown}"
}
EOF
)
  echo "$json" >> "$AUDIT_LOG"
}

# Get last commit message
get_last_commit_message() {
  git log -1 --pretty=%B
}

# Get last commit title
get_last_commit_title() {
  git log -1 --pretty=%s
}

# Generate PR title from commits
generate_pr_title() {
  local base_branch=$1
  local feature_branch=$2

  # Count commits
  local commit_count=$(git rev-list --count "$base_branch".."$feature_branch")

  # Get first commit message
  local first_commit=$(git log "$base_branch".."$feature_branch" --pretty=%s | tail -1)

  if [ "$commit_count" -eq 1 ]; then
    echo "$first_commit"
  else
    echo "$first_commit (+ $((commit_count - 1)) more)"
  fi
}

# Generate PR body from commits
generate_pr_body() {
  local base_branch=$1
  local feature_branch=$2

  cat <<'EOF'
## 📝 Descripción

EOF

  # First commit message
  git log "$base_branch".."$feature_branch" --pretty=%B | tail -1 | head -5

  cat <<'EOF'

## 📊 Cambios

EOF

  # List of commits
  git log "$base_branch".."$feature_branch" --pretty="- %s (%an)" | head -10

  local commit_count=$(git rev-list --count "$base_branch".."$feature_branch")
  if [ "$commit_count" -gt 10 ]; then
    echo "- ... y $((commit_count - 10)) más"
  fi

  cat <<'EOF'

## ✅ Checklist

- [ ] Tests pasan localmente
- [ ] Cambios auditables (sin secrets)
- [ ] Linters pasan
- [ ] Cambios documentados si es necesario

---

*🤖 PR creada automáticamente por Claude*
EOF
}

# Create PR to target branch
create_pr_to_branch() {
  local feature_branch=$1
  local target_branch=$2
  local auto_merge=${3:-false}

  echo -e "${BLUE}📝 Creating PR: $feature_branch → $target_branch${NC}"

  local pr_title=$(generate_pr_title "$target_branch" "$feature_branch")
  local pr_body=$(generate_pr_body "$target_branch" "$feature_branch")

  # Create PR with better error handling
  echo -e "${CYAN}📝 Creating PR: $feature_branch → $target_branch${NC}"

  local pr_result
  if pr_result=$(gh pr create \
    --title "$pr_title" \
    --body "$pr_body" \
    --head "$feature_branch" \
    --base "$target_branch" \
    2>&1); then

    # Extract PR number from URL
    local pr_number=$(echo "$pr_result" | grep -oP 'pull/\K[0-9]+' || echo "")

    if [ -z "$pr_number" ]; then
      # Try to extract from the output line
      pr_number=$(echo "$pr_result" | grep -oP '#\K[0-9]+' | head -1)
    fi

    if [ -z "$pr_number" ]; then
      echo -e "${YELLOW}⚠️  Could not extract PR number${NC}"
      # Get the created PR
      sleep 1
      local created_pr=$(gh pr list \
        --head "$feature_branch" \
        --base "$target_branch" \
        --json number,url \
        --jq '.[0]' 2>/dev/null)

      if [ -n "$created_pr" ] && [ "$created_pr" != "null" ]; then
        pr_number=$(echo "$created_pr" | jq -r '.number')
      fi
    fi
  else
    # Check if PR already exists
    echo -e "${YELLOW}⚠️  PR might already exist${NC}"
    local existing_pr=$(gh pr list \
      --head "$feature_branch" \
      --base "$target_branch" \
      --json number,url \
      --jq '.[0]' 2>/dev/null)

    if [ -n "$existing_pr" ] && [ "$existing_pr" != "null" ]; then
      pr_number=$(echo "$existing_pr" | jq -r '.number')
      echo -e "${CYAN}📌 Using existing PR #$pr_number${NC}"
      echo "$pr_number"
      log_event "pr_create_skipped" "existing_pr" "PR #$pr_number already exists"
      return 0
    else
      echo -e "${RED}❌ Failed to create PR${NC}"
      echo -e "${RED}$pr_result${NC}"
      log_event "pr_create_failed" "error" "Could not create PR $feature_branch → $target_branch"
      return 1
    fi
  fi

  local pr_url="https://github.com/$REPO_OWNER/$REPO_NAME/pull/$pr_number"

  echo -e "${GREEN}✅ PR created: #$pr_number${NC}"
  echo -e "${CYAN}🔗 $pr_url${NC}"

  log_event "pr_created" "success" "PR #$pr_number: $feature_branch → $target_branch"

  # If auto_merge enabled, wait for checks and merge
  if [ "$auto_merge" = "true" ]; then
    wait_and_merge_pr "$pr_number" "$target_branch"
  fi

  echo "$pr_number"
}

# Wait for PR checks and merge
wait_and_merge_pr() {
  local pr_number=$1
  local target_branch=$2

  echo -e "\n${BLUE}⏳ Waiting for checks to complete...${NC}"

  local max_wait=20 # minutes
  local elapsed=0
  local check_interval=30 # seconds

  while [ $elapsed -lt $((max_wait * 60)) ]; do
    local pr_status=$(gh pr view "$pr_number" \
      --repo "$REPO_OWNER/$REPO_NAME" \
      --json statusCheckRollup,reviewDecision,mergeable \
      --jq '{checks: .statusCheckRollup, review: .reviewDecision, mergeable: .mergeable}' 2>/dev/null || echo "null")

    if [ "$pr_status" = "null" ]; then
      echo -e "${YELLOW}⚠️  Could not fetch PR status${NC}"
      sleep $check_interval
      elapsed=$((elapsed + check_interval))
      continue
    fi

    local all_checks=$(echo "$pr_status" | jq '.checks')
    local failed_checks=$(echo "$all_checks" | jq '[.[] | select(.status == "FAIL")] | length')
    local passing_checks=$(echo "$all_checks" | jq '[.[] | select(.status == "PASS")] | length')
    local pending_checks=$(echo "$all_checks" | jq '[.[] | select(.status == "PENDING")] | length')

    printf "\r${CYAN}⏳ Checks: ${GREEN}$passing_checks passing${NC}, ${YELLOW}$pending_checks pending${NC}, ${RED}$failed_checks failed${NC}"

    # Check if all done
    if [ "$pending_checks" -eq 0 ]; then
      echo ""
      if [ "$failed_checks" -gt 0 ]; then
        echo -e "${RED}❌ Some checks failed${NC}"
        log_event "pr_merge_blocked" "checks_failed" "PR #$pr_number: $failed_checks checks failed"
        return 1
      else
        # All passed, merge
        perform_pr_merge "$pr_number" "$target_branch"
        return $?
      fi
    fi

    sleep $check_interval
    elapsed=$((elapsed + check_interval))
  done

  echo -e "\n${YELLOW}⚠️  Timeout waiting for checks${NC}"
  log_event "pr_merge_timeout" "timeout" "PR #$pr_number exceeded $max_wait minutes"
  return 2
}

# Perform merge
perform_pr_merge() {
  local pr_number=$1
  local target_branch=$2

  echo -e "\n${MAGENTA}🔄 Merging PR #$pr_number to $target_branch${NC}"

  # Determine merge strategy based on branch
  local merge_strategy="squash"
  if [ "$target_branch" = "main" ]; then
    merge_strategy="merge"  # Use regular merge for main
  fi

  if gh pr merge "$pr_number" \
    --repo "$REPO_OWNER/$REPO_NAME" \
    --"$merge_strategy" \
    --delete-branch 2>/dev/null; then

    echo -e "${GREEN}✅ PR #$pr_number merged to $target_branch${NC}"
    log_event "pr_merged" "success" "PR #$pr_number merged with --$merge_strategy"

    # If merged to develop, create PR to main
    if [ "$target_branch" = "develop" ]; then
      sleep 2  # Wait for branch to be ready
      echo -e "\n${BLUE}📌 Creating follow-up PR: develop → main${NC}"
      create_pr_to_branch "develop" "main" "true"
    fi

    return 0
  else
    echo -e "${RED}❌ Failed to merge PR #$pr_number${NC}"
    log_event "pr_merge_failed" "error" "PR #$pr_number merge failed"
    return 1
  fi
}

# Full automation: detect branch and create PRs
full_automation() {
  local current_branch=$(git rev-parse --abbrev-ref HEAD)

  echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}🚀 Full PR Automation${NC}"
  echo -e "${CYAN}Current branch: $current_branch${NC}"
  echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}\n"

  log_event "automation_start" "started" "Branch: $current_branch"

  # Check if we're on a feature branch
  if [ "$current_branch" = "main" ] || [ "$current_branch" = "develop" ]; then
    echo -e "${YELLOW}ℹ️  You're on $current_branch (not a feature branch)${NC}"
    return 0
  fi

  # Create PR to develop
  local pr_develop=$(create_pr_to_branch "$current_branch" "develop" "true")

  if [ -z "$pr_develop" ]; then
    echo -e "${RED}❌ Failed to create PR to develop${NC}"
    return 1
  fi

  echo -e "\n${MAGENTA}════════════════════════════════════════════════════════════${NC}"
  echo -e "${GREEN}✅ Automation Complete${NC}"
  echo -e "${MAGENTA}════════════════════════════════════════════════════════════${NC}\n"

  log_event "automation_complete" "success" "PRs created and merged"
}

# Main
main() {
  local command=${1:-}

  case "$command" in
    "create")
      local feature_branch=${2:-}
      local target_branch=${3:-develop}
      if [ -z "$feature_branch" ]; then
        feature_branch=$(git rev-parse --abbrev-ref HEAD)
      fi
      create_pr_to_branch "$feature_branch" "$target_branch" "true"
      ;;
    "merge")
      local pr_number=$2
      local target_branch=${3:-develop}
      if [ -z "$pr_number" ]; then
        echo -e "${RED}Usage: $0 merge <pr_number> [target_branch]${NC}"
        exit 1
      fi
      perform_pr_merge "$pr_number" "$target_branch"
      ;;
    "auto")
      full_automation
      ;;
    *)
      full_automation
      ;;
  esac
}

if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  main "$@"
fi
