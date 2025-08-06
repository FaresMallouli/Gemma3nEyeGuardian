// This file is at the root of your project

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

        // --- START OF UPDATE ---
        // This adds the 'app/libs' folder as a source for dependencies.
        // It's required for Gradle to find your local .aar files.
        flatDir {
            dirs("app/libs")
        }
        // --- END OF UPDATE ---
    }
}

rootProject.name = "EyeGuardian"
include(":app")