plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("com.google.devtools.ksp")
    id("com.gradleup.shadow")
}

micronaut {
    version("4.10.2")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.financialdata.*")
    }
}

dependencies {
    // Project dependencies
    implementation(project(":common"))
    testImplementation(testFixtures(project(":common")))

    // Financial data-specific dependencies
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // Cache
    implementation("io.micronaut.cache:micronaut-cache-caffeine")

    // KSP processors
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.data:micronaut-data-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut.data:micronaut-data-processor")
}

application {
    mainClass.set("io.github.salomax.neotool.financialdata.Application")
}

// Enable ZIP64 support for all JAR tasks to handle more than 65535 entries
tasks.withType<Jar> {
    isZip64 = true
}

// Configure test task to disable Ryuk
tasks.test {
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

kover {
    reports {
        filters {
            excludes {
                classes("io.github.salomax.neotool.financialdata.rest.internal.*")
                classes("io.github.salomax.neotool.financialdata.repo.*RepositoryImpl")
            }
        }
    }
}
