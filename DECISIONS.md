# Auxio9 Decision Record

This file records decisions that should remain stable across development conversations. It is not a substitute for inspecting the current code.

## D-001 — GitHub is the technical source of truth

**Status:** Accepted

Use repository files, commit history, branch state, and GitHub Actions evidence to establish technical facts. Archived ChatGPT conversations and assistant recollections are historical context only.

## D-002 — Build #212 is the accepted stable baseline

**Status:** Accepted

- Build: Android Build #212
- Commit: `ca1e92b47883211b309e17c5d80b7211c5ed8ed4`
- Baseline designation recorded: 2026-11-03
- Build workflow: run `41628113886`
- Lint workflow: run `41628113871`

A later commit or successful build does not automatically replace this baseline. The user must explicitly approve a replacement.

## D-003 — No separate stable branch

**Status:** Accepted

The user confirmed that Build #212 on `tune-glass-insights` is the stable baseline and that there is no separate stable branch.

## D-004 — Future development branch

**Status:** Accepted

Use `tune-glass-insights` for future development unless the user explicitly changes this policy. The repository default branch `dev` is not the accepted stable baseline.

## D-005 — Preserve the current stable application

**Status:** Accepted

The user reports that the application is currently stable and plans to use it before requesting upgrades. Do not initiate feature work merely because a development conversation has started.

## D-006 — No unverified claims

**Status:** Accepted

Distinguish discussed, implemented, built, tested, and user/device-verified work. Do not report one state as another.

## D-007 — Repository branch protection

**Status:** Needs follow-up

The observed GitHub branch metadata reported `protected: false` for `dev` and `tune-glass-insights`. The connected integration could not read the branch-protection endpoint (HTTP 403). No branch-protection changes have been made. Any decision to enable protection requires user approval and a separate permission/capability check.

## Decision maintenance

Add a dated entry when the user makes a durable technical or workflow decision. Do not record speculative ideas as accepted decisions. When a decision is superseded, retain the old entry and mark it superseded rather than silently rewriting history.
