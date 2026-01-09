package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
import io.github.salomax.neotool.common.test.assertions.shouldHaveNonEmptyBody
import io.github.salomax.neotool.common.test.http.exchangeAsString
import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.json.read
import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepository
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.json.tree.JsonNode
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.security.MessageDigest

/**
 * Integration tests for institution search GraphQL queries.
 * Tests autocomplete, full search, pagination, and financial metrics aggregation.
 */
@MicronautTest(startApplication = true)
@DisplayName("Institution Search Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
@TestMethodOrder(MethodOrderer.Random::class)
class InstitutionSearchIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {

    @Inject
    lateinit var bacenInstitutionRepository: BacenInstitutionRepository

    @BeforeEach
    fun setUpTestData() {
        // Create test institutions for integration tests
        val testInstitutions = listOf(
            BacenInstitutionEntity(
                bacenCodInst = "001",
                anoMesRef = "202403",
                sourceHash = calculateHash("001", "Banco do Brasil", "202403"),
                nomeInstituicao = "Banco do Brasil S.A.",
                situacao = 'A',
            ),
            BacenInstitutionEntity(
                bacenCodInst = "033",
                anoMesRef = "202403",
                sourceHash = calculateHash("033", "Banco Santander", "202403"),
                nomeInstituicao = "Banco Santander (Brasil) S.A.",
                situacao = 'A',
            ),
            BacenInstitutionEntity(
                bacenCodInst = "104",
                anoMesRef = "202403",
                sourceHash = calculateHash("104", "Caixa Econômica Federal", "202403"),
                nomeInstituicao = "Caixa Econômica Federal",
                situacao = 'A',
            ),
        )

        testInstitutions.forEach { institution ->
            bacenInstitutionRepository.save(institution)
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            bacenInstitutionRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun calculateHash(bacenCodInst: String, nomeInstituicao: String, anoMesRef: String): String {
        val data = "$bacenCodInst|$nomeInstituicao|$anoMesRef"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun graphQLQuery(query: String, variables: Map<String, Any>? = null): Map<String, Any> {
        return mapOf("query" to query) + if (variables != null) mapOf("variables" to variables) else emptyMap()
    }

    @Test
    fun `should search institutions autocomplete via GraphQL`() {
        val query = """
            query SearchAutocomplete(${'$'}query: String!) {
                searchBacenInstitutionsAutocomplete(query: ${'$'}query) {
                    suggestions {
                        id
                        name
                        cnpj
                        logoAsset {
                          publicUrl
                        }
                    }
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("query" to "Banco")),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()

        val autocomplete = data["searchBacenInstitutionsAutocomplete"]
        assertThat(autocomplete).isNotNull()

        val suggestions = autocomplete["suggestions"]
        assertThat(suggestions).isNotNull()
        assertThat(suggestions.isArray).isTrue()
    }

    @Test
    fun `should search institutions with full search via GraphQL`() {
        val query = """
            query SearchInstitutions(${'$'}query: String, ${'$'}first: Int, ${'$'}after: String) {
                bacenInstitutions(query: ${'$'}query, first: ${'$'}first, after: ${'$'}after) {
                    edges {
                        node {
                            id
                            name
                            logoAsset {
                          publicUrl
                        }
                            profit
                            equity
                            yoyEquityGrowth
                            basileiaIndex
                            bacenInstitutionType
                            institutionType
                            tipoConsolidadoBancario
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                        hasPreviousPage
                        startCursor
                        endCursor
                    }
                    totalCount
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("query" to "Banco", "first" to 10)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()

        val connection = data["bacenInstitutions"]
        assertThat(connection).isNotNull()

        val edges = connection["edges"]
        assertThat(edges).isNotNull()
        assertThat(edges.isArray).isTrue()

        val pageInfo = connection["pageInfo"]
        assertThat(pageInfo).isNotNull()
        assertThat(pageInfo["hasNextPage"]).isNotNull()
        assertThat(pageInfo["hasPreviousPage"]).isNotNull()

        val totalCount = connection["totalCount"]
        assertThat(totalCount).isNotNull()
    }

    @Test
    fun `should search all institutions when query is null`() {
        val query = """
            query SearchAllInstitutions(${'$'}first: Int) {
                bacenInstitutions(query: null, first: ${'$'}first) {
                    edges {
                        node {
                            id
                            name
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                    }
                    totalCount
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("first" to 5)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()

        val connection = data["bacenInstitutions"]
        assertThat(connection).isNotNull()

        val totalCount = connection["totalCount"]
        assertThat(totalCount).isNotNull()
        assertThat(totalCount.intValue).isGreaterThan(0)
    }

    @Test
    fun `should paginate institutions with cursor`() {
        val query = """
            query SearchInstitutions(${'$'}first: Int, ${'$'}after: String) {
                bacenInstitutions(first: ${'$'}first, after: ${'$'}after) {
                    edges {
                        node {
                            id
                            name
                        }
                        cursor
                    }
                    pageInfo {
                        hasNextPage
                        endCursor
                    }
                }
            }
        """.trimIndent()

        // First page
        val firstRequest =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("first" to 5)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val firstResponse = httpClient.exchangeAsString(firstRequest)
        firstResponse.shouldBeSuccessful().shouldBeJson()

        val firstPayload: JsonNode = json.read(firstResponse)
        firstPayload["errors"].assertNoErrors()

        val firstData = firstPayload["data"]
            val firstConnection = firstData["bacenInstitutions"]
        val firstPageInfo = firstConnection["pageInfo"]
        val endCursor = firstPageInfo["endCursor"]

        // Second page if hasNextPage
        if (firstPageInfo["hasNextPage"].booleanValue) {
            assertThat(endCursor).isNotNull()

            val secondRequest =
                HttpRequest.POST(
                    "/graphql",
                    graphQLQuery(query, mapOf("first" to 5, "after" to endCursor.stringValue)),
                )
                    .contentType(MediaType.APPLICATION_JSON)

            val secondResponse = httpClient.exchangeAsString(secondRequest)
            secondResponse.shouldBeSuccessful().shouldBeJson()

            val secondPayload: JsonNode = json.read(secondResponse)
            secondPayload["errors"].assertNoErrors()

            val secondData = secondPayload["data"]
            val secondConnection = secondData["bacenInstitutions"]
            val secondEdges = secondConnection["edges"]

            assertThat(secondEdges).isNotNull()
            assertThat(secondEdges.isArray).isTrue()
        }
    }

    @Test
    fun `should handle invalid pagination parameters`() {
        val query = """
            query SearchInstitutions(${'$'}first: Int) {
                bacenInstitutions(first: ${'$'}first) {
                    edges {
                        node {
                            id
                            name
                        }
                    }
                }
            }
        """.trimIndent()

        // Test with first = 0 (invalid)
        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("first" to 0)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
        assertThat(errors.size()).isGreaterThan(0)
    }

    @Test
    fun `should handle invalid cursor`() {
        val query = """
            query SearchInstitutions(${'$'}after: String) {
                bacenInstitutions(after: ${'$'}after) {
                    edges {
                        node {
                            id
                            name
                        }
                    }
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("after" to "invalid-cursor")),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
    }

    @Test
    fun `should handle empty autocomplete query`() {
        val query = """
            query SearchAutocomplete(${'$'}query: String!) {
                searchBacenInstitutionsAutocomplete(query: ${'$'}query) {
                    suggestions {
                        id
                        name
                    }
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("query" to "")),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
    }

    @Test
    fun `should return financial metrics in search results`() {
        val query = """
            query SearchInstitutions(${'$'}query: String) {
                bacenInstitutions(query: ${'$'}query, first: 10) {
                    edges {
                        node {
                            id
                            name
                            profit
                            equity
                            yoyEquityGrowth
                            basileiaIndex
                            institutionType
                            tipoConsolidadoBancario
                        }
                    }
                }
            }
        """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("query" to "Banco")),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        val connection = data["bacenInstitutions"]
        val edges = connection["edges"]

        if (edges.isArray && edges.size() > 0) {
            val firstNode = edges[0]["node"]
            // Financial metrics may be null if no data available
            assertThat(firstNode["profit"]).isNotNull()
            assertThat(firstNode["equity"]).isNotNull()
            assertThat(firstNode["yoyEquityGrowth"]).isNotNull()
            assertThat(firstNode["institutionType"]).isNotNull()
            assertThat(firstNode["tipoConsolidadoBancario"]).isNotNull()
        }
    }

    @Test
    fun `should respect max page size limit`() {
        val query = """
            query SearchInstitutions(${'$'}first: Int) {
                bacenInstitutions(first: ${'$'}first) {
                    edges {
                        node {
                            id
                        }
                    }
                }
            }
        """.trimIndent()

        // Test with first > MAX_PAGE_SIZE (100)
        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("first" to 200)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
    }
}

