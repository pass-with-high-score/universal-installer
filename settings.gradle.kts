pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github.topjohnwu.libsu")
                includeGroup("com.github.d4rken-org.porter-api")
            }
        }
    }
}

rootProject.name = "Universal Installer"
include(":app")
include(":core")
include(":updater")
include(":tv")
include(":tv-baselineprofile")
include(":wearos")