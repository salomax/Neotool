plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.micronaut.library")
    id("com.google.devtools.ksp")
}

repositories { mavenCentral() }

micronaut {
    version("4.9.3")
    processing {
        incremental(true)
        annotations("io.github.salomax.neotool.framework.*")
    }
}

dependencies {
    // Platform BOMs - should be api so all modules use same versions
    api(platform("io.micronaut.platform:micronaut-platform:4.9.3"))
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
    api("com.apollographql.federation:federation-graphql-java-support:5.4.0")
    api("com.graphql-java:java-dataloader:3.3.0")
    
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
    
    // Utilities - api for other modules
    api("org.apache.commons:commons-lang3:3.18.0")
    api("org.yaml:snakeyaml")
    
    // Logging - api for other modules
    api("io.github.microutils:kotlin-logging:3.0.5")
    api("net.logstash.logback:logstash-logback-encoder:7.4")
    api("ch.qos.logback:logback-classic:1.4.14")
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
    api("org.testcontainers:testcontainers:1.20.6")
    api("org.junit.jupiter:junit-jupiter:5.12.2")
    api("org.mockito.kotlin:mockito-kotlin:3.2.0")
}
