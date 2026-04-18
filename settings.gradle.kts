rootProject.name = "bird-platform"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "api",
    "shared:domain",
    "shared:infra",
    "contexts:identity",
    "contexts:observations",
    "contexts:species",
    "contexts:reserves",
    "contexts:bookings",
    "contexts:expeditions",
    "contexts:surveys",
    "contexts:permits",
    "contexts:notifications",
    "contexts:integrations:ebird",
    "contexts:integrations:taxonomy",
)
