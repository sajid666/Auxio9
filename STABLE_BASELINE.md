# Auxio9 stable baseline

**Accepted stable build:** Android Build #212  
**Designation date:** 2026-11-03  
**Stable commit:** `ca1e92b47883211b309e17c5d80b7211c5ed8ed4`  
**Commit:** https://github.com/sajid666/Auxio9/commit/ca1e92b47883211b309e17c5d80b7211c5ed8ed4  
**Build #212 workflow:** https://github.com/sajid666/Auxio9/actions/runs/41628113886  
**Lint #246 workflow:** https://github.com/sajid666/Auxio9/actions/runs/41628113871  
**Branch at verification:** `tune-glass-insights`

## Verification when designated

- Android APK build: passed
- Android lint: passed
- Unit tests: passed
- Kotlin formatting: passed

## Rules for future work

1. Treat Android Build #212 and commit `ca1e92b47883211b309e17c5d80b7211c5ed8ed4` as the accepted known-good baseline.
2. When resuming after a context or memory reset, read this file first.
3. Make future development changes on `tune-glass-insights` only.
4. Never modify the separate stable branch unless the user explicitly changes this instruction.
5. Do not replace this baseline just because a newer build exists. A newer build becomes the accepted stable baseline only after the user explicitly approves it.
6. The Player design in Build #212 is accepted as-is. Do not make further visual changes to it unless requested.

This file is a project reference note. It does not itself create a GitHub Release or change branch protection.
