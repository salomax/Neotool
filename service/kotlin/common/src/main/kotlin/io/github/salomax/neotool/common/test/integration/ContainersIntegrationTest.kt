package io.github.salomax.neotool.common.test.integration

import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration

interface PostgresIntegrationTest

interface KafkaIntegrationTest

interface MinIOIntegrationTest

// TODO implement the other containers:
// interface RedisntegrationTest

interface MicronautPropsTestContainer {
    fun micronautProps(): Map<String, String>
}

object PostgresTestContainer : MicronautPropsTestContainer {
    private val image = TestConfig.str("test.postgres.image", "postgres:18rc1-alpine")
    private val databaseName = TestConfig.str("test.postgres.db", "neotool_db")
    private val username = TestConfig.str("test.postgres.user", "neotool")
    private val password = TestConfig.str("test.postgres.pass", "neotool")
    private val reusable = TestConfig.bool("test.postgres.reuse", true)
    private val flywayEnabled = TestConfig.bool("test.postgres.flyway", true)

    val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer(DockerImageName.parse(image))
            .withDatabaseName(databaseName)
            .withUsername(username)
            .withPassword(password)
            .withReuse(reusable)
            .waitingFor(
                Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofSeconds(60)),
            )
            .withStartupAttempts(3)
            .apply { start() }
    }

    override fun micronautProps(): Map<String, String> =
        mapOf(
            "datasources.default.enabled" to "true",
            "datasources.default.url" to container.jdbcUrl,
            "datasources.default.username" to username,
            "datasources.default.password" to password,
            "datasources.default.driverClassName" to "org.postgresql.Driver",
            "jpa.default.properties.dialect" to "org.hibernate.dialect.PostgreSQLDialect",
            // Use Flyway instead of Hibernate validation
            "jpa.default.properties.hibernate.hbm2ddl.auto" to "none",
            "jpa.default.properties.hibernate.show_sql" to "false",
            "jpa.default.properties.hibernate.format_sql" to "false",
            "flyway.enabled" to flywayEnabled.toString(),
            "flyway.datasources.default.enabled" to flywayEnabled.toString(),
            "flyway.datasources.default.baseline-on-migrate" to "true",
            "flyway.datasources.default.baseline-version" to "0",
        )
}

object KafkaTestContainer : MicronautPropsTestContainer {
    private val image = TestConfig.str("test.kafka.image", "confluentinc/cp-kafka:7.6.1")
    private val reusable = TestConfig.bool("test.kafka.reuse", true)

    val container: KafkaContainer by lazy {
        KafkaContainer(DockerImageName.parse(image))
            .withReuse(reusable)
            .waitingFor(
                Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofSeconds(60)),
            )
            .withStartupAttempts(3)
            .apply { start() }
    }

    override fun micronautProps(): Map<String, String> =
        mapOf(
            "kafka.enabled" to "true",
            "kafka.bootstrap.servers" to container.bootstrapServers,
            "kafka.producers.default.bootstrap.servers" to container.bootstrapServers,
            "kafka.producers.default.key-serializer" to StringSerializer::class.java.name,
            "kafka.producers.default.value-serializer" to "io.micronaut.serde.kafka.KafkaSerdeSerializer",
        )
}

object MinIOTestContainer : MicronautPropsTestContainer {
    private val image = TestConfig.str("test.minio.image", "minio/minio:latest")
    private val reusable = TestConfig.bool("test.minio.reuse", true)
    private val bucket = TestConfig.str("test.minio.bucket", "neotool-assets")
    private val accessKey = TestConfig.str("test.minio.accessKey", "minioadmin")
    private val secretKey = TestConfig.str("test.minio.secretKey", "minioadmin")

    val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse(image))
            .withCommand("server", "/data", "--console-address", ":9001")
            .withEnv("MINIO_ROOT_USER", accessKey)
            .withEnv("MINIO_ROOT_PASSWORD", secretKey)
            .withExposedPorts(9000, 9001)
            .withReuse(reusable)
            .waitingFor(
                Wait.forListeningPort()
                    .withStartupTimeout(Duration.ofSeconds(60)),
            )
            .withStartupAttempts(3)
            .apply { start() }
    }

    fun getApiEndpoint(): String {
        return "http://${container.host}:${container.getMappedPort(9000)}"
    }

    fun getConsoleEndpoint(): String {
        return "http://${container.host}:${container.getMappedPort(9001)}"
    }

    override fun micronautProps(): Map<String, String> {
        val endpoint = getApiEndpoint()
        return mapOf(
            "asset.storage.hostname" to container.host,
            "asset.storage.port" to container.getMappedPort(9000).toString(),
            "asset.storage.useHttps" to "false",
            "asset.storage.region" to "us-east-1",
            "asset.storage.bucket" to bucket,
            "asset.storage.accessKey" to accessKey,
            "asset.storage.secretKey" to secretKey,
            "asset.storage.publicBasePath" to bucket,
            "asset.storage.forcePathStyle" to "true",
        )
    }
}
