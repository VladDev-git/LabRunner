rootProject.name = "LabRunner"

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
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

// Shared API contracts (DTO) used by the server and the mobile client.
include(":shared:contracts")

// Server-side containers (built into Docker images).
include(":server:api")
include(":server:orchestrator")

// The mobile client needs the Android SDK, which is not present inside the
// server Docker build. Dockerfiles set LABRUNNER_SERVER_ONLY=true to skip it.
val serverOnly = providers.environmentVariable("LABRUNNER_SERVER_ONLY").orNull == "true"
if (!serverOnly) {
    include(":composeApp")
}
