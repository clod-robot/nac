---
name: deploy-script-release
description: Harden a deployment script before release — fold scattered ops components into one self-contained script, make installs idempotent, and verify on the live host without disrupting service. Use when the user asks to finalize, clean up, or make a deploy script release-ready. Do not use for one-off local scripts or application source code.
---

# Deploy Script Release Hardening

Release a deployment script so the deliverable is one file plus artifacts, and running it on the host is safe and idempotent.

## Workflow

1. **Inventory** — list every ops component the system relies on that is NOT already in the deploy script (LB config generators, active health-check daemons, systemd units, sudoers rules, cron @reboot entries, sysctl tuning). Anything hand-deployed separately is a release gap.

2. **Fold into one script** — add an install command (e.g. `install-radius`) that materializes each component:
   - Generate child scripts inline with a **single-quoted heredoc delimiter** (`<<'EOF'`) so `$vars` are written verbatim and not expanded by the parent.
   - For files needing root, write to a `mktemp` file first, then `sudo cp` it in. Never pipe a heredoc into `sudo` — `sudo -S` reads the password from stdin and collides with the heredoc.
   - systemd units and sudoers snippets go down the same mktemp + `sudo cp` path; validate sudoers with `visudo -cf`.

3. **Defeat `set -e`** — if the script has a global `set -euo pipefail`, wrap the install function body in `set +e` ... `set -e`. Otherwise a benign non-zero step (e.g. `cp` onto itself, an already-enabled service) aborts the whole install halfway. This is the single most common silent failure.

4. **Validate syntax at two levels** — `bash -n` on the main script does NOT check heredoc string contents. Extract each embedded child script (by line range between its delimiters) and `bash -n` it separately.

5. **Verify idempotency on the live host** — deploy to a temp path, `bash -n`, then back up the in-place copy and overwrite. Run the install command. It must (a) refresh components, (b) leave running services healthy, (c) not restart user-facing traffic. Confirm with a smoke test against the live entrypoint.

6. **Close the loop with measured evidence** — report service state (`is-active`/`is-enabled`), the cron line present, file sizes, and a smoke result. Never claim "done" without the live check.

## Safety rules
- Keep passwords out of the delivered script; pass `SUDO_PASS` as an env var at runtime.
- sudoers grants must be minimal (one specific script path, `NOPASSWD` only where cron needs it).
- On Windows, never pass remote bash containing `$()`, backticks, or `<<` through PowerShell — write a local `.sh`, upload it, and execute it.
- Back up the in-place script before overwriting; never skip `bash -n` before applying.
