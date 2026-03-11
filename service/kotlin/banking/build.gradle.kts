plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("com.google.devtools.ksp")
    id("com.gradleup.shadow")
}

version = "0.1.0"
group = "io.github.salomax.neotool.banking"

micronaut {
    version("4.10.6")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.banking.*")
    }
}

repositories { mavenCentral() }

dependencies {
    // Project dependencies
    implementation(project(":common"))
    testImplementation(testFixtures(project(":common")))

    // KSP processors
    ksp("io.micronaut.data:micronaut-data-processor")
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")

    // Micronaut Core
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http")
    implementation("io.micronaut:micronaut-management")

    // GraphQL
    implementation("io.micronaut.graphql:micronaut-graphql")
    implementation("com.graphql-java:graphql-java-extended-scalars:21.0")

    // Database
    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // YAML parsing
    implementation("org.yaml:snakeyaml:2.2")

    // Excel extraction (XLSX/XLS)
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    // Kafka (import file requested / job completed topics and consumer)
    implementation("io.micronaut.kafka:micronaut-kafka")

    // Reactor (in-process event bus for import job status SSE)
    implementation("io.projectreactor:reactor-core")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

    // Testing
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass = "io.github.salomax.neotool.banking.Application"
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

// Strip version from shadow JAR filename so Dockerfile doesn't need updating on each release
tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveVersion.set("")
}

// Banking-specific Kover exclusions:
// keep coverage gate focused on business logic, excluding transport/adapters.
afterEvaluate {
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    classes(
                        "io.github.salomax.neotool.banking.graphql.*",
                        "io.github.salomax.neotool.banking.infra.*",
                        "io.github.salomax.neotool.banking.infrastructure.*",
                    )
                }
            }
        }
    }
}
