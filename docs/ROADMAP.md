# Universal Installer — Roadmap

Last updated: 2026-04-26

## v1.4.1 — Hotfix (urgent)

v1.4.0 shipped a regression that broke APK installs on at least Android 15 / Motorola.
A hotfix is required before any new feature work.

| # | Issue                                             | Type       | Notes                                                                                                                                                                                                                                                                                                                     |
|---|---------------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | [#12](https://github.com/.../issues/12)           | **P0 bug** | "Unknown package" since v1.4.0. Likely caused by `ab50725` (migrate to multiple activities) — the new activity layout probably lost an `intent-filter` for `application/vnd.android.package-archive` + `content://` / `file://` schemes, or the `PackageInstaller` session is created against the wrong activity context. |
| 2 | [#11](https://github.com/.../issues/11)           | P1 bug     | Shizuku "replace existing" toggle does nothing. Verify the `-r` flag is forwarded to `pm install` in the Shizuku backend path.                                                                                                                                                                                            |
| 3 | [#3 (icon part)](https://github.com/.../issues/3) | P2 bug     | Missing app icon on some screen. Check `loadIcon()` vs `loadUnbadgedIcon()` and adaptive-icon fallback.                                                                                                                                                                                                                   |

**Release as `versionCode = 6`, `versionName = "1.4.1"`.**

---

## v1.5.0 — Feature release

Self-contained features, no external dependencies (no OAuth review, no Play Services).

| Item                                              | Source                                              | Effort | Notes                                                                                             |
|---------------------------------------------------|-----------------------------------------------------|--------|---------------------------------------------------------------------------------------------------|
| Advanced Install Options tab (Details / Advanced) | [#9](https://github.com/.../issues/9)               | M      | Already assigned. Two-tab install screen; Advanced holds Shizuku opts + OBB attach.               |
| Color theme picker + dynamic launcher icon        | community #3                                        | S–M    | Extend existing theme screen. Material You on Android 12+. Icons via `<activity-alias>` toggling. |
| APK extractor (installed apps → APK / XAPK)       | community #1                                        | M      | New screen, reuse uninstall list pattern. Bundle split APKs into `.xapk`.                         |
| "Keep app data" toggle on uninstall               | [#5](https://github.com/.../issues/5) (cherry-pick) | S      | `pm uninstall -k` flag. Small ask, high value.                                                    |

---

## v1.6.0 — Bigger bets

| Item                                    | Source                                | Effort | Notes                                                                                                                                          |
|-----------------------------------------|---------------------------------------|--------|------------------------------------------------------------------------------------------------------------------------------------------------|
| Cloud backup (Google Drive only, first) | community #2                          | L      | AppAuth, not Play Services SDK, to keep F-Droid build viable. **Start Google OAuth verification the day v1.5.0 ships** — that's the long pole. |
| Default installer system-wide           | [#5](https://github.com/.../issues/5) | M      | Intent-filter priority work + onboarding flow to set as default handler. Pairs with the #12 intent-filter audit.                               |
| Install profiles (per-source rules)     | [#5](https://github.com/.../issues/5) | M–L    | Data model: `(source package → backend, flags, prompt/silent)`.                                                                                |

---

## Backlog — needs discovery before scoping

- **Sandbox / isolated install** ([#3](https://github.com/.../issues/3)). Real sandboxing on stock Android is hard. Options to spike: Work Profile API, Island/Shelter integration, custom user via root. Don't promise a release date until an approach is validated.
- **"Open Android" advocacy banner** ([#13](https://github.com/.../issues/13)). One-time dismissible banner re: Google's developer-verification / sideloading policy. Low effort but a public stance — decide whether the project takes it before building.
- **Different notification/interaction forms** ([#5](https://github.com/.../issues/5), one of several). Reporter was vague; ask for a concrete example before scoping.
- **Additional cloud providers** (Dropbox, OneDrive). Defer until Drive ships and we see usage.

---

## Working agreement

- Hotfixes ship as `versionCode +1`, patch version bump.
- Feature releases bundle 3–5 items so changelogs stay readable.
- F-Droid reproducibility constraint: any new SDK must work without Google Play Services, or be flavor-gated.
