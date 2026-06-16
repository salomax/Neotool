package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.financialdata.domain.fixedincome.FixedIncomeInvalidEffectiveRateException
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@MicronautTest(startApplication = true)
@DisplayName("Fixed Income Comparison Integration Tests")
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
class FixedIncomeComparisonIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    private fun graphQLQuery(
        query: String,
        variables: Map<String, Any>? = null,
    ): Map<String, Any> =
        mapOf("query" to query) + if (variables != null) mapOf("variables" to variables) else emptyMap()

    @Test
    fun `fixedIncomeComparison omits committed lane from rows and series after iof window`() {
        val query =
            """
            query Compare(${'$'}input: FixedIncomeComparisonInput!) {
              fixedIncomeComparison(input: ${'$'}input) {
                summary {
                  baseProduct
                  baseProductGroup
                  baseRateModel
                  baseEquivalentRateBps
                  baseRateDisplay
                  principal
                  gross
                  ir
                  net
                  realGainPercent
                }
                rows {
                  label
                  productGroup
                  rateModel
                  equivalentRateBps
                  isBase
                  net
                }
                performanceSeries {
                  productGroup
                  points { date netValue }
                }
                methodology {
                  dayCount {
                    accrualConvention
                    calendarDays
                    businessDays
                  }
                  tax {
                    appliesOnProfitOnly
                    activeBracket {
                      fromCalendarDays
                      toCalendarDays
                      rateDecimal
                    }
                    bracketTable {
                      fromCalendarDays
                      toCalendarDays
                      rateDecimal
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "productGroup" to "TAXABLE_IR",
                        "principal" to 10000,
                        "startDate" to "2024-01-01",
                        "endDate" to "2024-12-26",
                        "rateModel" to "CDI",
                        "contractedRateBps" to 10000,
                        "cdiAnnualRateBps" to 1200,
                        "ipcaAnnualRateBps" to 600,
                    ),
            )

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", graphQLQuery(query, variables))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        jsonBody["errors"].assertNoErrors()

        val data = jsonBody["data"]?.get("fixedIncomeComparison")
        assertThat(data).isNotNull()
        assertThat(data?.get("summary")?.get("baseProduct")?.stringValue).isEqualTo("CDB")
        assertThat(data?.get("summary")?.get("baseProductGroup")?.stringValue).isEqualTo("TAXABLE_IR")
        assertThat(data?.get("summary")?.get("baseRateModel")?.stringValue).isEqualTo("CDI")
        assertThat(data?.get("rows")?.isArray).isTrue()
        assertThat(data?.get("rows")?.size()).isEqualTo(6)
        assertThat(data?.get("performanceSeries")?.isArray).isTrue()
        assertThat(data?.get("performanceSeries")?.size()).isEqualTo(2)
        assertThat(
            data?.get("methodology")?.get("dayCount")?.get("accrualConvention")?.stringValue,
        ).isEqualTo("BUSINESS_252")
        assertThat(data?.get("methodology")?.get("tax")?.get("appliesOnProfitOnly")?.booleanValue).isTrue()
        assertThat(data?.get("methodology")?.get("tax")?.get("bracketTable")?.size()).isEqualTo(4)

        val groups =
            (0 until 2).mapNotNull { data?.get("performanceSeries")?.get(it)?.get("productGroup")?.stringValue }.toSet()
        assertThat(groups).containsExactlyInAnyOrder("TAXABLE_IR", "EXEMPT_IR")
        val rowGroups =
            (0 until 6).mapNotNull { data?.get("rows")?.get(it)?.get("productGroup")?.stringValue }.toSet()
        assertThat(rowGroups).containsExactlyInAnyOrder("TAXABLE_IR", "EXEMPT_IR")
        (0 until 2).forEach { idx ->
            val points = data?.get("performanceSeries")?.get(idx)?.get("points")
            assertThat(points?.isArray).isTrue()
            assertThat(points?.size()).isGreaterThanOrEqualTo(2)
        }

        val taxableSeries =
            (0 until 2).mapNotNull { data?.get("performanceSeries")?.get(it) }
                .first { it.get("productGroup")?.stringValue == "TAXABLE_IR" }
        val exemptSeries =
            (0 until 2).mapNotNull { data?.get("performanceSeries")?.get(it) }
                .first { it.get("productGroup")?.stringValue == "EXEMPT_IR" }
        val taxablePoints = taxableSeries.get("points")
        val exemptPoints = exemptSeries.get("points")
        val taxableFinal = taxablePoints?.get((taxablePoints.size() ?: 1) - 1)?.get("netValue")?.doubleValue
        val exemptFinal = exemptPoints?.get((exemptPoints.size() ?: 1) - 1)?.get("netValue")?.doubleValue
        assertThat(taxableFinal).isNotNull()
        assertThat(exemptFinal).isNotNull()
        assertThat(taxableFinal).isBetween(exemptFinal!! - 0.5, exemptFinal + 0.5)

        val baseCell =
            (0 until 6).map { data?.get("rows")?.get(it) }
                .firstOrNull { it?.get("isBase")?.booleanValue == true }
        assertThat(baseCell).isNotNull()
        assertThat(baseCell?.get("productGroup")?.stringValue).isEqualTo("TAXABLE_IR")
        assertThat(baseCell?.get("rateModel")?.stringValue).isEqualTo("CDI")
    }

    @Test
    fun `fixedIncomeComparison keeps committed lane in rows during iof window`() {
        val query =
            """
            query Compare(${'$'}input: FixedIncomeComparisonInput!) {
              fixedIncomeComparison(input: ${'$'}input) {
                rows {
                  productGroup
                  rateModel
                }
                performanceSeries {
                  productGroup
                }
              }
            }
            """.trimIndent()

        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "productGroup" to "TAXABLE_IR",
                        "principal" to 10000,
                        "startDate" to "2024-01-01",
                        "endDate" to "2024-01-11",
                        "rateModel" to "CDI",
                        "contractedRateBps" to 10000,
                        "cdiAnnualRateBps" to 1200,
                        "ipcaAnnualRateBps" to 600,
                    ),
            )

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", graphQLQuery(query, variables))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldHaveNonEmptyBody().shouldBeJson()

        val jsonBody: JsonNode = json.read(response)
        jsonBody["errors"].assertNoErrors()

        val data = jsonBody["data"]?.get("fixedIncomeComparison")
        assertThat(data).isNotNull()
        assertThat(data?.get("rows")?.size()).isEqualTo(9)
        assertThat(data?.get("performanceSeries")?.size()).isEqualTo(3)

        val rowGroups =
            (0 until 9).mapNotNull { data?.get("rows")?.get(it)?.get("productGroup")?.stringValue }.toSet()
        val seriesGroups =
            (0 until 3).mapNotNull { data?.get("performanceSeries")?.get(it)?.get("productGroup")?.stringValue }.toSet()

        assertThat(rowGroups).containsExactlyInAnyOrder("TAXABLE_IR", "TAXABLE_IR_NO_IOF", "EXEMPT_IR")
        assertThat(seriesGroups).containsExactlyInAnyOrder("TAXABLE_IR", "TAXABLE_IR_NO_IOF", "EXEMPT_IR")
    }

    @Test
    fun `fixedIncomeComparison returns error for invalid input`() {
        val query =
            """
            query Compare(${'$'}input: FixedIncomeComparisonInput!) {
              fixedIncomeComparison(input: ${'$'}input) {
                summary { baseProduct }
              }
            }
            """.trimIndent()

        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "productGroup" to "TAXABLE_IR",
                        "principal" to -1,
                        "startDate" to "2024-01-01",
                        "endDate" to "2024-12-26",
                        "rateModel" to "CDI",
                        "contractedRateBps" to 10000,
                        "cdiAnnualRateBps" to 1200,
                        "ipcaAnnualRateBps" to 600,
                    ),
            )

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", graphQLQuery(query, variables))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldBeJson()
        val jsonBody: JsonNode = json.read(response)
        val errors = jsonBody["errors"]
        assertThat(errors?.isArray).isTrue()
        assertThat(errors?.size()).isGreaterThan(0)
        assertThat(
            errors?.get(0)?.get("extensions")?.get("code")?.stringValue,
        ).isEqualTo(CommonErrorCode.INVALID_INPUT.code)
    }

    @Test
    fun `fixedIncomeComparison returns specific code for invalid effective annual rate`() {
        val query =
            """
            query Compare(${'$'}input: FixedIncomeComparisonInput!) {
              fixedIncomeComparison(input: ${'$'}input) {
                summary { baseProduct }
              }
            }
            """.trimIndent()

        val variables =
            mapOf(
                "input" to
                    mapOf(
                        "productGroup" to "TAXABLE_IR",
                        "principal" to 10000,
                        "startDate" to "2024-01-01",
                        "endDate" to "2024-12-26",
                        "rateModel" to "FIXED",
                        "contractedRateBps" to -10000,
                        "cdiAnnualRateBps" to 1200,
                        "ipcaAnnualRateBps" to 600,
                    ),
            )

        val response =
            httpClient.exchangeAsString(
                HttpRequest.POST("/graphql", graphQLQuery(query, variables))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON),
            )

        response.shouldBeSuccessful().shouldBeJson()
        val jsonBody: JsonNode = json.read(response)
        val errors = jsonBody["errors"]
        assertThat(errors?.isArray).isTrue()
        assertThat(errors?.size()).isGreaterThan(0)
        assertThat(
            errors?.get(0)?.get("extensions")?.get("code")?.stringValue,
        ).isEqualTo(FixedIncomeInvalidEffectiveRateException.CODE)
        assertThat(errors?.get(0)?.get("message")?.stringValue)
            .isEqualTo(FixedIncomeInvalidEffectiveRateException.DEFAULT_MESSAGE)
    }
}
