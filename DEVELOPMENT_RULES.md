# Auxio9 Development Rules

## Authority and evidence

1. GitHub repository contents, commit history, and actual workflow results are the technical source of truth.
2. Read `STABLE_BASELINE.md` and `PROJECT_STATUS.md` before starting a new development task.
3. Verify the current branch, HEAD commit, relevant source files, and relevant workflow configuration before making claims about implementation.
4. Separate verified repository facts from user-reported facts, assumptions, and unknowns.
5. Never invent file paths, symbols, commits, branches, workflow outcomes, test results, or implementation details.
6. If repository access or evidence is unavailable, state that verification has not occurred.

## Baseline and branch policy

1. Accepted stable baseline: Android Build #212, commit `ca1e92b47883211b309e17c5d80b7211c5ed8ed4`.
2. The baseline is associated with `tune-glass-insights`; there is no separate stable branch in the agreed arrangement.
3. Use `tune-glass-insights` for future development unless the user explicitly changes this instruction.
4. Never treat the default branch `dev` as the stable baseline.
5. Never replace the accepted baseline merely because a newer build exists.
6. Only the user can approve a new accepted stable baseline.
7. Do not rewrite, reset, delete, force-push, or merge branches without explicit approval.

## Scope control

1. Make only changes needed for the user's requested outcome.
2. Inspect the relevant implementation before editing.
3. Prefer the smallest maintainable change.
4. Preserve existing behavior unless the user explicitly requests a behavior change.
5. Do not perform unrelated refactoring, dependency upgrades, formatting sweeps, or cleanup.
6. Do not silently change build configuration, CI behavior, permissions, signing, release behavior, or data formats.

## Approval gates

Obtain explicit user approval before:
- Replacing or changing the accepted stable baseline.
- Merging, releasing, publishing, or distributing an APK.
- Changing branch protection, repository settings, secrets, permissions, or automation triggers.
- Performing destructive or history-rewriting Git operations.
- Expanding a task beyond its originally requested scope.
- Making a change that could affect user data or existing playback behavior in a material way.

## Implementation workflow

For each authorized code change:
1. Restate the intended behavior and acceptance criteria.
2. Inspect the branch, commit, relevant code, and related tests.
3. Identify the files to change and likely side effects.
4. Present a plan for substantial changes.
5. Implement only the approved scope.
6. Inspect the complete diff for accidental changes.
7. Run the relevant checks and report their actual results.
8. Record meaningful decisions and handover state.

## CI and test integrity

- Never disable, skip, weaken, or delete a meaningful test to make CI green.
- Never claim a check passed without a completed successful result.
- A successful APK build does not imply that lint, formatting, or unit tests passed.
- Report failed, cancelled, skipped, and unverified checks accurately.
- Do not claim physical-device verification unless it was actually performed and reported.

## Communication

- Explain technical outcomes in clear language suitable for a non-specialist.
- State what changed, why it changed, what was verified, and what remains uncertain.
- Prefer evidence and precise wording over confident speculation.
- Ask one focused question when an essential requirement is missing.
