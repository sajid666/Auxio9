# Auxio9 Testing and Verification Protocol

## Purpose

Use this protocol to report software verification accurately. The existence of a workflow file is not evidence that a workflow passed. Inspect the actual run associated with the commit being evaluated.

## Current GitHub Actions workflows

### Android Build

File: `.github/workflows/android.yml`

Observed behavior on `tune-glass-insights`:
- Runs on pushes to `tune-glass-insights` and pull requests targeting it.
- Checks out the repository and initializes pinned submodules.
- Sets up JDK 21.
- Installs Android NDK `28.2.13676358`.
- Runs `./gradlew app:packageDebug`.
- Uploads the debug APK as the `Auxio_Canary` artifact.

### Android Lint

File: `.github/workflows/lint.yml`

Observed behavior on `tune-glass-insights`:
- Runs on pushes to `tune-glass-insights` and pull requests targeting it.
- Checks out the repository and initializes pinned submodules.
- Sets up JDK 21.
- Runs Kotlin formatting check: `./gradlew spotlessCheck`.
- Runs unit tests: `./gradlew musikr:testDebug app:testDebugUnitTest`.
- Runs Android lint: `./gradlew lintDebug`.
- Prints the Android lint report when available.

## Verification sequence for a future change

1. Confirm the tested commit SHA and branch.
2. Inspect the relevant workflow configuration.
3. Inspect the exact workflow runs for that commit or pull request.
4. Confirm the Android build result and artifact, if a build was required.
5. Confirm formatting, unit-test, and lint results separately.
6. Inspect logs for failed or inconclusive steps.
7. Report any checks that were not run.
8. Ask the user to install and test the APK when physical-device validation is required.

## Required distinctions

- **Discussed:** An idea or change was described; no code change is implied.
- **Implemented:** The relevant code change exists in the repository.
- **Built:** A build workflow completed successfully for the identified commit.
- **Automated tests passed:** The relevant test steps completed successfully.
- **Lint/format passed:** The corresponding steps completed successfully.
- **Device tested:** The user or an available device-testing process actually installed and exercised the identified APK.
- **Accepted stable:** The user explicitly designated that build/commit as the new stable baseline.

Do not infer device testing from a successful CI build.

## Build #212 baseline evidence

The inspected Build #212 workflow run (`41628113886`) completed successfully, including the APK packaging and artifact upload steps.

The inspected Lint #246 workflow run (`41628113871`) completed successfully, including Kotlin formatting, unit tests, and Android lint.

An `Auxio_Canary` artifact was reported as unexpired when inspected. Artifact availability can change; recheck before relying on it later.

## Failure handling

- Preserve the failing logs and exact commit SHA.
- Identify the failing step and reproduce only when safe and practical.
- Do not bypass or weaken the failing check.
- Do not claim overall verification passed if any required check failed or was not run.
