import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Consumer side: packages the profile that :tv-baselineprofile generates into the APK.
    // Without this the profile is produced but never shipped.
    alias(libs.plugins.androidx.baselineprofile)
}

val firebaseConfig = file("src/play/google-services.json")
val hasFirebaseConfig = firebaseConfig.exists()

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
    namespace = "app.pwhs.tv"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        // One Play listing, three form factors. Play tells the artifacts apart by their
        // uses-feature requirements — leanback here, watch in :wearos, neither in :app — so each
        // needs its own versionCode band: phone 1-999, watch 1000+, TV 2000+.
        applicationId = "app.pwhs.universalinstaller"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2030
        versionName = "1.16.0"

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
            // Compose generates thousands of small classes and lambdas; leaving R8 off shipped all
            // of them. Measured against Netflix's TV build (14 MB of dex) this module was at 73 MB.
            // On TV hardware that costs both startup and steady-state performance.
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

    flavorDimensions += "distribution"
    productFlavors {
        create("opensource") {
            dimension = "distribution"
            isDefault = true
        }
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

dependencies {
    implementation(project(":core"))
    "opensourceImplementation"(project(":updater"))
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    testImplementation(libs.junit)
    // Installs the packaged profile at first run on API 28-30, where the platform does not do
    // it itself. Already on the classpath transitively; declared so the dependency is explicit.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":tv-baselineprofile"))
    implementation(libs.zxing.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)

    // Koin DI
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Image loading
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.timber)

    if (hasFirebaseConfig) {
        "playImplementation"(platform(libs.firebase.bom))
        "playImplementation"(libs.firebase.analytics)
        "playImplementation"(libs.firebase.crashlytics)
    }

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}