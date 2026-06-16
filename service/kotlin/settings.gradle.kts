rootProject.name = "neotool-service"
include(":common", ":common-batch", ":common-features", ":common-finance", ":security", ":assets", ":comms", ":assistant", ":financialdata", ":banking")

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
