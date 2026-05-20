/**
 * GitHub Actions API client helpers for decision-tree.ts.
 * Uses the `gh` CLI so no additional auth token management is needed.
 */

import { execSync } from 'child_process';

const REPO = 'Jfdz/TradeMindAI';

// Resolve gh CLI path: on Windows the installer may not update the PATH seen by
// node child processes launched from Claude hooks.
function ghBin(): string {
  try {
    execSync('gh --version', { stdio: 'ignore' });
    return 'gh';
  } catch {
    const winPath = 'C:\\Program Files\\GitHub CLI\\gh.exe';
    try {
      execSync(`"${winPath}" --version`, { stdio: 'ignore' });
      return `"${winPath}"`;
    } catch {
      throw new Error('gh CLI not found. Install from https://cli.github.com');
    }
  }
}

const GH = ghBin();

function ghJson<T>(args: string): T {
  const out = execSync(`${GH} ${args}`, { encoding: 'utf-8', stdio: ['pipe', 'pipe', 'pipe'] });
  return JSON.parse(out) as T;
}

export interface WorkflowRun {
  id: number;
  status: 'queued' | 'in_progress' | 'completed';
  conclusion: 'success' | 'failure' | 'cancelled' | 'skipped' | null;
  name: string;
  updatedAt: string;
  url: string;
}

export interface PRStatus {
  number: number;
  state: 'open' | 'closed' | 'merged';
  mergeable: boolean | null;
  checksTotal: number;
  checksPassing: number;
  checksFailing: number;
  checksPending: number;
}

/** Return the most-recent run for a workflow on a given branch, or null. */
export function getLatestRunForBranch(branch: string, workflow: string): WorkflowRun | null {
  try {
    const runs = ghJson<WorkflowRun[]>(
      `run list --repo ${REPO} --workflow ${workflow} --branch ${branch} --limit 1 --json id,status,conclusion,name,updatedAt,url`
    );
    return runs.length > 0 ? runs[0] : null;
  } catch {
    return null;
  }
}

/** Return status of a specific run by ID. */
export function getRunStatus(runId: number): WorkflowRun | null {
  try {
    return ghJson<WorkflowRun>(
      `run view ${runId} --repo ${REPO} --json id,status,conclusion,name,updatedAt,url`
    );
  } catch {
    return null;
  }
}

/** Aggregate check status for a PR. */
export function getPRStatus(prNumber: number): PRStatus | null {
  try {
    const raw = ghJson<{
      number: number;
      state: string;
      mergeable: boolean | null;
      statusCheckRollup: Array<{ status: string; conclusion: string }>;
    }>(
      `pr view ${prNumber} --repo ${REPO} --json number,state,mergeable,statusCheckRollup`
    );

    const checks = raw.statusCheckRollup ?? [];
    return {
      number: raw.number,
      state: raw.state as PRStatus['state'],
      mergeable: raw.mergeable,
      checksTotal: checks.length,
      checksPassing: checks.filter((c) => c.conclusion === 'SUCCESS').length,
      checksFailing: checks.filter((c) => c.conclusion === 'FAILURE').length,
      checksPending: checks.filter((c) => c.status === 'IN_PROGRESS' || c.status === 'QUEUED').length,
    };
  } catch {
    return null;
  }
}

/** List open PRs for a branch. Returns empty array when none. */
export function listOpenPRsForBranch(branch: string): Array<{ number: number; url: string }> {
  try {
    return ghJson(`pr list --repo ${REPO} --head ${branch} --state open --json number,url`);
  } catch {
    return [];
  }
}

/** Re-run failed jobs for a workflow run. Returns true on success. */
export function rerunFailedJobs(runId: number): boolean {
  try {
    execSync(`${GH} run rerun ${runId} --repo ${REPO} --failed`, { stdio: 'inherit' });
    return true;
  } catch {
    return false;
  }
}
