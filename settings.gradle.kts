pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://cache-redirector.jetbrains.com/kotlin.bintray.com/kotlin-plugin")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "kotlin-language-server"

include(
    "platform",
    "shared",
    "server"
)
