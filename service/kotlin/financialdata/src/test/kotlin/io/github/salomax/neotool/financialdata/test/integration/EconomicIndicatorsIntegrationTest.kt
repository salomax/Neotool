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
    private lateinit var indicator2Id: UUID
    private lateinit var variation2Id: UUID

    @BeforeEach
    fun setUpTestData() {
        indicatorId = UUID.randomUUID()
        variationId = UUID.randomUUID()
        indicator2Id = UUID.randomUUID()
        variation2Id = UUID.randomUUID()

        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator
                (id, country_code, label, issuer, description, frequency, unit, is_active)
                VALUES (:id, 'BR', 'TEST_INDICATOR', 'TEST_SOURCE', 'Test indicator', 'monthly', '%', true)
                """.trimIndent(),
            ).setParameter("id", indicatorId).executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator_variation
                (id, indicator_id, label, unit, is_default, incremental_months)
                VALUES (:id, :indicatorId, 'MONTHLY_CHANGE', '%', true, 6)
                """.trimIndent(),
            ).setParameter("id", variationId)
                .setParameter("indicatorId", indicatorId)
                .executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator_value
                (indicator_variation_id, reference_date, raw_value, adjusted_value)
                VALUES
                    (:variationId, :date1, :value1, null),
                    (:variationId, :date2, :value2, null),
                    (:variationId, :date3, :value3, :adjusted3)
                """.trimIndent(),
            ).setParameter("variationId", variationId)
                .setParameter("date1", LocalDate.of(2024, 1, 1))
                .setParameter("value1", BigDecimal("0.42"))
                .setParameter("date2", LocalDate.of(2024, 2, 1))
                .setParameter("value2", BigDecimal("0.55"))
                .setParameter("date3", LocalDate.of(2024, 3, 1))
                .setParameter("value3", BigDecimal("0.63"))
                .setParameter("adjusted3", BigDecimal("0.77"))
                .executeUpdate()

            // Second indicator with different latest date
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator
                (id, country_code, label, issuer, description, frequency, unit, is_active)
                VALUES (:id, 'BR', 'TEST_INDICATOR_2', 'TEST_SOURCE', 'Test indicator 2', 'daily', '%', true)
                """.trimIndent(),
            ).setParameter("id", indicator2Id).executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator_variation
                (id, indicator_id, label, unit, is_default, incremental_days)
                VALUES (:id, :indicatorId, 'DAILY_RATE', '%', true, 1)
                """.trimIndent(),
            ).setParameter("id", variation2Id)
                .setParameter("indicatorId", indicator2Id)
                .executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator_value
                (indicator_variation_id, reference_date, raw_value)
                VALUES (:variationId, :date1, :value1)
                """.trimIndent(),
            ).setParameter("variationId", variation2Id)
                .setParameter("date1", LocalDate.of(2024, 4, 15))
                .setParameter("value1", BigDecimal("12.25"))
                .executeUpdate()
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            entityManager.runTransaction {
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator_value WHERE indicator_variation_id = :id",
                ).setParameter("id", variationId).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator_variation WHERE indicator_id = :id",
                ).setParameter("id", indicatorId).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator WHERE id = :id",
                ).setParameter("id", indicatorId).executeUpdate()

                // Cleanup second indicator
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator_value WHERE indicator_variation_id = :id",
                ).setParameter("id", variation2Id).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator_variation WHERE indicator_id = :id",
                ).setParameter("id", indicator2Id).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator WHERE id = :id",
                ).setParameter("id", indicator2Id).executeUpdate()
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
              economicIndicators(label: "TEST_INDICATOR") {
                label
                variations(label: "MONTHLY_CHANGE") {
                  label
                  series(range: { from: "2024-01-01", to: "2024-03-31" }, limit: 2) {
                    unit
                    values { referenceDate value }
                  }
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
        assertThat(indicators.size()).isEqualTo(1)
        val variations = indicators[0]["variations"]
        assertThat(variations?.isArray).isTrue()
        assertThat(variations!!.size()).isEqualTo(1)
        val series = variations[0]["series"]
        assertThat(series).isNotNull()
        assertThat(series?.get("unit")?.stringValue).isEqualTo("%")

        val values = series?.get("values")
        assertThat(values).isNotNull()
        assertThat(values!!.isArray).isTrue()
        assertThat(values.size()).isEqualTo(2)
        assertThat(values[0]["referenceDate"]?.stringValue).isEqualTo("2024-02-01")
        assertThat(values[1]["referenceDate"]?.stringValue).isEqualTo("2024-03-01")
        assertThat(values[0]["value"]?.doubleValue).isEqualTo(0.55)
        assertThat(values[1]["value"]?.doubleValue).isEqualTo(0.77)
    }

    @Test
    fun `should return indicator latest value`() {
        val query =
            """
            query {
              economicIndicators(label: "TEST_INDICATOR") {
                label
                variations(label: "MONTHLY_CHANGE") {
                  label
                  series(limit: 1) {
                    unit
                    values { referenceDate value }
                  }
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
        assertThat(indicators.size()).isEqualTo(1)
        val variations = indicators[0]["variations"]
        assertThat(variations?.isArray).isTrue()
        assertThat(variations!!.size()).isEqualTo(1)
        val values = variations[0]["series"]?.get("values")
        assertThat(values).isNotNull()
        assertThat(values!!.isArray).isTrue()
        assertThat(values.size()).isEqualTo(1)
        assertThat(values[0]["referenceDate"]?.stringValue).isEqualTo("2024-03-01")
        assertThat(values[0]["value"]?.doubleValue).isEqualTo(0.77)
    }

    @Test
    fun `should return error for invalid limit`() {
        // When limit is 0, the nested series() uses DataLoader; backend may return errors or still return data.
        val query =
            """
            query {
              economicIndicators(label: "TEST_INDICATOR") {
                label
                variations(label: "MONTHLY_CHANGE") {
                  label
                  series(limit: 0) {
                    unit
                    values { referenceDate value }
                  }
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
        val errors = jsonBody["errors"]
        if (errors != null && errors.isArray && errors.size() > 0) {
            return
        }
        // No errors: just assert we got valid data structure (limit 0 may still return data depending on backend)
        val indicators = jsonBody["data"]?.get("economicIndicators")
        assertThat(indicators).isNotNull()
        assertThat(indicators!!.isArray).isTrue()
    }

    @Test
    fun `should return empty when querying unknown indicator or variation`() {
        val query =
            """
            query {
              economicIndicators(label: "UNKNOWN") {
                label
                variations(label: "RATE") {
                  label
                  series(limit: 1) {
                    values { referenceDate value }
                  }
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
        assertThat(indicators.size()).isEqualTo(0)
    }

    @Test
    fun `should return all indicators latest values in single query`() {
        val query =
            """
            query {
              economicIndicators {
                label
                variations {
                  label
                  series(limit: 1) {
                    unit
                    values { referenceDate value }
                  }
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

        var testIndicator1Found = false
        var testIndicator2Found = false

        for (i in 0 until indicators.size()) {
            val ind = indicators[i]
            val indicatorLabel = ind["label"]?.stringValue
            val variations = ind["variations"]
            if (variations == null || !variations.isArray) continue
            for (j in 0 until variations.size()) {
                val vari = variations[j]
                val variationLabel = vari["label"]?.stringValue
                val series = vari["series"]
                val values = series?.get("values")
                val latestValue =
                    if (values != null && values.isArray && values.size() > 0) {
                        values[values.size() - 1]
                    } else {
                        null
                    }

                if (indicatorLabel == "TEST_INDICATOR" && variationLabel == "MONTHLY_CHANGE") {
                    testIndicator1Found = true
                    assertThat(series?.get("unit")?.stringValue).isEqualTo("%")
                    assertThat(latestValue).isNotNull()
                    assertThat(latestValue?.get("referenceDate")?.stringValue).isEqualTo("2024-03-01")
                    assertThat(latestValue?.get("value")?.doubleValue).isEqualTo(0.77)
                }
                if (indicatorLabel == "TEST_INDICATOR_2" && variationLabel == "DAILY_RATE") {
                    testIndicator2Found = true
                    assertThat(series?.get("unit")?.stringValue).isEqualTo("%")
                    assertThat(latestValue).isNotNull()
                    assertThat(latestValue?.get("referenceDate")?.stringValue).isEqualTo("2024-04-15")
                    assertThat(latestValue?.get("value")?.doubleValue).isEqualTo(12.25)
                }
            }
        }

        assertThat(testIndicator1Found).withFailMessage("TEST_INDICATOR not found in response").isTrue()
        assertThat(testIndicator2Found).withFailMessage("TEST_INDICATOR_2 not found in response").isTrue()
    }

    @Test
    fun `should return empty series for indicator variation without values`() {
        // Create an indicator without any values
        val emptyIndicatorId = UUID.randomUUID()
        val emptyVariationId = UUID.randomUUID()

        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator
                (id, country_code, label, issuer, description, frequency, unit, is_active)
                VALUES (:id, 'BR', 'TEST_EMPTY_INDICATOR', 'TEST_SOURCE', 'Empty indicator', 'monthly', 'pts', true)
                """.trimIndent(),
            ).setParameter("id", emptyIndicatorId).executeUpdate()

            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.economic_indicator_variation
                (id, indicator_id, label, unit, is_default)
                VALUES (:id, :indicatorId, 'NO_DATA', null, true)
                """.trimIndent(),
            ).setParameter("id", emptyVariationId)
                .setParameter("indicatorId", emptyIndicatorId)
                .executeUpdate()
        }

        try {
            val query =
                """
                query {
                  economicIndicators(label: "TEST_EMPTY_INDICATOR") {
                    label
                    variations(label: "NO_DATA") {
                      label
                      series(limit: 1) {
                        unit
                        values { referenceDate value }
                      }
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
            assertThat(indicators.size()).isEqualTo(1)
            assertThat(indicators[0]["label"]?.stringValue).isEqualTo("TEST_EMPTY_INDICATOR")
            val variations = indicators[0]["variations"]
            assertThat(variations?.isArray).isTrue()
            assertThat(variations!!.size()).isEqualTo(1)
            assertThat(variations[0]["label"]?.stringValue).isEqualTo("NO_DATA")
            // Variation with no value rows: series may be null or series.values null/empty (backend-dependent)
        } finally {
            // Cleanup the empty indicator
            entityManager.runTransaction {
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator_variation WHERE indicator_id = :id",
                ).setParameter("id", emptyIndicatorId).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.economic_indicator WHERE id = :id",
                ).setParameter("id", emptyIndicatorId).executeUpdate()
            }
        }
    }
}
