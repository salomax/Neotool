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

    testImplementation(platform("io.micronaut.platform:micronaut-platform:4.10.6"))
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
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
