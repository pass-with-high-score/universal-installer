# Firebase (Analytics + Crashlytics)

Firebase ships in the Play Store build and nowhere else. The `:app` module has a `distribution`
flavor dimension with two flavors:

| Flavor | Firebase | Ships as |
| :--- | :--- | :--- |
| `opensource` | none — no Google Play Services libraries at all | GitHub release APK, and the default for local builds |
| `play` | Analytics + Crashlytics | Play Store AAB |

Everything else about the two builds is identical: same `applicationId`, same version, same
install backends (Default / Shizuku / Root / Dhizuku).

## Build commands

```bash
./gradlew :app:assembleOpensourceDebug      # day-to-day debug build
./gradlew :app:assembleOpensourceRelease    # GitHub release APK
./gradlew :app:bundlePlayRelease            # Play Store AAB
```

The `play` variants only exist when `app/src/play/google-services.json` is present. Without it
`assembleDebug` / `assembleRelease` build the `opensource` flavor alone, exactly as they did
before the dimension was added, and any `*Play*` task fails with "task not found" rather than
quietly producing a Play build with Firebase missing. With the file present those two build
*both* flavors, so name the variant explicitly when you only want one.

## Setting up google-services.json

The file is **not** in the repository — it carries our Firebase project's app id and API key, and
this repo is public.

1. In the [Firebase console](https://console.firebase.google.com/), open the project and add (or
   select) an Android app with package name `app.pwhs.universalinstaller`. :app, :tv and :wearos all
   share it — one Play listing, three form factors — so one Firebase client covers all three.
2. Download `google-services.json`.
3. Put it at `app/src/play/google-services.json` and `tv/google-services.json`. They are gitignored; do not commit them.

For CI, the same file is stored base64-encoded in the `GOOGLE_SERVICES_JSON` repository secret and
written back by the `Decode Firebase config` step of `.github/workflows/publish-release.yml`:

```bash
base64 -i app/src/play/google-services.json | pbcopy   # macOS
```

Contributors don't need any of this. A clone without the file builds and runs the `opensource`
flavor normally.

## Deobfuscating Play crash reports

`play` release builds are minified, so Crashlytics needs the R8 mapping file to show real class
names. The Crashlytics Gradle plugin uploads it as part of `bundlePlayRelease` — nothing to do by
hand — and `proguard-rules.pro` keeps `SourceFile,LineNumberTable` so reports carry line numbers.
The `opensource` flavor turns that upload off: it has no Firebase app id to upload against.

## What the app reports

The seam is `app.pwhs.universalinstaller.telemetry.Telemetry`. Code under `src/main` only ever
calls that; each flavor's `src/<flavor>/…/TelemetrySinkFactory.kt` decides what it does — Firebase
on `play`, nothing at all on `opensource`.

Events and parameters are declared in `TelemetryEvents`, currently:

| Event | Parameters | Answers |
| :--- | :--- | :--- |
| `install_started` | `method`, `apk_count` | — |
| `install_result` | `method`, `result` (`success` / `failure` / `cancelled`), `failure` | Which backends actually work, and how they fail |
| `backend_health` | `method`, `healthy` | How many people set up Shizuku or root and arrive with it broken |
| `default_installer_set` | `method`, `enabled`, `result` (+ `blocked`) | Whether taking over the installer role succeeds, per backend |
| `feature_used` | `feature` | Which secondary features are worth maintaining |

`method` is the backend that actually ran — `default`, `shizuku`, `root`, `dhizuku`,
`manual_shizuku`, `manual_root` — and is also set as the `install_method` user property so crashes
can be split by backend. Firebase collects screen views and sessions on its own; we add nothing
there.

`backend_health` is the one that can't be derived from the others: `install_result` only describes
installs that happened, so someone who configured Shizuku, had it break and gave up never appears
in it. It fires from `BackendSelfHeal` once per cold start, and only for backends the user turned
on — so the denominator is people who chose that backend.

`feature_used` is one event name with a `feature` parameter rather than one name per feature:
`lan_share`, `virustotal_scan`, `apk_backup`, `installer_profile`, `batch_install`, `obb_copy`,
`url_download`, `uninstall`, `review_prompt`. It fires when a feature is used, not when its screen is opened. `review_prompt` is the odd one
out: it records that the in-app review sheet was asked for, which is all Play lets us know —
see [REVIEW.md](REVIEW.md).

Warnings and errors logged through Timber become Crashlytics breadcrumbs, and `Timber.e(throwable)`
is additionally reported as a non-fatal.

**Rule for anything added here:** never report what the user installed. No package names, no app
names, no file names, no URIs. What we want to learn is which install backends work and where they
fail, and none of that is needed for it.

## The advertising ID permission

Firebase Analytics merges two permissions into the `play` manifest that `opensource` doesn't have:

```
com.google.android.gms.permission.AD_ID
android.permission.ACCESS_ADSERVICES_AD_ID
```

They must be declared in the Play Console's Data Safety form. The app has no ads and doesn't use
attribution, so they can also just be dropped — put this in `app/src/play/AndroidManifest.xml`
(with `xmlns:tools` declared on the root element):

```xml
<uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
```

Analytics keeps working without them; it just can't tie events to an advertising ID. Left in
place for now because that's Firebase's default and removing it is a product call.

## Debug builds report too

`assemblePlayDebug` collects and sends like the release build does — deliberately, so the
integration can be verified before shipping. If dev noise becomes a problem in the console, add
`app/src/playDebug/AndroidManifest.xml` with:

```xml
<meta-data android:name="firebase_crashlytics_collection_enabled" android:value="false" />
<meta-data android:name="firebase_analytics_collection_deactivated" android:value="true" />
```

## The opt-out

Reporting is on by default and the user can turn it off in two places, both writing
`SharedPrefsKeys.ANALYTICS_ENABLED` (absent means on):

- An onboarding page, shown only when `Telemetry.isCollecting` — that is, on the `play` build.
  It presents the switch already on.
- Settings → Privacy, a section that likewise does not exist in the `opensource` build, because a
  switch there would promise control over something that never happens.

Both call `Telemetry.setCollectionEnabled`, which reaches
`FirebaseAnalytics.setAnalyticsCollectionEnabled` and
`FirebaseCrashlytics.setCrashlyticsCollectionEnabled` immediately, so turning it off stops the next
event rather than the next launch. `App.applyTelemetryPreference` re-applies the preference at
every start: Firebase persists the flag itself, but the preference is what should decide — including
after a restore onto a new device, where the preference travelled and Firebase's own state did not.

It runs before `BackendSelfHeal`, which is the first thing that would otherwise report.

## Not done yet

The consent strings are English-only. The app ships 15+ locales, so
`onboarding_analytics_*` in `:core` and `setting_analytics_*` in `:app` still need translating.

The Play listing's Data Safety section needs to declare crash logs, diagnostics, and device
identifiers.
