---
name: release
description: Cut a new release of universal-installer. Bumps versionCode + versionName in app/build.gradle.kts, generates a simple Fastlane changelog from git log since the last tag, commits as `build: <versionName> (<versionCode>)`, pushes, creates and pushes the `v<versionName>` tag, then updates the GitHub release notes via `gh`. Use when the user says "release", "cut a release", "bump version", or "/release".
---

# Release Workflow

End-to-end release cut for this Android project. Six ordered steps — do not reorder, do not skip.

## Inputs

Ask the user up front (single message, both questions):
1. **New `versionName`** (e.g. `1.5.0`). Default suggestion: bump minor from current.
2. **Bump type for changelog tone** — feature release, bugfix, or hotfix. Affects wording only.

`versionCode` is always `current + 1`. Do not ask.

If the working tree is dirty, stop and tell the user — this skill assumes a clean tree.

## Step 1 — Bump version

Read `app/build.gradle.kts`, find:

```
versionCode = <N>
versionName = "<X.Y.Z>"
```

Edit to `versionCode = <N+1>` and `versionName = "<new>"`. Use the Edit tool, not sed.

## Step 2 — Generate changelog

Find the previous tag and collect commits since:

```bash
git describe --tags --abbrev=0
git log <prev-tag>..HEAD --pretty=format:'%s'
```

Distill into a **simple** changelog — 5–8 bullets max, user-facing language only. Drop:
- `chore:`, `build:`, `ci:`, `docs:`, `refactor:` (unless user-visible)
- pure `fix: lint …`, translation-only commits, internal renames
- duplicate/superseded commits (later commit wins)

Keep `feat:` and user-visible `fix:`. Rewrite each as a plain bullet starting with `- ` — no conventional-commit prefix, no scope, no commit hash. One line each.

Write to `fastlane/metadata/android/en-US/changelogs/<new-versionCode>.txt`. No header, no trailing newline-noise — match the style of existing files in that folder (`5.txt` is the latest reference).

Show the draft to the user and wait for approval before step 3. If they edit, re-read the file before continuing.

## Step 3 — Commit

```bash
git add app/build.gradle.kts fastlane/metadata/android/en-US/changelogs/<new-versionCode>.txt
git commit -m "build: <versionName> (<versionCode>)"
```

Exact message format: `build: 1.5.0 (6)`. No body, no Co-Authored-By, no emoji.

If a pre-commit hook fails, fix the underlying issue and create a NEW commit — never `--amend`, never `--no-verify`.

## Step 4 — Push commit

```bash
git push origin HEAD
```

Confirm push succeeded before tagging. If the branch is behind, stop and surface the conflict — do not force-push.

## Step 5 — Tag and push tag

```bash
git tag v<versionName>
git push origin v<versionName>
```

Pushing the tag triggers `.github/workflows/publish-release.yml`, which builds signed artifacts and creates the GitHub Release. The workflow takes several minutes.

## Step 6 — Update release notes

The workflow creates the release with empty notes. Wait for the release to exist, then attach the changelog as the body:

```bash
# Wait for release to appear (workflow needs to run softprops/action-gh-release)
until gh release view v<versionName> >/dev/null 2>&1; do sleep 20; done

# Push changelog as release notes
gh release edit v<versionName> \
  --notes-file fastlane/metadata/android/en-US/changelogs/<new-versionCode>.txt
```

Use the Monitor tool's until-loop pattern for the wait — do NOT chain short sleeps. If the release still doesn't exist after ~10 minutes, check `gh run list --workflow=publish-release.yml` for failures and report back to the user instead of looping forever.

After `gh release edit` succeeds, print the release URL (`gh release view v<versionName> --json url -q .url`) and stop.

## Guardrails

- Never run with a dirty tree.
- Never force-push, never `--amend`, never skip hooks.
- Tag format is `v<versionName>` (with the `v`). The deploy workflow only triggers on `v*`.
- `versionCode` is monotonically increasing — never decrement, never reuse.
- The Fastlane changelog filename **is** the versionCode (`6.txt`, not `1.5.0.txt`). Play Console requires this.
- If steps 1–3 are done but step 4 (push) fails, do not roll back the local commit — the user can fix and re-push. Tell them what state things are in.
