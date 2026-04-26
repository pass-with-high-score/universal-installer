# Manage Screen — Feature Plan

Scope: features to add to the Manage tab (`presentation/manage/`) in **v1.5.0** and beyond.
Bottom sheet (`AppActionSheet`) is the extension point — most actions are one `ActionRow` away.

Last updated: 2026-04-26

---

## v1.5.0 — Phase 1 (this release)

Goal: ship a "looks rich, doesn't bloat" set of actions that leverage existing infrastructure
(Shizuku/Root already wired). 5 actions in the sheet + 1 list-level improvement.

### Per-app actions (in `AppActionSheet`)

| # | Action | API | Effort | Acceptance |
|---|---|---|---|---|
| 1 | **Open app** | `PackageManager.getLaunchIntentForPackage(pkg)` | 15 min | Tap → app launches. Disabled / no launch intent → row hidden, not greyed. |
| 2 | **App info** | `Intent(ACTION_APPLICATION_DETAILS_SETTINGS, "package:$pkg".toUri())` | 10 min | Tap → system Settings → App info page for that package. |
| 3 | **Share APK** | `ApkExtractor.extract` to `cacheDir/share/` then `ACTION_SEND` chooser | 45 min | Tap → progress dialog (reuse extract running state) → share sheet pops. Cache file deleted on activity stop. |
| 4 | **Force stop** *(privileged)* | `am force-stop <pkg>` via `ShizukuShellExecutor` / `RootController` | 1 h | Visible only when `useShizuku \|\| useRoot`. Tap → toast "Stopped <name>". Best-effort, swallow non-zero exit codes. |
| 5 | **Disable / Enable** *(privileged)* | `pm disable-user --user 0 <pkg>` / `pm enable <pkg>` | 1.5 h | Same gate as Force stop. Row label flips based on `ApplicationInfo.enabled`. After action → refresh app list so the badge updates. |

**Sheet ordering:** Open app · App info · Share APK · _(privileged group divider)_ · Force stop · Disable/Enable · Uninstall.

### List-level improvement

| # | Item | Effort | Acceptance |
|---|---|---|---|
| 6 | **Filter chips** below search bar | 2 h | Replace the "Show system apps" `Switch` with row of chips: `User` (default selected), `System`, `Disabled`. Multi-select. Existing system-toggle pref is migrated to a chip selection state. |

**Total Phase 1 effort: ~6 hours.** Lands in v1.5.0 alongside the Manage merge already done.

---

## v1.6.0 — Phase 2

Add the rest of Tier 2 + power-user filters once Phase 1 ships and we see usage signal.

| # | Feature | Effort | Why later |
|---|---|---|---|
| 7 | **Clear cache** *(privileged)* | 1 h | Needs Android-version branching: `pm clear --cache-only` is API 34+, fall back to `rm -rf /data/data/<pkg>/cache` for root. |
| 8 | **Clear all data** *(privileged)* | 30 min | Trivial after #7 lands; pairs as a destructive variant. Confirm dialog needed. |
| 9 | **Storage breakdown** | 4 h | `StorageStatsManager.queryStatsForPackage` → APK / Data / Cache / OBB. Add as expandable section in `AppActionSheet` header. |
| 10 | **Disabled apps filter** | 1 h | Filter chip + dedicated empty-state copy. Depends on #5 to be useful. |
| 11 | **Stats banner** | 2 h | "%d apps · %s · %d disabled" pinned at top of list above the search bar. Recomputed from `apps.sumOf { … }`. |

---

## v1.7.0+ — Phase 3 (defer until validated)

| # | Feature | Notes |
|---|---|---|
| 12 | **Group by installer** | Read `pm.getInstallSourceInfo(pkg)` → bucket Play / F-Droid / Aurora / Sideload. Useful for security review. ~half day. |
| 13 | **App permissions viewer** | `getPackageInfo(GET_PERMISSIONS)` → list with revoke button (Shizuku `pm revoke`). ~half day. Possibly its own sub-screen, not in sheet. |
| 14 | **Usage stats chart** | Extend existing `UsageStatsManager` query → daily bar chart for last 7 days. ~half day. |
| 15 | **Open in store** | Smart route Play/F-Droid/Aurora based on `getInstallSourceInfo` and what's installed. ~1 h. Skipped from Phase 1 because most users don't reinstall to update. |

---

## Out of scope — won't build

- App cloning / multi-instance — needs work profile + extensive plumbing
- Backup with data (root) — niche, security risk if backups land on cloud sync
- App label/icon override — manifest mutation, breaks signature
- Battery usage — system permission usually denied
- Notification history — `NotificationListenerService` is heavy plumbing for marginal value

---

## Code touchpoints (Phase 1)

```
presentation/manage/
├── ManageScreen.kt
│   ├── AppActionSheet — append 5 ActionRows + privileged divider
│   ├── ActionRow — already exists, no change
│   └── (new) FilterChipsRow — replaces system Switch
├── ManageViewModel.kt
│   ├── (new) launchApp(pkg)
│   ├── (new) openAppInfo(pkg)
│   ├── (new) shareApk(pkg) — wraps ApkExtractor → cache → emits SharedFile state
│   ├── (new) forceStop(pkg) — Shizuku/Root shell
│   ├── (new) toggleEnabled(pkg, enabled) — Shizuku/Root shell
│   └── extend ManageUiState with appFilter: Set<AppFilter>
└── (no new files needed)
```

External resources:
- `res/values/strings.xml` — 8 new strings (action labels, filter chip labels)
- No new permissions
- No new dependencies

---

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Force-stop / disable on critical system process bricks UI | Block actions on `app.packageName == BuildConfig.APPLICATION_ID` and on launchers (`isHomePackage()`) |
| Disabled app stays in list but looks broken | Add `enabled: Boolean` to `InstalledApp`, render greyed icon + "Disabled" badge |
| Share APK leaks to cloud apps the user didn't expect | Use `Intent.createChooser(intent, null)` with `EXTRA_EXCLUDE_COMPONENTS` empty — let user decide. Doc the cache cleanup in code comment. |
| Filter chip migration loses user's "show system" pref | One-time DataStore migration: `if (showSystemApps) appFilter = setOf(User, System) else setOf(User)` |
| Privileged actions silently no-op when Shizuku permission revoked between toggle and tap | Re-probe Shizuku state in ViewModel before each privileged shell exec; show snackbar "Shizuku not ready" instead of silent failure |

---

## Open questions

1. **Sheet vs screen for permissions viewer (#13)** — sheet gets crowded if we add many actions. Maybe permissions deserves its own sub-screen accessed from the sheet?
2. **Disabled apps in the main list, or filter-only?** — recommend: in main list with greyed icon + badge, plus a filter chip to view only-disabled.
3. **Privileged group label** — "Advanced" or "Shizuku" or "Privileged"? Defer to user; "Advanced" feels least technical.

---

## Decision log

- 2026-04-26: Pivot the Manage merge done; Phase 1 picked from Tier 1 + 2 of brainstorm. Filter chips elevated above stats banner because chips also enable the Disabled filter post-v1.5.
