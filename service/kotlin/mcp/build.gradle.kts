plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.application")
    id("io.micronaut.aot")
    id("org.jetbrains.kotlin.plugin.jpa")
    id("com.google.devtools.ksp")
    id("com.gradleup.shadow")
}

version = "0.1.0"
group = "io.github.salomax.neotool.mcp"

micronaut {
    version("4.10.6")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.mcp.*")
    }
}

repositories { mavenCentral() }

dependencies {
    implementation(project(":common"))
    testImplementation(testFixtures(project(":common")))

    ksp("io.micronaut.data:micronaut-data-processor")
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")

    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut:micronaut-http")
    implementation("io.micronaut:micronaut-management")
    implementation("io.micronaut:micronaut-jackson-databind")

    implementation("io.micronaut.data:micronaut-data-hibernate-jpa")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor:reactor-core")

    runtimeOnly("ch.qos.logback:logback-classic")

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

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

application {
    mainClass = "io.github.salomax.neotool.mcp.Application"
}

java {
    sourceCompatibility = JavaVersion.toVersion("21")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")
}

val bankingContractsSchemaFile =
    rootProject.projectDir.parentFile.parentFile.resolve("contracts/graphql/subgraphs/banking/schema.graphqls")
require(bankingContractsSchemaFile.isFile) {
    "Missing banking contracts schema at ${bankingContractsSchemaFile.absolutePath}"
}

val assistantContractsSchemaFile =
    rootProject.projectDir.parentFile.parentFile.resolve("contracts/graphql/subgraphs/assistant/schema.graphqls")
require(assistantContractsSchemaFile.isFile) {
    "Missing assistant contracts schema at ${assistantContractsSchemaFile.absolutePath}"
}

tasks.withType<Test>().configureEach {
    systemProperty("mcp.contracts.bankingSchemaPath", bankingContractsSchemaFile.absolutePath)
    systemProperty("mcp.contracts.assistantSchemaPath", assistantContractsSchemaFile.absolutePath)
}

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

    systemProperty("ryuk.disabled", "true")
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

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

tasks.withType<Jar> {
    isZip64 = true
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveVersion.set("")
}

// MCP-specific Kover exclusions (same idea as security/common/financialdata):
// keep the coverage gate on protocol/auth/dispatch logic, not wire models,
// thin HTTP adapters, JPA embeddables, or outbound service clients.
afterEvaluate {
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    classes(
                        // Outbound adapters (cf. financialdata rest.internal / RepositoryImpl)
                        "io.github.salomax.neotool.mcp.catalog.AssistantMcpToolCatalog",
                        "io.github.salomax.neotool.mcp.dispatch.DefaultMcpBankingGraphQLClient",
                        // Thin exception handlers (cf. common exception.*Handler)
                        "io.github.salomax.neotool.mcp.security.*ExceptionHandler",
                        // Health/readiness surface
                        "io.github.salomax.neotool.mcp.http.McpHealthController",
                        "io.github.salomax.neotool.mcp.http.McpReadinessResponse",
                        // JPA embeddable id (root already excludes *Entity, not *Id)
                        "io.github.salomax.neotool.mcp.security.ConnectionRevocationId",
                    )
                }
            }
        }
    }
}
