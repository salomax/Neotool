plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.library")
}

repositories { mavenCentral() }

micronaut {
    version("4.10.6")
}

dependencies {
    api(project(":common"))
    api("io.getunleash:unleash-client-java:9.3.0")
}

// No tests in this module; disable Kover verification (same as assistant)
afterEvaluate {
    tasks.named("koverVerify") {
        enabled = false
    }
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            verify {
                rule {
                    minBound(0)
                }
            }
        }
    }
}
