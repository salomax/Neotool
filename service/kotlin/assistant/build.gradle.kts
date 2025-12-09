plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("com.google.devtools.ksp")
    id("com.gradleup.shadow")
}

micronaut {
    version("4.10.2")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.assistant.*")
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Project dependencies
    implementation(project(":common"))

    // KSP processors
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")

    // HTTP Client for GraphQL
    implementation("io.micronaut:micronaut-http-client")

    // JSON processing
    implementation("io.micronaut.serde:micronaut-serde-jackson")

    // Google Gen AI Java SDK
    implementation("com.google.genai:google-genai:1.26.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
}

application {
    mainClass.set("io.github.salomax.neotool.assistant.Application")
}

tasks.test {
    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

// Enable ZIP64 support for all JAR tasks to handle more than 65535 entries
tasks.withType<Jar> {
    isZip64 = true
}

// Task to run integration tests
tasks.register<Test>("testIntegration") {
    group = "verification"
    description = "Runs integration tests using Testcontainers"

    useJUnitPlatform {
        includeEngines("junit-jupiter")
        includeTags("integration")
    }

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }

    // Disable Ryuk to avoid container startup issues
    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // Ensure Docker is available
    doFirst {
        try {
            val process = ProcessBuilder("docker", "version").start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw GradleException("Docker is required for integration tests but not available")
            }
        } catch (e: Exception) {
            throw GradleException("Docker is required for integration tests but not available: ${e.message}")
        }
    }
}

// Integration test coverage is configured in the parent build.gradle.kts
// This ensures consistent configuration across all modules with testIntegration task

// Configure Kover to disable coverage verification for assistant module
// This module doesn't require coverage verification
afterEvaluate {
    // Disable koverVerify task for assistant module
    tasks.named("koverVerify") {
        enabled = false
    }

    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    // Exclude all classes since we don't need coverage
                    classes("*")
                }
            }

            // Set threshold to 0 to effectively disable verification
            verify {
                rule {
                    minBound(0)
                }
            }
        }
    }
}
