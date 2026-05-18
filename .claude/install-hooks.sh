#!/bin/bash
# Instalador de Git Hooks
# Copia hooks de .claude/hooks/ a .git/hooks/

set -euo pipefail

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}📦 Installing Git Hooks${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}\n"

# Check if .git exists
if [ ! -d ".git" ]; then
  echo -e "${RED}❌ .git directory not found${NC}"
  echo -e "${YELLOW}Make sure you're in the project root${NC}"
  exit 1
fi

# Create .git/hooks if doesn't exist
mkdir -p .git/hooks

# List of hooks to install
HOOKS=("post-commit" "post-push")

# Install each hook
for hook in "${HOOKS[@]}"; do
  HOOK_SRC=".claude/hooks/$hook"
  HOOK_DST=".git/hooks/$hook"

  if [ ! -f "$HOOK_SRC" ]; then
    echo -e "${YELLOW}⚠️  Hook not found: $HOOK_SRC${NC}"
    continue
  fi

  # Copy hook
  cp "$HOOK_SRC" "$HOOK_DST"
  chmod +x "$HOOK_DST"

  echo -e "${GREEN}✅ Installed: $hook${NC}"
done

echo ""
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}🎉 Git Hooks Installed Successfully!${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}\n"

echo -e "${YELLOW}📝 Hooks installed:${NC}"
echo -e "  • ${GREEN}post-commit${NC}  - Ejecuta decision-tree después de commit"
echo -e "  • ${GREEN}post-push${NC}    - Ejecuta PR automation después de push"

echo ""
echo -e "${YELLOW}🚀 Ahora cada vez que hagas:${NC}"
echo -e "  ${BLUE}git commit${NC}    → decision-tree se ejecuta automáticamente"
echo -e "  ${BLUE}git push${NC}      → PR automation se ejecuta automáticamente"

echo ""
echo -e "${YELLOW}🔍 Para verificar que están instalados:${NC}"
echo -e "  ${BLUE}ls -la .git/hooks/${NC}"

echo ""
echo -e "${YELLOW}❌ Para desinstalar:${NC}"
echo -e "  ${BLUE}rm .git/hooks/post-commit .git/hooks/post-push${NC}"

echo ""
