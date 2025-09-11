pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // maven("https://jitpack.io") // 👉 descomenta si alguna lib futura lo necesita
    }
}

rootProject.name = "SwipeClean"
include(":app")
