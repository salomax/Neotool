package io.github.salomax.neotool.test.integration

import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration

interface PostgresIntegrationTest
// TODO implement the other containers:
//interface KafkantegrationTest
//interface RedisntegrationTest

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
          .withStartupTimeout(Duration.ofSeconds(60))
      )
      .withStartupAttempts(3)
      .apply { start() }
  }

  override fun micronautProps(): Map<String, String> = mapOf(
    "datasources.default.enabled" to "true",
    "datasources.default.url" to container.jdbcUrl,
    "datasources.default.username" to username,
    "datasources.default.password" to password,
    "datasources.default.driverClassName" to "org.postgresql.Driver",
    "jpa.default.properties.dialect" to "org.hibernate.dialect.PostgreSQLDialect",
    "jpa.default.properties.hibernate.hbm2ddl.auto" to "none", // Use Flyway instead of Hibernate validation
    "jpa.default.properties.hibernate.show_sql" to "false",
    "jpa.default.properties.hibernate.format_sql" to "false",
    "flyway.enabled" to flywayEnabled.toString(),
    "flyway.datasources.default.enabled" to flywayEnabled.toString(),
    "flyway.datasources.default.baseline-on-migrate" to "true",
    "flyway.datasources.default.baseline-version" to "0"
  )
}
