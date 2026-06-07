# UI/UX Improvement Plan

Generated 2026-06-07 from a full-app audit. Each phase ships independently — pick up
where the previous one left off without breaking anything.

Status legend: `[ ]` pending · `[~]` in progress · `[x]` done

---

## Phase 1 — Quick wins (~½ day)

Low-effort fixes that visibly improve daily use. Ship as one commit.

- [x] **1.1 Search debounce in FoundApksSheet** — `FoundApksSheet.kt:278`
  - Wrap query state in a `MutableStateFlow`, collect with `.debounce(300)` →
    set the filtered list. Today every keystroke recomputes the filter against
    1000+ files.
- [x] **1.2 IconButton ≥48dp** — `ApkInfoContent.kt:543` (OBB delete) + sweep for
    other `IconButton` calls without `.size(48.dp)`. Use `Modifier.minimumInteractiveComponentSize()`
    where the visual icon is smaller than 48dp.
- [x] **1.3 Empty state for permissions list** — `ApkInfoContent.kt:462`
  - When `apkInfo.permissions.isEmpty()`, render a single neutral row
    "No permissions requested" instead of the empty toggle.
- [x] **1.4 VirusTotal error surfacing** — `DialogMenuContent.kt:520`
  - Add a `Failed` arm to the existing scan-status branching that renders the
    failure message in `colorScheme.error`; otherwise users see "Not scanned"
    when an API call actually errored.
- [x] **1.5 Biometric toggle feedback** — `SettingScreen.kt` biometric toggles
  - Emit a one-shot `_events` Snackbar/Toast when the pref flip persists, mirroring
    how Default-installer toggle already does it.

**Acceptance:** all 5 items merged behind one commit; no behavior change beyond
visible polish; build green on debug.

---

## Phase 2 — Manage screen overhaul (~1.5 days)

The largest UX gap. Today Manage is a single-tap-per-app list — cleanup workflows
require 20+ trips through bottom sheets.

- [ ] **2.1 Multi-select mode**
  - Long-press an app row → enter selection mode. Top app bar changes to show
    `N selected` + Cancel + select-all. Tapping rows toggles selection. Back
    press exits selection.
  - State lives in `ManageViewModel` as `selectedPackages: StateFlow<Set<String>>`.
- [ ] **2.2 Batch operations**
  - Floating action bar (or trailing top-bar menu) in selection mode with:
    Uninstall · Disable · Force-stop · Clear data.
  - Each runs sequentially via existing `BatchUninstall`-style helper or a new
    `BatchManageOp` coroutine. Reuse the existing batch-uninstall notification
    channel for progress.
- [ ] **2.3 Usage stats column**
  - `PACKAGE_USAGE_STATS` is already declared. Query `UsageStatsManager` at load
    time and surface `lastTimeUsed` on each row as `"Unused 60d"` / `"3h ago"`.
  - Add sort option `By last used` to existing sort menu.
- [ ] **2.4 LazyColumn stable keys**
  - `items(apps, key = { it.packageName })` everywhere a list of `AppInfo` is
    rendered. Today there's no key → wrong-identity flashes during scroll.
- [ ] **2.5 Skeleton + empty states**
  - Loading: 6 shimmer placeholder rows (reuse a small `ShimmerBox` composable).
  - Empty after filter: illustration + "Clear filters" button.

**Acceptance:** can multi-select 10 apps and uninstall in one flow; sort by
last-used reflects real usage; no list scroll jank.

---

## Phase 3 — Dialog flow polish (~1 day)

The install dialog has had a lot of love already. Last gaps:

- [ ] **3.1 TabRow → PrimaryTabRow** — `DialogMenuContent.kt:264`
  - Migrate the deprecated `TabRow` + manual indicator to `PrimaryTabRow`. Drop
    the manual `tabIndicatorOffset` call (also deprecated).
- [ ] **3.2 Sticky button footer in menu tabs** — `DialogMenuContent.kt:299`
  - Wrap each tab's content in `Column` with `LazyColumn(Modifier.weight(1f))`
    + footer Row outside. Today the Install button can scroll off-screen with
    many splits.
- [ ] **3.3 Parse-state skeleton** — `InstallScreen.kt:526` / Dialog loading stage
  - Replace `CircularProgressIndicator` with stacked skeleton cards mirroring the
    Prepare layout: icon placeholder + 2 text lines + chip row.
- [ ] **3.4 Failed-stage actionable** — `DialogResultContent.kt`
  - Failed result currently shows Close only. Add Retry (re-fires `confirmInstall`)
    + Copy error (clipboard) for diagnostic reports.

**Acceptance:** no compiler deprecation warnings from these files; Failed stage
gives at least one recovery path.

---

## Phase 4 — Settings polish (~½ day)

- [ ] **4.1 Collapse Installation section**
  - Wrap Shizuku-options / Root-options sections in `ExpandableSection`
    composable (already exists?) defaulted closed. Today they're always open.
- [ ] **4.2 Inline "Create profile" CTA** — `ApkInfoContent.kt:342`
  - Empty-state currently reads "Go to Settings → Installer Profiles". Add a
    TextButton that opens `ProfileEditActivity` with `profileId=null`.
- [ ] **4.3 Save state in ProfileEdit** — `ProfileEditScreen.kt:124`
  - Disable Save button + show small `CircularProgressIndicator` while
    `viewModel.saveProfile` is in flight. Re-enable on completion before finish.

---

## Phase 5 — Design system + perf (~1.5 days, foundation)

- [ ] **5.1 Spacing tokens**
  - Add `object Spacing { val XS=4.dp; S=8.dp; M=12.dp; L=16.dp; XL=24.dp }`
    under `ui/theme/`. Migrate hard-coded `12.dp`/`20.dp` in dialog/* and
    setting/* files. Don't churn — only places already audited.
- [ ] **5.2 Async icon decode** — `ApkInfoContent.kt:120`
  - `apkInfo.icon?.toBitmap(128, 128)` runs in composition. Move to
    `produceState(initialValue = null) { … withContext(IO) { … } }` so first
    composition doesn't block on the decode.
- [ ] **5.3 Dialog icon LRU cache**
  - Reusing the AppLock pattern: an object-scoped `LruCache<String, Bitmap>`
    keyed on `iconPath` so re-opening a dialog for the same APK is instant.

---

## Phase 6 — Deprecation cleanup (~½ day, low risk)

Sweep of compile-time warnings. Pure refactor, no behavior change.

- [ ] **6.1 LocalClipboardManager → LocalClipboard**
  - `AboutScreen.kt:367`, `DiagnosticsScreen.kt:150`. Suspend variant — propagate
    to `LaunchedEffect`.
- [ ] **6.2 Icons.Rounded.List → AutoMirrored** — `ProfileEditScreen.kt:297`
- [ ] **6.3 Remove !! on non-null** — `DialogInstallStages.kt:185`,
    `InstallScreen.kt:411`
- [ ] **6.4 Elvis on non-null type** — `DialogPrepareContent.kt:59`
- [ ] **6.5 Unchecked casts** — `SyncViewModel.kt:57-58`

---

## Execution order

1. Phase 1 — quick wins. Visible result, sets the cadence.
2. Phase 5.1 (Spacing tokens only) — ground for later phases.
3. Phase 6 — cleanup, no risk.
4. Phase 3 — dialog polish.
5. Phase 4 — settings polish.
6. Phase 2 — manage overhaul (biggest, save for last with full context).
7. Phase 5.2–5.3 — perf, after the bigger refactors land.
