pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "kosha-android"
include(":app", ":core:model", ":core:network", ":core:data", ":core:design", ":core:ui")
include(":feature:auth", ":feature:add", ":feature:overview", ":feature:activity", ":feature:recurring")
include(":feature:accounts", ":feature:statements", ":feature:settings", ":feature:welcome")
