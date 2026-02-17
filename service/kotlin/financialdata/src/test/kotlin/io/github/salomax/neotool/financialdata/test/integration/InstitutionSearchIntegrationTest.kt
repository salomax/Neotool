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
import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.model.BacenInstitutionId
import io.github.salomax.neotool.financialdata.repo.BacenInstitutionRepository
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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.security.MessageDigest
import java.util.UUID

/**
 * Integration tests for institution search GraphQL queries.
 * Tests autocomplete, full search, pagination, financial metrics, cursor handling, and institution type.
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

    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUpTestData() {
        // 1. bacen_institution: 12 for pagination; 001=FINANCIAL_GROUP, 002=PRUDENTIAL_GROUP, 003=INDIVIDUAL
        val codes = (1..12).map { "%03d".format(it) }
        val names =
            listOf(
                "Banco do Brasil S.A.",
                "Banco Santander (Brasil) S.A.",
                "Caixa Econômica Federal",
                "Inst 004", "Inst 005", "Inst 006", "Inst 007", "Inst 008",
                "Inst 009", "Inst 010", "Inst 011", "Inst 012",
            )
        codes.forEachIndexed { i, cod ->
            val entity =
                BacenInstitutionEntity(
                    id = BacenInstitutionId(bacenCodInst = cod, anoMesRef = "202403"),
                    sourceHash = calculateHash(cod, names[i], "202403"),
                    nomeInstituicao = names[i],
                    situacao = 'A',
                    codConglomeradoFinanceiro = if (cod == "001") "001" else null,
                    codConglomeradoPrudencial = if (cod == "002") "002" else null,
                    tipoConsolidacao = if (cod == "003") 'I' else null,
                )
            bacenInstitutionRepository.save(entity)
        }

        entityManager.runTransaction {
            // 2. bacen_inst_report_catalog
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_catalog (numero_relatorio, nome_relatorio)
                VALUES ('50', 'Demonstração') ON CONFLICT (numero_relatorio) DO NOTHING
                """.trimIndent(),
            ).executeUpdate()

            // 3. bacen_inst_report_account_canonical: LUCRO_LIQUIDO, PATRIMONIO_LIQUIDO for profit/equity
            val lucroId = UUID.randomUUID()
            val patrId = UUID.randomUUID()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account_canonical (id, normalized_name, display_name)
                VALUES (:id, 'LUCRO_LIQUIDO', 'Lucro')
                """.trimIndent(),
            ).setParameter("id", lucroId).executeUpdate()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account_canonical (id, normalized_name, display_name)
                VALUES (:id, 'PATRIMONIO_LIQUIDO', 'Patrimônio')
                """.trimIndent(),
            ).setParameter("id", patrId).executeUpdate()

            // 4. bacen_inst_report_account (2 rows)
            val accLucro = UUID.randomUUID()
            val accPatr = UUID.randomUUID()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account
                (id, grupo, conta, nome_coluna, descricao_coluna, canonical_account_id)
                VALUES (:id, '1', '1.1', 'lucro', 'Lucro', :cid)
                """.trimIndent(),
            ).setParameter("id", accLucro).setParameter("cid", lucroId).executeUpdate()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account
                (id, grupo, conta, nome_coluna, descricao_coluna, canonical_account_id)
                VALUES (:id, '1', '1.2', 'patrimonio', 'Patrimônio', :cid)
                """.trimIndent(),
            ).setParameter("id", accPatr).setParameter("cid", patrId).executeUpdate()

            // 5. bacen_inst_report + bacen_inst_report_data per institution
            codes.forEach { cod ->
                val reportId = UUID.randomUUID()
                entityManager.createNativeQuery(
                    """
                    INSERT INTO financial_data.bacen_inst_report (id, cod_inst, ano_mes, numero_relatorio, tipo_instituicao)
                    VALUES (:id, :cod, '202403', '50', 1)
                    """.trimIndent(),
                ).setParameter("id", reportId).setParameter("cod", cod).executeUpdate()
                entityManager.createNativeQuery(
                    """
                    INSERT INTO financial_data.bacen_inst_report_data (inst_report_id, account_id, saldo)
                    VALUES (:rid, :aid, 100.0)
                    """.trimIndent(),
                ).setParameter("rid", reportId).setParameter("aid", accLucro).executeUpdate()
                entityManager.createNativeQuery(
                    """
                    INSERT INTO financial_data.bacen_inst_report_data (inst_report_id, account_id, saldo)
                    VALUES (:rid, :aid, 500.0)
                    """.trimIndent(),
                ).setParameter("rid", reportId).setParameter("aid", accPatr).executeUpdate()
            }
        }
    }

    @AfterEach
    fun cleanupTestData() {
        try {
            entityManager.runTransaction {
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.bacen_inst_report",
                ).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.bacen_inst_report_account WHERE grupo='1' AND conta IN ('1.1','1.2')",
                ).executeUpdate()
                entityManager.createNativeQuery(
                    """
                    DELETE FROM financial_data.bacen_inst_report_account_canonical
                    WHERE normalized_name IN ('LUCRO_LIQUIDO','PATRIMONIO_LIQUIDO')
                    """.trimIndent(),
                ).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.bacen_inst_report_catalog WHERE numero_relatorio='50'",
                ).executeUpdate()
            }
            bacenInstitutionRepository.deleteAll()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun calculateHash(
        bacenCodInst: String,
        nomeInstituicao: String,
        anoMesRef: String,
    ): String {
        val data = "$bacenCodInst|$nomeInstituicao|$anoMesRef"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun graphQLQuery(
        query: String,
        variables: Map<String, Any>? = null,
    ): Map<String, Any> {
        return mapOf("query" to query) + if (variables != null) mapOf("variables" to variables) else emptyMap()
    }

    @Test
    fun `should search institutions autocomplete via GraphQL`() {
        val query =
            """
            query SearchAutocomplete(${'$'}query: String!) {
                searchBacenInstitutionsAutocomplete(query: ${'$'}query) {
                    suggestions {
                        name
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
        val query =
            """
            query SearchInstitutions(${'$'}query: String, ${'$'}first: Int, ${'$'}after: String) {
                bacenInstitutions(query: ${'$'}query, first: ${'$'}first, after: ${'$'}after) {
                    edges {
                        node {
                            id
                            name
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
        val query =
            """
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
        val query =
            """
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
        val query =
            """
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
        val query =
            """
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
        val query =
            """
            query SearchAutocomplete(${'$'}query: String!) {
                searchBacenInstitutionsAutocomplete(query: ${'$'}query) {
                    suggestions {
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
        val query =
            """
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
        val query =
            """
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

    @Test
    fun `cursor pagination with result mapping covers bacenInstitutions result mapping and cursor handling`() {
        val query =
            """
            query(${'$'}first: Int, ${'$'}after: String, ${'$'}anoMes: String) {
                bacenInstitutions(first: ${'$'}first, after: ${'$'}after, anoMes: ${'$'}anoMes) {
                    edges {
                        node {
                            id
                            name
                            profit
                            equity
                            institutionType
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

        val firstVars = mutableMapOf<String, Any>("first" to 3, "anoMes" to "202403")
        val firstRequest =
            HttpRequest
                .POST("/graphql", graphQLQuery(query, firstVars))
                .contentType(MediaType.APPLICATION_JSON)
        val firstResponse = httpClient.exchangeAsString(firstRequest)
        firstResponse.shouldBeSuccessful().shouldBeJson()

        val firstPayload: JsonNode = json.read(firstResponse)
        firstPayload["errors"].assertNoErrors()

        val firstData = firstPayload["data"]
        val firstConn = firstData["bacenInstitutions"]
        val firstEdges = firstConn["edges"]
        val firstPageInfo = firstConn["pageInfo"]

        assertThat(firstEdges.isArray).isTrue()
        assertThat(firstEdges.size()).isEqualTo(3)
        assertThat(firstPageInfo["hasNextPage"].booleanValue).isTrue()
        val endCursor = firstPageInfo["endCursor"]
        assertThat(endCursor).isNotNull()
        assertThat(endCursor.stringValue).isNotBlank()

        val secondRequest =
            HttpRequest
                .POST(
                    "/graphql",
                    graphQLQuery(
                        query,
                        mapOf("first" to 3, "after" to endCursor.stringValue, "anoMes" to "202403"),
                    ),
                )
                .contentType(MediaType.APPLICATION_JSON)
        val secondResponse = httpClient.exchangeAsString(secondRequest)
        secondResponse.shouldBeSuccessful().shouldBeJson()

        val secondPayload: JsonNode = json.read(secondResponse)
        secondPayload["errors"].assertNoErrors()

        val secondConn = secondPayload["data"]["bacenInstitutions"]
        val secondEdges = secondConn["edges"]
        assertThat(secondEdges.isArray).isTrue()
        assertThat(secondEdges.size()).isGreaterThan(0)

        val firstIds = (0 until firstEdges.size()).map { firstEdges[it]["node"]["id"].stringValue }.toSet()
        val secondIds = (0 until secondEdges.size()).map { secondEdges[it]["node"]["id"].stringValue }.toSet()
        assertThat(firstIds).doesNotContainAnyElementsOf(secondIds)
    }

    @Test
    fun `institution type calculation covers calculateInstitutionType`() {
        val query =
            """
            query(${'$'}anoMes: String) {
                bacenInstitutions(anoMes: ${'$'}anoMes, first: 20) {
                    edges {
                        node {
                            id
                            institutionType
                        }
                    }
                }
            }
            """.trimIndent()

        val request =
            HttpRequest
                .POST("/graphql", graphQLQuery(query, mapOf("anoMes" to "202403")))
                .contentType(MediaType.APPLICATION_JSON)
        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val edges = payload["data"]["bacenInstitutions"]["edges"]
        assertThat(edges.isArray).isTrue()

        val byId =
            (0 until edges.size()).associate {
                edges[it]["node"]["id"].stringValue to
                    edges[it]["node"]["institutionType"]?.stringValue
            }
        assertThat(byId["001"]).isEqualTo("FINANCIAL_GROUP")
        assertThat(byId["002"]).isEqualTo("PRUDENTIAL_GROUP")
        assertThat(byId["003"]).isEqualTo("INDIVIDUAL_INSTITUTION")
    }

    @Test
    fun `bacenInstitutionSlugs returns id name updatedAt with relay pagination`() {
        val query =
            """
            query SitemapInstitutionSlugs(${'$'}first: Int, ${'$'}after: String, ${'$'}anoMes: String) {
                bacenInstitutionSlugs(first: ${'$'}first, after: ${'$'}after, anoMes: ${'$'}anoMes) {
                    edges {
                        node {
                            id
                            name
                            updatedAt
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

        val request =
            HttpRequest.POST(
                "/graphql",
                graphQLQuery(query, mapOf("first" to 5)),
            )
                .contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()

        val connection = data["bacenInstitutionSlugs"]
        assertThat(connection).isNotNull()

        val edges = connection["edges"]
        assertThat(edges.isArray).isTrue()
        assertThat(edges.size()).isGreaterThan(0)

        val firstEdge = edges[0]
        val node = firstEdge["node"]
        assertThat(node["id"]).isNotNull()
        assertThat(node["name"]).isNotNull()
        assertThat(node.get("updatedAt")).isNotNull()
        assertThat(firstEdge["cursor"]).isNotNull()

        val pageInfo = connection["pageInfo"]
        assertThat(pageInfo["hasNextPage"]).isNotNull()
    }
}
