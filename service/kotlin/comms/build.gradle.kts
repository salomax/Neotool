plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("com.google.devtools.ksp")
    id("com.gradleup.shadow")
}

version = "0.1.0"
group = "io.github.salomax.neotool.comms"

micronaut {
    version("4.10.6")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.comms.*")
    }
}

repositories { mavenCentral() }

dependencies {
    // Project dependencies
    implementation(project(":common"))
    implementation(project(":common-batch"))
    testImplementation(testFixtures(project(":common")))

    // KSP processors
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")

    // Micrometer extras not provided by :common
    implementation("io.micronaut.micrometer:micronaut-micrometer-observation")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")

    // Email
    implementation("io.micronaut.email:micronaut-email-javamail")
    implementation("com.sun.mail:jakarta.mail:2.0.2")

    // GraphQL integration
    implementation("io.micronaut.graphql:micronaut-graphql")

    // Template Engine
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.helger:ph-css:8.1.1")

    // Testing not provided by :common
    testImplementation("io.mockk:mockk:1.13.13")
}

application {
    mainClass = "io.github.salomax.neotool.comms.ApplicationKt"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
}

// Configure test task to disable Ryuk
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

// Task to run unit tests only (excludes integration tests)
tasks.register<Test>("testUnit") {
    group = "verification"
    description = "Runs unit tests only (excludes integration tests)"

    useJUnitPlatform {
        excludeTags("integration")
    }

    testLogging {
        events("passed", "skipped", "failed")
    }

    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
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

// Enable ZIP64 support for all JAR tasks to handle more than 65535 entries
tasks.withType<Jar> {
    isZip64 = true
}

// Workaround for KSP configuration cache issue
// KSP tasks are not fully compatible with Gradle configuration cache
tasks.all {
    if (name.startsWith("ksp") || name.contains("Ksp")) {
        notCompatibleWithConfigurationCache("KSP tasks are not compatible with configuration cache")
    }
}
