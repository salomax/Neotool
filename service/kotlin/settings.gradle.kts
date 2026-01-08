rootProject.name = "neotool-service"
include(":common", ":security", ":assets", "financialdata", ":assistant")

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
