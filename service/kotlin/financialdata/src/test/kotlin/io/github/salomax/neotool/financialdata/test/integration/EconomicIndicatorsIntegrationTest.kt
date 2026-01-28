package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Economic Indicators Integration Tests")
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
class EconomicIndicatorsIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var entityManager: EntityManager

    private lateinit var indicatorId: UUID
    private lateinit var variationId: UUID

    @BeforeEach
    fun setUpTestData() {
        indicatorId = UUID.randomUUID()
        variationId = UUID.randomUUID()

        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.indicator
                (id, country_code, label, issuer, description, frequency, unit, is_active)
                VALUES (:id, 'BR', 'TEST_INDICATOR', 'TEST_SOURCE', 'Test indicator', 'monthly', '%', true)
                """.trimIndent(),
            ).setParameter("id", indicatorId).executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.indicator_variation
                (id, indicator_id, label, unit, is_default, incremental_months)
                VALUES (:id, :indicatorId, 'MONTHLY_CHANGE', '%', true, 6)
                """.trimIndent(),
            ).setParameter("id", variationId)
                .setParameter("indicatorId", indicatorId)
                .executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.indicator_value
                (indicator_variation_id, reference_date, value)
                VALUES
                    (:variationId, :date1, :value1),
                    (:variationId, :date2, :value2),
                    (:variationId, :date3, :value3)
                """.trimIndent(),
            ).setParameter("variationId", variationId)
                .setParameter("date1", LocalDate.of(2024, 1, 1))
                .setParameter("value1", BigDecimal("0.42"))
                .setParameter("date2", LocalDate.of(2024, 2, 1))
                .setParameter("value2", BigDecimal("0.55"))
                .setParameter("date3", LocalDate.of(2024, 3, 1))
                .setParameter("value3", BigDecimal("0.63"))
                .executeUpdate()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            entityManager.runTransaction {
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.indicator_value WHERE indicator_variation_id = :id",
                ).setParameter("id", variationId).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.indicator_variation WHERE indicator_id = :id",
                ).setParameter("id", indicatorId).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.indicator WHERE id = :id",
                ).setParameter("id", indicatorId).executeUpdate()
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    @Test
    fun `should return economic indicators via GraphQL`() {
        val query =
            """
            query {
              economicIndicators {
                label
                issuer
                variations {
                  label
                  isDefault
                }
              }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", mapOf("query" to query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        jsonBody["errors"].assertNoErrors()

        val indicators = jsonBody["data"]?.get("economicIndicators")
        assertThat(indicators).isNotNull()
        assertThat(indicators!!.isArray).isTrue()
        var hasTestIndicator = false
        for (i in 0 until indicators.size()) {
            val label = indicators[i]["label"]?.stringValue
            if (label == "TEST_INDICATOR") {
                hasTestIndicator = true
                break
            }
        }
        assertThat(hasTestIndicator).isTrue()
    }

    @Test
    fun `should return indicator series with limit and range`() {
        val query =
            """
            query {
              indicatorSeries(
                indicator: "TEST_INDICATOR",
                variation: "MONTHLY_CHANGE",
                range: { from: "2024-01-01", to: "2024-03-31" },
                limit: 2
              ) {
                indicator
                variation
                unit
                values { referenceDate value }
              }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", mapOf("query" to query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        jsonBody["errors"].assertNoErrors()

        val series = jsonBody["data"]?.get("indicatorSeries")
        assertThat(series).isNotNull()
        assertThat(series?.get("indicator")?.stringValue).isEqualTo("TEST_INDICATOR")
        assertThat(series?.get("variation")?.stringValue).isEqualTo("MONTHLY_CHANGE")

        val values = series?.get("values")
        assertThat(values).isNotNull()
        assertThat(values!!.isArray).isTrue()
        assertThat(values.size()).isEqualTo(2)
        assertThat(values[0]["referenceDate"]?.stringValue).isEqualTo("2024-02-01")
        assertThat(values[1]["referenceDate"]?.stringValue).isEqualTo("2024-03-01")
    }

    @Test
    fun `should return indicator latest value`() {
        val query =
            """
            query {
              indicatorLatest(indicator: "TEST_INDICATOR", variation: "MONTHLY_CHANGE") {
                referenceDate
                value
              }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", mapOf("query" to query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        jsonBody["errors"].assertNoErrors()

        val latest = jsonBody["data"]?.get("indicatorLatest")
        assertThat(latest).isNotNull()
        assertThat(latest?.get("referenceDate")?.stringValue).isEqualTo("2024-03-01")
    }

    @Test
    fun `should return error for invalid limit`() {
        val query =
            """
            query {
              indicatorSeries(
                indicator: "TEST_INDICATOR",
                variation: "MONTHLY_CHANGE",
                limit: 0
              ) {
                indicator
              }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", mapOf("query" to query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        val errors = jsonBody["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
        assertThat(errors.size()).isGreaterThan(0)
    }

    @Test
    fun `should return error for unknown indicator variation`() {
        val query =
            """
            query {
              indicatorLatest(indicator: "UNKNOWN", variation: "RATE") {
                referenceDate
                value
              }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", mapOf("query" to query))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        val errors = jsonBody["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
        assertThat(errors.size()).isGreaterThan(0)
    }
}
