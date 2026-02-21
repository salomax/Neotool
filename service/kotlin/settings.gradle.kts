rootProject.name = "neotool-service"
include(":common", ":common-batch", ":common-features", ":security", ":assets", ":comms", ":assistant", ":financialdata")

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
