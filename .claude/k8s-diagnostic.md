# K8s Diagnostic Workflow — Operator Manual for Future Claude Sessions

**Authoritative source for remote K8s diagnostics. Read this BEFORE asking the user to run kubectl manually.**

This repo ships a self-hosted-runner workflow that wraps `kubectl` in the cluster and commits the output back to `main` so any Claude session can read it. **Use this path always**; do not ask the operator for kubectl output.

## Where it lives

- Workflow: `.github/workflows/claude-k8s-diagnostic.yml` (workflow_dispatch only, runs on `self-hosted`).
- Wrapper on the runner: `/usr/local/bin/claude-k8s <command>` — installed on the self-hosted runner, has cluster access.
- Output sink: `.github/.diagnostic/output.txt` on **`claude-diagnostic`**, committed by `claude-bot` after every run (`ci: k8s diagnostic output [skip ci]`). Both `main` and `develop` are branch-protected, so the workflow cannot push to either directly. `claude-diagnostic` is a dedicated unprotected branch that exists solely for this purpose — overwritten every dispatch, never merged anywhere, never matched by any CI path filter.

## How to use it from a Claude session

1. **Trigger** the workflow with the right `command` input. Use `mcp__github__run_workflow` (load via ToolSearch):
   ```
   workflow_id: claude-k8s-diagnostic.yml
   ref: main
   inputs: { command: "<the command>" }
   ```
   Examples of `command` (single string, space-separated arguments to the wrapper):
   - `pods trading-core-service` — pod status (default)
   - `logs trading-core-service` — recent logs
   - `events trading-saas` — namespace events
   - `describe pod <pod-name>` — describe a specific pod
   - `rollout trading-core-service` — rollout status

   Note: exact subcommands depend on `claude-k8s`'s implementation. If a command fails, try the closest kubectl-style form (`logs`, `get pods`, `describe`, `top`, `events`, `rollout`).

2. **Wait for the bot commit on `claude-diagnostic`**, then fetch and read:
   ```bash
   git fetch origin claude-diagnostic && git show origin/claude-diagnostic:.github/.diagnostic/output.txt
   ```
   Or use `mcp__github__get_file_contents` for the file at `path=.github/.diagnostic/output.txt`, `ref=claude-diagnostic`. The file is overwritten each run — read it as soon as the new commit appears.

3. **Confirm freshness** before trusting the output:
   ```bash
   git log -1 --format='%ci %s' origin/claude-diagnostic -- .github/.diagnostic/output.txt
   ```
   The commit timestamp must be after the trigger; otherwise you are reading a stale dump.

## Operational rules

- **Always use this workflow** for cluster diagnostics. Do not ask the user to paste kubectl output unless the workflow itself is broken.
- **Self-hosted runner availability**: if the run queues for >2 min, surface that to the user — the runner may be down.
- **One concern per dispatch**. The file is overwritten, so chain commands by triggering, reading, then triggering again.
- **Workflow file lives on `main`** (so `workflow_dispatch` always references the latest version). The diagnostic commit always lands on `claude-diagnostic` because both `main` and `develop` are branch-protected; the push step is hardcoded to `git push origin HEAD:claude-diagnostic`.
- **The wrapper `claude-k8s` is opaque**; its source is not in the repo. Discover its supported subcommands empirically — start with `pods <svc>` and `logs <svc>`, then refine.
- **For multi-step debugging** (e.g. pods → events → describe → logs), batch your reasoning offline: pick the single most informative command first, read the output, then pick the next. Each dispatch is one runner job.

## Related references

- `.claude/k8s-contexts.json` — service → deployment/port/healthPath mapping. The deployment name for `trading-core-service` is `trading-core` (not `trading-core-service`) per that file.
- `.claude/helpers/k8s.sh` — local helpers (require local kubectl; **not usable** from a sandboxed Claude session).
- `.claude/plans/automatizar-debugging-remoto-kubernetes.md` — broader vision document. The diagnostic workflow is the first concrete piece.
