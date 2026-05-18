#!/bin/bash
# Fase 1: GitHub Actions Monitoring
# Monitorea automáticamente workflows en tiempo real
# Detecta fallos, analiza logs, sugiere re-runs

set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
REPO_OWNER="Jfdz"
REPO_NAME="TradeMindAI"
WORKFLOWS_TO_TRACK=(
  "ci-market-data-service.yml"
  "ci-trading-core-service.yml"
  "ci-ai-engine.yml"
  "ci-web-app.yml"
  "release-orchestrator.yml"
)
MAX_WAIT_MINUTES=20
CHECK_INTERVAL=30 # seconds
LOG_DIR="./.claude/debug-logs"

# Create log directory
mkdir -p "$LOG_DIR"
AUDIT_LOG="$LOG_DIR/ga-monitor.jsonl"

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
  "repo": "$REPO_OWNER/$REPO_NAME"
}
EOF
)
  echo "$json" >> "$AUDIT_LOG"
}

# Function: Get the latest workflow run for a branch
get_latest_run() {
  local branch=$1
  local workflow=$2

  gh run list \
    --repo "$REPO_OWNER/$REPO_NAME" \
    --workflow "$workflow" \
    --branch "$branch" \
    --limit 1 \
    --json id,status,conclusion,name,updatedAt \
    --jq '.[0]'
}

# Function: Monitor a single workflow run
monitor_workflow_run() {
  local run_id=$1
  local workflow_name=$2
  local start_time=$(date +%s)
  local elapsed=0

  echo -e "${BLUE}⏳ Monitoring $workflow_name (Run: $run_id)${NC}"

  while true; do
    local run_status=$(gh run view "$run_id" \
      --repo "$REPO_OWNER/$REPO_NAME" \
      --json status,conclusion,displayTitle \
      --jq '{status: .status, conclusion: .conclusion, title: .displayTitle}')

    local status=$(echo "$run_status" | jq -r '.status')
    local conclusion=$(echo "$run_status" | jq -r '.conclusion')
    local title=$(echo "$run_status" | jq -r '.title')

    # Calculate elapsed time
    local current_time=$(date +%s)
    elapsed=$((($current_time - $start_time) / 60))

    # Status indicators
    local status_icon="⏳"
    if [ "$status" = "completed" ]; then
      if [ "$conclusion" = "success" ]; then
        status_icon="${GREEN}✅${NC}"
        echo -e "${GREEN}✅ PASSED: $workflow_name${NC} (${elapsed}m)"
        log_event "workflow_completed" "success" "$workflow_name: $title"
        return 0
      else
        status_icon="${RED}❌${NC}"
        echo -e "${RED}❌ FAILED: $workflow_name${NC} (${elapsed}m)"
        log_event "workflow_completed" "failure" "$workflow_name: $title"
        analyze_workflow_failure "$run_id" "$workflow_name"
        return 1
      fi
    else
      printf "\r${BLUE}⏳${NC} $workflow_name... ${CYAN}${elapsed}m${NC} elapsed"
    fi

    # Check timeout
    if [ $elapsed -gt $MAX_WAIT_MINUTES ]; then
      echo -e "\n${YELLOW}⚠️  Timeout: Workflow running > ${MAX_WAIT_MINUTES}m${NC}"
      log_event "workflow_timeout" "timeout" "$workflow_name exceeded $MAX_WAIT_MINUTES minutes"
      return 2
    fi

    sleep $CHECK_INTERVAL
  done
}

# Function: Analyze workflow failure
analyze_workflow_failure() {
  local run_id=$1
  local workflow_name=$2

  echo -e "\n${RED}🔍 Analyzing failure...${NC}\n"

  # Get failed job
  local failed_job=$(gh run view "$run_id" \
    --repo "$REPO_OWNER/$REPO_NAME" \
    --json jobs \
    --jq '.jobs[] | select(.conclusion == "failure") | {name: .name, id: .databaseId}' | head -1)

  if [ -z "$failed_job" ]; then
    echo -e "${YELLOW}Could not identify failed job${NC}"
    return
  fi

  local job_name=$(echo "$failed_job" | jq -r '.name')
  local job_id=$(echo "$failed_job" | jq -r '.id')

  echo -e "${RED}Failed Job: $job_name${NC}\n"

  # Get job logs
  echo -e "${CYAN}Last 50 lines of logs:${NC}"
  gh run view "$run_id" \
    --repo "$REPO_OWNER/$REPO_NAME" \
    --log \
    --jq '.[]' 2>/dev/null | tail -50 || echo "Could not fetch logs"

  echo -e "\n${YELLOW}Recovery options:${NC}"
  echo "  1. /retry-ga-workflow <run_id>  - Retry failed jobs"
  echo "  2. /investigate-error           - Deep dive into error"
  echo "  3. /push-fix                    - Push a fix and retry"

  log_event "workflow_failure_analyzed" "analyzed" "Job: $job_name"
}

# Function: Monitor all workflows for a PR/branch
monitor_all_workflows() {
  local branch=$1
  local pr_number=${2:-}

  echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}🚀 GitHub Actions Monitoring${NC}"
  echo -e "${CYAN}Branch: $branch${NC}"
  if [ -n "$pr_number" ]; then
    echo -e "${CYAN}PR: #$pr_number${NC}"
  fi
  echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}\n"

  local failed_count=0
  local passed_count=0
  local timeout_count=0
  local run_ids=()

  # Start all workflows
  for workflow in "${WORKFLOWS_TO_TRACK[@]}"; do
    local run=$(get_latest_run "$branch" "$workflow")

    if [ -z "$run" ] || [ "$run" = "null" ]; then
      echo -e "${YELLOW}⚠️  No runs found for $workflow on branch $branch${NC}"
      continue
    fi

    local run_id=$(echo "$run" | jq -r '.id')
    run_ids+=("$run_id")
  done

  # Monitor all runs in parallel
  local pids=()
  for run_id in "${run_ids[@]}"; do
    # Get workflow name from run
    local workflow_name=$(gh run view "$run_id" \
      --repo "$REPO_OWNER/$REPO_NAME" \
      --json name \
      --jq '.name')

    monitor_workflow_run "$run_id" "$workflow_name" &
    pids+=($!)
  done

  # Wait for all monitoring to complete
  for pid in "${pids[@]}"; do
    if wait "$pid"; then
      ((passed_count++))
    else
      exit_code=$?
      if [ $exit_code -eq 2 ]; then
        ((timeout_count++))
      else
        ((failed_count++))
      fi
    fi
  done

  # Summary
  echo -e "\n${CYAN}════════════════════════════════════════════════════════════${NC}"
  echo -e "${BLUE}📊 Summary${NC}"
  echo -e "${GREEN}✅ Passed: $passed_count${NC}"
  echo -e "${RED}❌ Failed: $failed_count${NC}"
  echo -e "${YELLOW}⏱️  Timeout: $timeout_count${NC}"
  echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}\n"

  log_event "monitoring_complete" "summary" "Passed: $passed_count, Failed: $failed_count, Timeout: $timeout_count"

  # Return exit code based on results
  if [ $failed_count -gt 0 ]; then
    return 1
  elif [ $timeout_count -gt 0 ]; then
    return 2
  else
    return 0
  fi
}

# Function: Detect if PR passed all checks
pr_ready_to_merge() {
  local pr_number=$1

  echo -e "${BLUE}🔍 Checking if PR is ready to merge...${NC}"

  # Get PR status
  local pr_status=$(gh pr view "$pr_number" \
    --repo "$REPO_OWNER/$REPO_NAME" \
    --json statusCheckRollup,reviewDecision \
    --jq '{checks: .statusCheckRollup, review: .reviewDecision}')

  local check_status=$(echo "$pr_status" | jq -r '.checks[] | select(.status == "FAIL") | .name' | wc -l)

  if [ "$check_status" -gt 0 ]; then
    echo -e "${RED}❌ Checks failing${NC}"
    return 1
  fi

  echo -e "${GREEN}✅ Ready to merge${NC}"
  return 0
}

# Function: Suggest auto-merge
suggest_auto_merge() {
  local pr_number=$1

  if pr_ready_to_merge "$pr_number"; then
    echo -e "\n${GREEN}🚀 Ready for auto-merge${NC}"
    echo -e "Command: ${CYAN}gh pr merge $pr_number --auto --squash${NC}"
    log_event "auto_merge_suggested" "ready" "PR #$pr_number"
    return 0
  else
    echo -e "\n${RED}⚠️  Not ready for auto-merge yet${NC}"
    return 1
  fi
}

# Main entry point
main() {
  local branch=${1:-}
  local pr_number=${2:-}

  if [ -z "$branch" ]; then
    # Try to detect current branch
    branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
    if [ -z "$branch" ]; then
      echo -e "${RED}Error: Please provide a branch name${NC}"
      echo "Usage: $0 <branch> [pr_number]"
      exit 1
    fi
  fi

  monitor_all_workflows "$branch" "$pr_number"
  local result=$?

  if [ $result -eq 0 ] && [ -n "$pr_number" ]; then
    suggest_auto_merge "$pr_number"
  fi

  exit $result
}

# Execute if run directly
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  main "$@"
fi
