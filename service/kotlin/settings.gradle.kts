rootProject.name = "neotool-service"
include(":common", ":security", ":app", ":assistant", ":assets")

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
