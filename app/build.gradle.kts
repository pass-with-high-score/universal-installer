import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlinx.serialization)
    id("kotlin-parcelize")
}

// Firebase (Analytics + Crashlytics) ships in the `play` flavor only. Its config file carries
// our Firebase project's keys, so it is deliberately untracked — see docs/FIREBASE.md. Without
// it the `play` variants are switched off entirely and the Firebase plugins are never applied,
// which is what keeps a fresh open-source checkout building exactly as it did before.
val firebaseConfig = file("src/play/google-services.json")
val hasFirebaseConfig = firebaseConfig.exists()

// The module root is also on the plugin's search path, and a file there applies to *every*
// flavor — the `opensource` APK silently gains the Firebase app id and API key as string
// resources. Android Studio's Firebase assistant puts one here given half a chance, and it is
// gitignored, so nothing else would ever tell us. Fail loudly instead.
val strayFirebaseConfig = file("google-services.json")
require(!strayFirebaseConfig.exists()) {
    "$strayFirebaseConfig applies to every flavor, including the open-source build. " +
        "Move it to $firebaseConfig — that is the only location the `play` flavor reads."
}

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")

    extensions.findByName("googleServices")?.let { ext ->
        try {
            val strategyClass = Class.forName("com.google.gms.googleservices.GoogleServicesPlugin\$MissingGoogleServicesStrategy")
            val ignoreStrategy = strategyClass.enumConstants?.firstOrNull { (it as Enum<*>).name == "IGNORE" }
            if (ignoreStrategy != null) {
                ext.javaClass.getMethod("setMissingGoogleServicesStrategy", strategyClass)
                    .invoke(ext, ignoreStrategy)
            }
        } catch (_: Throwable) {}
    }
}

android {
    namespace = "app.pwhs.universalinstaller"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "app.pwhs.universalinstaller"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 39
        versionName = "1.15.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Load signing config from key.properties (CI/CD)
    val keyPropertiesFile = rootProject.file("key.properties")
    val useReleaseKeystore = keyPropertiesFile.exists()

    if (useReleaseKeystore) {
        val keyProperties = Properties().apply {
            load(keyPropertiesFile.inputStream())
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keyProperties["storeFile"] as String)
                storePassword = keyProperties["storePassword"] as String
                keyAlias = keyProperties["keyAlias"] as String
                keyPassword = keyProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (useReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // Both flavors ship the same installer backends — libsu for Root alongside Shizuku and the
    // default system installer. The dimension exists purely to keep Google's proprietary
    // Analytics/Crashlytics libraries out of the build we publish as open source. (An earlier
    // store/full split over libsu was removed for unrelated reasons; this is not that split.)
    flavorDimensions += "distribution"
    productFlavors {
        // What `assembleDebug` / `assembleRelease` and the IDE pick by default, and the build
        // the GitHub release APK comes from. No Firebase, no Google Play Services.
        create("opensource") {
            dimension = "distribution"
            isDefault = true
        }
        // The Play Store build: same app plus Firebase Analytics and Crashlytics.
        create("play") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            keepDebugSymbols.add("**/*.so")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

// The Crashlytics plugin attaches to the project, not to a flavor, so it injects its
// mapping_file_id and version_control_info string resources into *every* variant — opensource
// included. Two extra resources shift every resource ID, which moves resources.arsc, the binary
// manifest, all of res/, and classes.dex through inlined R constants.
//
// F-Droid rebuilds the opensource APK from a checkout that has no google-services.json and
// requires a byte-for-byte match against the one we publish. Disabling these tasks makes the
// artifact identical either way, so it no longer matters whether the config happens to be
// present — on CI, or on the machine of whoever builds a release by hand.
if (hasFirebaseConfig) {
    tasks.matching {
        (it.name.startsWith("injectCrashlytics") || it.name.startsWith("uploadCrashlyticsMappingFile")) &&
            it.name.contains("Opensource")
    }.configureEach {
        enabled = false
    }
}

androidComponents {
    // Drop the `play` variants when there is no google-services.json to build them against.
    // A contributor cloning the repo then sees exactly the task list they saw before this
    // dimension existed, and `assemblePlayRelease` fails with "task not found" rather than
    // producing a Play build with Firebase silently missing.
    beforeVariants(selector().withFlavor("distribution" to "play")) { variant ->
        variant.enable = hasFirebaseConfig
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.bundles.ackpine)
    // Kept out of the shared ackpine bundle: :tv consumes that bundle too, and these declare
    // minSdk 26 against the modules' minSdk 24. Only the phone app offers Dhizuku.
    implementation(libs.ackpine.dhizuku)
    implementation(libs.ackpine.dhizuku.ktx)
    implementation(libs.dhizuku.api)
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)


    implementation(libs.bottom.sheet)

    implementation(libs.timber)
    compileOnly(libs.rikka.stub)
    implementation(libs.hiddenapibypass)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)

    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.bundles.ackpine.libsu)
    implementation(libs.libsu.core)
    implementation(libs.libsu.service)

    // App Updater module — opensource flavor only. Keeps Google Play build free of self-updating code.
    "opensourceImplementation"(project(":updater"))

    // Firebase, `play` flavor only. Quoted configuration names because type-safe accessors for
    // flavor configurations aren't generated for the script that declares the flavor.
    "playImplementation"(platform(libs.firebase.bom))
    "playImplementation"(libs.firebase.analytics)
    "playImplementation"(libs.firebase.crashlytics)

    // Play In-App Review, `play` flavor only for the same reason as Firebase: it is a closed
    // Play Services library, and `opensource` ships none. See docs/REVIEW.md.
    "playImplementation"(libs.play.review.ktx)

    implementation(libs.nanohttpd)
    implementation(libs.zxing.core)
    // Open-source QR scanner (Apache-2.0) for "Send to TV" on F-Droid builds.
    "opensourceImplementation"(libs.zxing.android.embedded)

    // Google Code Scanner (ML Kit) for instant QR scanning without Camera permission (`play` only).
    "playImplementation"(libs.play.services.code.scanner)

    // Wear OS Data Layer client for sending APKs to paired watches (`play` flavor only).
    // Proprietary Play Services library cannot be included in F-Droid `opensource` builds.
    "playImplementation"(libs.play.services.wearable)
}