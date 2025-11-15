rootProject.name = "neotool-service"
include(":common", ":security", ":app")

pluginManagement {
  repositories {
    gradlePluginPortal();
    mavenCentral();
    google()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral();
    google()
  }
}
