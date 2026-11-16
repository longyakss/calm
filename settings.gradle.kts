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
    }
}

rootProject.name = "Calm"

include(":app")
include(":core:common")
include(":core:settings")
include(":data:local")
include(":core:downloader")
include(":core:browser")
include(":core:filemanager")
include(":core:player")
include(":core:reader")
