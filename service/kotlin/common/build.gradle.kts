plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.library")
    id("com.google.devtools.ksp")
}

repositories { mavenCentral() }

micronaut {
    version("4.10.6")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.common.*", "io.github.salomax.neotool.security.*")
    }
}

dependencies {
    // Platform BOMs - should be api so all modules use same versions
    api(platform("io.micronaut.platform:micronaut-platform:4.10.6"))
    api(platform("io.micronaut.micrometer:micronaut-micrometer-bom:5.12.0"))
    api(platform("io.micronaut.tracing:micronaut-tracing-bom:7.1.4"))

    // Core Micronaut dependencies - api so other modules can use them
    api("io.micronaut:micronaut-inject")
    api("io.micronaut:micronaut-runtime")
    api("io.micronaut:micronaut-http")
    api("io.micronaut:micronaut-http-client")
    api("io.micronaut:micronaut-http-server-netty")
    api("io.micronaut:micronaut-jackson-databind")
    api("io.micronaut:micronaut-jackson-core")
    api("io.micronaut:micronaut-json-core")

    // Kotlin support - api for other modules
    api("io.micronaut.kotlin:micronaut-kotlin-runtime")
    api("org.jetbrains.kotlin:kotlin-stdlib")
    api("org.jetbrains.kotlin:kotlin-reflect")
    api("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Validation - api for other modules
    api("io.micronaut.validation:micronaut-validation")

    // Serde - api for other modules
    api("io.micronaut.serde:micronaut-serde-jackson")
    api("io.micronaut.serde:micronaut-serde-api")
    api("io.micronaut.serde:micronaut-serde-support")

    // GraphQL - api for other modules
    api("com.graphql-java:graphql-java:21.5")
    api("com.graphql-java:graphql-java-extended-scalars:21.0")
    api("com.apollographql.federation:federation-graphql-java-support:5.4.0")
    api("com.graphql-java:java-dataloader:3.3.0")

    // JWT - shared for all modules
    api("io.jsonwebtoken:jjwt-api:0.12.5")
    api("io.jsonwebtoken:jjwt-impl:0.12.5")
    api("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Vault client for secret management
    api("com.bettercloud:vault-java-driver:5.1.0")

    // Micrometer - api for other modules (needed for metrics instrumentation)
    api("io.micronaut.micrometer:micronaut-micrometer-core")

    // Database - api for other modules
    api("io.micronaut.data:micronaut-data-jdbc")
    api("io.micronaut.data:micronaut-data-hibernate-jpa")
    api("io.micronaut.data:micronaut-data-tx-hibernate")
    api("io.micronaut.data:micronaut-data-tx-jdbc")
    api("io.micronaut.data:micronaut-data-connection-jdbc")
    api("io.micronaut.data:micronaut-data-connection-hibernate")
    api("io.micronaut.data:micronaut-data-runtime")
    api("io.micronaut.data:micronaut-data-model")
    api("io.micronaut.data:micronaut-data-connection")
    api("io.micronaut.data:micronaut-data-tx")

    api("io.micronaut.sql:micronaut-hibernate-jpa")
    api("io.micronaut.sql:micronaut-jdbc-hikari")
    api("io.micronaut.sql:micronaut-jdbc")
    api("io.micronaut.flyway:micronaut-flyway")

    api("org.hibernate.orm:hibernate-core:6.6.15.Final")
    api("org.flywaydb:flyway-database-postgresql")
    api("org.flywaydb:flyway-core")

    // Database drivers - api so other modules can use them
    api("org.postgresql:postgresql:42.7.7")

    // Kafka - api for other modules (needed for batch processing)
    api("io.micronaut.kafka:micronaut-kafka")

    // Utilities - api for other modules
    api("org.apache.commons:commons-lang3:3.18.0")
    api("commons-codec:commons-codec:1.17.1")
    api("org.yaml:snakeyaml")

    // Logging - api for other modules
    api("io.github.microutils:kotlin-logging:3.0.5")
    api("net.logstash.logback:logstash-logback-encoder:7.4")
    api("ch.qos.logback:logback-classic:1.5.23")
    api("com.github.loki4j:loki-logback-appender:1.4.0")

    // KSP processors
    ksp("io.micronaut:micronaut-inject-kotlin")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    kspTest("io.micronaut:micronaut-inject-kotlin")

    // Test dependencies - api so other modules can use them
    api("io.micronaut.test:micronaut-test-junit5:4.8.1")
    api("org.assertj:assertj-core:3.27.3")
    api("org.testcontainers:junit-jupiter:1.20.6")
    api("org.testcontainers:postgresql:1.20.6")
    api("org.testcontainers:kafka:1.20.6")
    api("org.testcontainers:testcontainers:1.20.6")
    api("org.junit.jupiter:junit-jupiter:5.12.2")
    api("org.mockito.kotlin:mockito-kotlin:3.2.0")

    api("org.mockito:mockito-inline:5.2.0")
}

// Configure Kover exclusions specific to common module
// Note: We explicitly include parent exclusions to ensure they're not overridden
afterEvaluate {
    extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
        reports {
            filters {
                excludes {
                    classes(
                        // Logging infrastructure - observability, not business logic
                        "io.github.salomax.neotool.common.logging.MDCFilter",
                        "io.github.salomax.neotool.common.logging.EnterpriseLoggingFilter",
                        "io.github.salomax.neotool.common.logging.EnterpriseLogMethodInterceptor",
                        // Exception handlers - framework integration
                        "io.github.salomax.neotool.common.exception.GraphQLOptimisticLockExceptionHandler",
                        "io.github.salomax.neotool.common.exception.OptimisticLockExceptionHandler",
                        "io.github.salomax.neotool.common.graphql.GraphQLRequest",
                        "io.github.salomax.neotool.common.graphql.GraphQLInputDTO",
                        "io.github.salomax.neotool.common.graphql.BaseInputDTO",
                        "io.github.salomax.neotool.common.graphql.InputPatterns",
                        "io.github.salomax.neotool.common.graphql.GraphQLValidations",
                        "io.github.salomax.neotool.common.graphql.GraphQLModule",
                        "io.github.salomax.neotool.common.graphql.BaseGraphQLModule",
                        "io.github.salomax.neotool.common.graphql.GraphQLModuleRegistry",
                        "io.github.salomax.neotool.common.graphql.GraphQLWiringFactory",
                        "io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry",
                        "io.github.salomax.neotool.common.graphql.BaseSchemaRegistryFactory",
                        "io.github.salomax.neotool.common.graphql.DataLoaderRegistryFactory",
                        "io.github.salomax.neotool.common.graphql.GraphQLInstrumentationFactory",
                        "io.github.salomax.neotool.common.graphql.GraphQLPayloadException",
                        "io.github.salomax.neotool.common.graphql.GraphQLResolver",
                        "io.github.salomax.neotool.common.graphql.CrudResolver",
                        "io.github.salomax.neotool.common.graphql.GenericCrudResolver",
                        "io.github.salomax.neotool.common.graphql.EnhancedCrudResolver",
                        "io.github.salomax.neotool.common.graphql.CrudService",
                        "io.github.salomax.neotool.common.graphql.pagination.GenericSortingHelper",
                        "io.github.salomax.neotool.common.graphql.pagination.PaginationConstants",
                        "io.github.salomax.neotool.common.graphql.pagination.PageInfo",
                        "io.github.salomax.neotool.common.graphql.pagination.Edge",
                        "io.github.salomax.neotool.common.graphql.pagination.Connection",
                        "io.github.salomax.neotool.common.graphql.pagination.CompositeCursor",
                        // Metrics instrumentation - observability
                        "io.github.salomax.neotool.common.metrics.GraphQLMetricsInstrumentation",
                        // Test infrastructure - utilities for testing, not production code
                        "io.github.salomax.neotool.common.test.security.JwtTestFixture",
                        "io.github.salomax.neotool.common.test.http.HttpClientExtensionKt",
                        "io.github.salomax.neotool.common.test.http.RequestBuildersKt",
                        "io.github.salomax.neotool.common.test.assertions.ResponseAssertionsKt",
                        "io.github.salomax.neotool.common.test.assertions.GraphQLAssertionsKt",
                        "io.github.salomax.neotool.common.test.integration.BaseIntegrationTest",
                        "io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest",
                        "io.github.salomax.neotool.common.test.integration.KafkaIntegrationTest",
                        "io.github.salomax.neotool.common.test.integration.MicronautPropsTestContainer",
                        "io.github.salomax.neotool.common.test.integration.PostgresTestContainer",
                        "io.github.salomax.neotool.common.test.integration.KafkaTestContainer",
                        "io.github.salomax.neotool.common.test.integration.MinIOIntegrationTest",
                        "io.github.salomax.neotool.common.test.integration.MinIOTestContainer",
                        "io.github.salomax.neotool.common.test.integration.TestConfig",
                        "io.github.salomax.neotool.common.test.transaction.TransactionExtensionsKt",
                        // Factory classes - minimal logic, mostly delegation
                        "io.github.salomax.neotool.common.security.key.KeyManagerFactory",
                    )
                }
            }
        }
    }
}
