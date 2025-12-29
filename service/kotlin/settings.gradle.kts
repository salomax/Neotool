rootProject.name = "neotool-service"
include(":common", ":security", ":app", ":assistant", ":financial_data")

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
