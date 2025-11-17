
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
        annotations("io.github.salomax.neotool.security.*")
    }
}

dependencies {
    // Project dependencies
    implementation(project(":common"))

    // Security-specific dependencies
    implementation("jakarta.annotation:jakarta.annotation-api")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // KSP processors
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.data:micronaut-data-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")
    kspTest("io.micronaut.data:micronaut-data-processor")

    // JWT dependencies
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    // Password hashing - Argon2id
    implementation("com.password4j:password4j:1.8.0")
}

application {
  mainClass.set("io.github.salomax.neotool.security.Application")
}

// Configure test task to disable Ryuk
tasks.test {
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
