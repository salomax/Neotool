rootProject.name = "neotool-service"
include(":common", ":common-batch", ":common-features", ":security", ":assets", ":financialdata", ":assistant")

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
