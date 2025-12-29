rootProject.name = "neotool-service"
include(":common", ":security", ":app", ":assistant", ":financialdata")

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
