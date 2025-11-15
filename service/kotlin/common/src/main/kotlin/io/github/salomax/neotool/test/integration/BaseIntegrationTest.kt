package io.github.salomax.neotool.test.integration

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.json.JsonMapper
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.reflect.full.isSuperclassOf

/**
 * Base integration test class that sets up Testcontainers and provides common utilities
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS) // keep container running per class
abstract class BaseIntegrationTest : TestPropertyProvider {

  override fun getProperties(): MutableMap<String, String> {
    val props = mutableMapOf<String, String>()

    if (PostgresIntegrationTest::class.isSuperclassOf(this::class)) {
      val postgres = PostgresTestContainer.container // init container
      props += PostgresTestContainer.micronautProps()
    }

    return props
  }

  fun getPgContainer(): PostgreSQLContainer<*> = PostgresTestContainer.container
//  fun getKafkaContainer(): KafkaContainer = TODO()
//  fun getRedisContainer(): RedisContainer = TODO()

  @Inject
  lateinit var applicationContext: ApplicationContext

  @Inject
  @field:Client("/")
  lateinit var httpClient: HttpClient

  @Inject
  lateinit var json: JsonMapper

  fun getServerPort(): Int {
    return applicationContext.getProperty("micronaut.server.port", Int::class.java).orElse(8080)
  }

  @BeforeEach
  fun setUp() {
    // Test PG test container
    if (PostgresIntegrationTest::class.isSuperclassOf(this::class)) {
      val pgContainer = PostgresTestContainer.container
      assertThat(pgContainer.isRunning).isTrue()
      assertThat(pgContainer.jdbcUrl).isNotNull()
      assertThat(pgContainer.firstMappedPort).isGreaterThan(0)

      // Use 'SELECT 1' check to verify database connection
      DriverManager.getConnection(pgContainer.jdbcUrl, pgContainer.username, pgContainer.password).use { conn ->
        conn.createStatement().use { st ->
          st.executeQuery("SELECT 1").use { rs ->
            assertTrue(rs.next())
            assertEquals(1, rs.getInt(1))
          }
        }
      }
    }
  }

  @AfterEach
  fun tearDown() {
    // Clean up test data if needed
    // This can be customized per test class
  }
}
