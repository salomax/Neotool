// Docker build: only include projects that are copied into the image.
// The full settings.gradle.kts includes all Kotlin services; this minimal version
// avoids "project directory does not exist" when only common + banking are present.
rootProject.name = "neotool-service"
include(":common", ":banking")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
