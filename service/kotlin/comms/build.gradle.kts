plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("org.jetbrains.kotlin.plugin.jpa")
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

    // Add Micronaut Data KSP processor
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
    implementation("io.micronaut.validation:micronaut-validation")

    // Kafka
    implementation("io.micronaut.kafka:micronaut-kafka")

    // Micrometer
    implementation("io.micronaut.micrometer:micronaut-micrometer-observation")
    implementation("io.micronaut.micrometer:micronaut-micrometer-core")
    implementation("io.micronaut.micrometer:micronaut-micrometer-annotation")
    implementation("io.micronaut.micrometer:micronaut-micrometer-registry-prometheus")

    // Email
    implementation("io.micronaut.email:micronaut-email-javamail")
    implementation("com.sun.mail:jakarta.mail:2.0.1")

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

    // Template Engine
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.helger:ph-css:8.1.1")

    // Logging
    runtimeOnly("ch.qos.logback:logback-classic")

    // Testing
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:kafka:1.20.6")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
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
