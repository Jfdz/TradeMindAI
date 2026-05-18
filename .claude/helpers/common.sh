#!/bin/bash
# Shared helpers sourced by all .claude/agents/*.sh scripts

# ── Colors ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
MAGENTA='\033[0;35m'
NC='\033[0m'

# ── gh CLI resolver ────────────────────────────────────────────────────────────
# On Windows the installer puts gh.exe in Program Files but does not always add
# it to the PATH seen by Git Bash / bash launched from Claude hooks.
# Resolve once here; every call in agent scripts goes through the gh() wrapper.
if ! command -v gh &>/dev/null; then
  _GH_WIN="/c/Program Files/GitHub CLI/gh.exe"
  if [ -x "$_GH_WIN" ]; then
    gh() { "$_GH_WIN" "$@"; }
    export -f gh
  else
    echo -e "${RED}ERROR: gh CLI not found. Install from https://cli.github.com${NC}" >&2
    exit 1
  fi
fi

# ── Shared log helper ─────────────────────────────────────────────────────────
# Usage: log_event <log_file> <event_type> <status> <details>
common_log_event() {
  local log_file=$1
  local event_type=$2
  local status=$3
  local details=$4
  local timestamp
  timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
  printf '{"timestamp":"%s","event":"%s","status":"%s","details":"%s"}\n' \
    "$timestamp" "$event_type" "$status" "$details" >> "$log_file"
}
