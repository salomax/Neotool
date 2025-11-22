rootProject.name = "neotool-service"
include(":common", ":security", ":app", ":assistant")

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
