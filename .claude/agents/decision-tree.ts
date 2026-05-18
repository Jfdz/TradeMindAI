#!/usr/bin/env node
/**
 * Fase 5: Decision Tree
 * Auto-detecta contexto y elige estrategia automáticamente
 *
 * - Merge/Deploy normal? → GitHub Actions (validación completa)
 * - Debugging/Investigation? → Direct K8s read-only
 * - Prod emergencia? → Hotfix (rollback incluido)
 * - Feature testing? → Local build
 */

import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';

// ANSI Colors
const colors = {
  reset: '\x1b[0m',
  red: '\x1b[0;31m',
  green: '\x1b[0;32m',
  yellow: '\x1b[1;33m',
  blue: '\x1b[0;34m',
  cyan: '\x1b[0;36m',
  magenta: '\x1b[0;35m',
};

function log(msg: string, color: keyof typeof colors = 'reset') {
  console.log(`${colors[color]}${msg}${colors.reset}`);
}

function exec(cmd: string, silent = false): string {
  try {
    return execSync(cmd, { encoding: 'utf-8', stdio: silent ? 'pipe' : 'inherit' }).trim();
  } catch (e) {
    return '';
  }
}

interface DecisionContext {
  currentBranch: string;
  isDirty: boolean;
  hasUnpushedCommits: boolean;
  uncommittedFiles: string[];
  lastCommitMessage: string;
  targetBranch?: string;
  isProductionEnvironment: boolean;
  k8sPodsDown: string[];
  recentErrors: string[];
  changedFiles: string[];
}

async function detectContext(): Promise<DecisionContext> {
  const currentBranch = exec('git rev-parse --abbrev-ref HEAD', true);
  const isDirty = exec('git status --porcelain', true).length > 0;
  const uncommittedFiles = exec('git status -s', true).split('\n').filter(l => l.length > 0);

  // Check for unpushed commits
  const hasUnpushedCommits = exec(`git rev-list --count ${currentBranch}@{u}..HEAD`, true).length > 0
    || exec(`git rev-list --count HEAD..${currentBranch}@{u}`, true).length > 0;

  const lastCommitMessage = exec('git log -1 --pretty=%B', true);

  // Detect if production environment
  const isProductionEnvironment = currentBranch === 'main' || currentBranch === 'prod';

  // Check K8s pod status
  const k8sPodsDown: string[] = [];
  try {
    const pods = exec(
      `kubectl get pods -n trading-saas --field-selector=status.phase!=Running -o jsonpath='{.items[*].metadata.name}'`,
      true
    );
    if (pods) {
      k8sPodsDown.push(...pods.split(' '));
    }
  } catch (_) {
    // K8s not available
  }

  // Get changed files
  const changedFiles = exec('git diff --name-only', true).split('\n').filter(f => f.length > 0);

  // Recent errors in logs
  const recentErrors: string[] = [];
  try {
    const logsDir = path.join(process.cwd(), '.claude/debug-logs');
    if (fs.existsSync(logsDir)) {
      const files = fs.readdirSync(logsDir);
      files.forEach(file => {
        if (file.endsWith('.jsonl')) {
          const content = fs.readFileSync(path.join(logsDir, file), 'utf-8');
          const lines = content.split('\n').filter(l => l.length > 0);
          lines.slice(-5).forEach(line => {
            try {
              const json = JSON.parse(line);
              if (json.status === 'failed') {
                recentErrors.push(`${json.event}: ${json.details}`);
              }
            } catch (_) {
              // Skip invalid JSON
            }
          });
        }
      });
    }
  } catch (_) {
    // Skip error collection if fails
  }

  return {
    currentBranch,
    isDirty,
    hasUnpushedCommits,
    uncommittedFiles,
    lastCommitMessage,
    isProductionEnvironment,
    k8sPodsDown,
    recentErrors,
    changedFiles,
  };
}

type Strategy = 'GA_MONITORING' | 'K8S_DEBUG' | 'HOTFIX' | 'LOCAL_BUILD' | 'PR_AUTOMATION';

interface Decision {
  strategy: Strategy;
  confidence: number;
  reason: string;
  nextSteps: string[];
}

function makeDecision(context: DecisionContext): Decision {
  // Rule 1: Production emergency
  if (context.isProductionEnvironment && context.k8sPodsDown.length > 0) {
    return {
      strategy: 'HOTFIX',
      confidence: 0.95,
      reason: `🚨 Production pod(s) down: ${context.k8sPodsDown.join(', ')}`,
      nextSteps: [
        'Diagnose pod: ./.claude/agents/k8s-debug.sh pod <pod-name>',
        'Perform hotfix: ./.claude/agents/hotfix.sh hotfix <deployment> <service> <version>',
        'Verify health: kubectl get pods -n trading-saas -w',
      ],
    };
  }

  // Rule 2: Unpushed commits + feature branch = PR automation
  if (context.hasUnpushedCommits && !context.isProductionEnvironment && context.currentBranch !== 'develop') {
    return {
      strategy: 'PR_AUTOMATION',
      confidence: 0.9,
      reason: `📝 Feature branch with unpushed commits detected`,
      nextSteps: [
        'git push origin ' + context.currentBranch,
        './.claude/agents/pr-automation-enhanced.sh auto',
        'Monitor: ./.claude/agents/ga-monitor.sh ' + context.currentBranch,
      ],
    };
  }

  // Rule 3: Debug question in last commit = K8s debugging
  if (
    context.lastCommitMessage.toLowerCase().includes('debug') ||
    context.lastCommitMessage.toLowerCase().includes('investigate') ||
    context.lastCommitMessage.toLowerCase().includes('issue') ||
    context.recentErrors.length > 0
  ) {
    return {
      strategy: 'K8S_DEBUG',
      confidence: 0.85,
      reason: `🔍 Debugging context detected in commit message or recent errors`,
      nextSteps: [
        'Diagnose namespace: ./.claude/agents/k8s-debug.sh namespace',
        'Check specific service logs: ./.claude/agents/k8s-debug.sh logs <pod-name>',
        'Review error logs: cat .claude/debug-logs/*.jsonl | jq \'select(.status=="failed")\' ',
      ],
    };
  }

  // Rule 4: Changed files suggest feature branch testing
  if (
    context.changedFiles.length > 0 &&
    !context.isProductionEnvironment &&
    context.currentBranch !== 'develop'
  ) {
    const javaChanges = context.changedFiles.some(f => f.startsWith('services/') && f.endsWith('.java'));
    const nodeChanges = context.changedFiles.some(f => f.startsWith('services/web-app') && f.endsWith('.tsx'));

    if (javaChanges || nodeChanges) {
      return {
        strategy: 'LOCAL_BUILD',
        confidence: 0.8,
        reason: `🏗️  Code changes detected - test in staging without GA (5min vs 15min)`,
        nextSteps: [
          'Identify service: java changes? → trading-core-service or market-data-service',
          'Build locally: ./.claude/agents/local-build.sh <service>',
          'Deploy to staging: https://staging.trademind.es',
          'Once validated, push and create PR: git push && ./.claude/agents/pr-automation-enhanced.sh auto',
        ],
      };
    }
  }

  // Rule 5: Main/develop push = GA monitoring
  if ((context.currentBranch === 'main' || context.currentBranch === 'develop') && context.hasUnpushedCommits) {
    return {
      strategy: 'GA_MONITORING',
      confidence: 0.9,
      reason: `📌 Main/develop branch - monitoring GA workflows`,
      nextSteps: [
        'Push: git push origin ' + context.currentBranch,
        'Monitor GA: ./.claude/agents/ga-monitor.sh ' + context.currentBranch,
        'Check results: gh run list --branch ' + context.currentBranch + ' --limit 5',
      ],
    };
  }

  // Default: GA monitoring
  return {
    strategy: 'GA_MONITORING',
    confidence: 0.7,
    reason: `📊 Default strategy: monitor GitHub Actions workflows`,
    nextSteps: [
      'Create or update PR: gh pr create',
      'Monitor: ./.claude/agents/ga-monitor.sh ' + context.currentBranch,
      'Review: gh pr view',
    ],
  };
}

async function main() {
  log('\n════════════════════════════════════════════════════════════', 'magenta');
  log('🎯 Decision Tree: Automated Strategy Selection', 'magenta');
  log('════════════════════════════════════════════════════════════\n', 'magenta');

  log('📊 Analyzing context...', 'blue');
  const context = await detectContext();

  log('\nContext Detected:', 'cyan');
  log(`  Branch: ${context.currentBranch}`, 'cyan');
  log(`  Uncommitted: ${context.uncommittedFiles.length} files`, 'cyan');
  log(`  Unpushed: ${context.hasUnpushedCommits ? 'Yes' : 'No'}`, 'cyan');
  if (context.k8sPodsDown.length > 0) {
    log(`  ⚠️  K8s pods down: ${context.k8sPodsDown.join(', ')}`, 'red');
  }
  if (context.recentErrors.length > 0) {
    log(`  ⚠️  Recent errors: ${context.recentErrors.length}`, 'yellow');
  }

  const decision = makeDecision(context);

  log('\n────────────────────────────────────────────────────────────', 'cyan');
  log('Decision:', 'cyan');
  log(`  Strategy: ${decision.strategy} (confidence: ${(decision.confidence * 100).toFixed(0)}%)`, 'cyan');
  log(`  Reason: ${decision.reason}`, 'cyan');
  log('────────────────────────────────────────────────────────────\n', 'cyan');

  log('Next Steps:', 'green');
  decision.nextSteps.forEach((step, i) => {
    log(`  ${i + 1}. ${step}`, 'green');
  });

  log('\n════════════════════════════════════════════════════════════\n', 'magenta');

  // Log decision
  const auditLog = path.join(process.cwd(), '.claude/debug-logs/decision-tree.jsonl');
  fs.mkdirSync(path.dirname(auditLog), { recursive: true });
  const event = {
    timestamp: new Date().toISOString(),
    strategy: decision.strategy,
    confidence: decision.confidence,
    branch: context.currentBranch,
    reason: decision.reason,
  };
  fs.appendFileSync(auditLog, JSON.stringify(event) + '\n');

  process.exit(0);
}

main().catch(err => {
  log(`Error: ${err.message}`, 'red');
  process.exit(1);
});
