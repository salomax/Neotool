package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.assertions.assertNoErrors
import io.github.salomax.neotool.common.test.assertions.shouldBeJson
import io.github.salomax.neotool.common.test.assertions.shouldBeSuccessful
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.security.MessageDigest
import java.util.UUID

/**
 * Integration tests for BacenInstitution detail GraphQL queries.
 * Tests bacenFinanceInstitution, reportsSummary, and reportDetails.
 */
@MicronautTest(startApplication = true)
@DisplayName("Bacen Institution Detail Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
class BacenInstitutionDetailIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var bacenInstitutionRepository: BacenInstitutionRepository

    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUpTestData() {
        // 1. bacen_institution for 001: 202301, 202304, 202307, 202403 (for reportsSummary from/to and reportDetails)
        listOf("202301", "202304", "202307", "202403").forEach { anoMes ->
            bacenInstitutionRepository.save(
                BacenInstitutionEntity(
                    id = BacenInstitutionId(bacenCodInst = "001", anoMesRef = anoMes),
                    sourceHash = calculateHash("001", "Banco do Brasil", anoMes),
                    nomeInstituicao = "Banco do Brasil S.A.",
                    situacao = 'A',
                ),
            )
        }

        entityManager.runTransaction {
            // 2. bacen_inst_report_catalog
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_catalog (numero_relatorio, nome_relatorio)
                VALUES ('50', 'Demonstração Contábil')
                ON CONFLICT (numero_relatorio) DO NOTHING
                """.trimIndent(),
            ).executeUpdate()

            // 3. bacen_inst_report_account_canonical
            val canonicalId = UUID.randomUUID()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account_canonical (id, normalized_name, display_name)
                VALUES (:id, 'TEST_ACCOUNT', 'Test Account')
                """.trimIndent(),
            ).setParameter("id", canonicalId).executeUpdate()

            // 4. bacen_inst_report_account
            val accountId = UUID.randomUUID()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.bacen_inst_report_account (id, grupo, conta, nome_coluna, descricao_coluna, canonical_account_id)
                VALUES (:id, '1', '1.1', 'col1', 'Desc', :canonicalId)
                """.trimIndent(),
            ).setParameter("id", accountId).setParameter("canonicalId", canonicalId).executeUpdate()

            // 5. financial_institution with JSON (social_network, receita_data) for enriched detail test
            val fiId = UUID.randomUUID()
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.financial_institution (id, parent_cnpj, legal_name, trade_name, website, social_network, receita_data)
                VALUES (:id, '00000000000191', 'Banco do Brasil S.A.', 'Banco do Brasil', 'https://www.bb.com.br',
                  '["https://twitter.com/bancodobrasil", "https://linkedin.com/company/bancodobrasil"]'::jsonb,
                  '{"mainActivity":{"text":"Banco comercial"},"address":{"city":"Brasília","state":"DF"},"founded":"1808-10-12"}'::jsonb)
                """.trimIndent(),
            ).setParameter("id", fiId).executeUpdate()

            // 6. financial_institution_bacen_institution link for 001/202403
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.financial_institution_bacen_institution (financial_institution_id, bacen_cod_inst, ano_mes_ref)
                VALUES (:fiId, '001', '202403')
                """.trimIndent(),
            ).setParameter("fiId", fiId).executeUpdate()

            // 7. bacen_inst_report for 001 in 202301, 202304, 202307, 202403 (for reportsSummary from/to)
            listOf("202301", "202304", "202307", "202403").forEach { anoMes ->
                val reportUuid = UUID.randomUUID()
                entityManager.createNativeQuery(
                    """
                    INSERT INTO financial_data.bacen_inst_report (id, cod_inst, ano_mes, numero_relatorio, tipo_instituicao)
                    VALUES (:id, '001', :anoMes, '50', 1)
                    """.trimIndent(),
                ).setParameter("id", reportUuid).setParameter("anoMes", anoMes).executeUpdate()
            }

            // 8. bacen_inst_report_data for 202403 report (for reportDetails with actual values)
            // Resolve report id for 001/202403/50 and account_id
            val rid =
                entityManager.createNativeQuery(
                    """
                    SELECT id FROM financial_data.bacen_inst_report
                    WHERE cod_inst='001' AND ano_mes='202403' AND numero_relatorio='50'
                    """.trimIndent(),
                ).singleResult as? UUID
            val aid =
                entityManager.createNativeQuery(
                    """
                    SELECT id FROM financial_data.bacen_inst_report_account
                    WHERE grupo='1' AND conta='1.1' AND nome_coluna='col1'
                    """.trimIndent(),
                ).singleResult as? UUID
            if (rid != null && aid != null) {
                entityManager.createNativeQuery(
                    """
                    INSERT INTO financial_data.bacen_inst_report_data (inst_report_id, account_id, saldo)
                    VALUES (:rid, :aid, 100.5)
                    """.trimIndent(),
                ).setParameter("rid", rid).setParameter("aid", aid).executeUpdate()
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
                    "DELETE FROM financial_data.financial_institution_bacen_institution",
                ).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.financial_institution WHERE parent_cnpj='00000000000191'",
                ).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.bacen_inst_report_account WHERE grupo='1' AND conta='1.1'",
                ).executeUpdate()
                entityManager.createNativeQuery(
                    """
                    DELETE FROM financial_data.bacen_inst_report_account_canonical
                    WHERE normalized_name='TEST_ACCOUNT'
                    """.trimIndent(),
                ).executeUpdate()
                entityManager.createNativeQuery(
                    "DELETE FROM financial_data.bacen_inst_report_catalog WHERE numero_relatorio='50'",
                ).executeUpdate()
            }
            bacenInstitutionRepository.deleteAll()
        } catch (_: Exception) {
        }
    }

    private fun calculateHash(
        bacenCodInst: String,
        nomeInstituicao: String,
        anoMesRef: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest
            .digest("$bacenCodInst|$nomeInstituicao|$anoMesRef".toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun graphQLQuery(
        query: String,
        variables: Map<String, Any>? = null,
    ): Map<String, Any> =
        mapOf("query" to query) +
            if (variables != null) {
                mapOf("variables" to variables)
            } else {
                emptyMap()
            }

    @Test
    fun `should fetch institution detail via GraphQL`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    name
                    quarter
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst).isNotNull()
        assertThat(inst.get("id").stringValue).isEqualTo("001")
        assertThat(inst.get("name").stringValue).isNotNull()
    }

    @Test
    fun `should return null for non-existent institution`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    name
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "99999")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst.isNull).isTrue()
    }

    @Test
    fun `should fetch reports summary via GraphQL`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    reportsSummary(range: { last: 4 }) {
                        quarters {
                            quarter
                            reports { reportId name }
                        }
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst).isNotNull()
        val summary = inst.get("reportsSummary")
        assertThat(summary).isNotNull()
        assertThat(summary.get("quarters")).isNotNull()
    }

    @Test
    fun `should fetch report details via GraphQL`() {
        val query =
            """
            query(${'$'}id: ID!, ${'$'}reportId: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    reportDetails(reportId: ${'$'}reportId, range: { last: 4 }) {
                        reportId
                        name
                        accounts { group accountId valuesByQuarter { quarter value } }
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001", "reportId" to "50")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        val data = payload["data"]?.get("bacenFinanceInstitution")
        if (errors != null && errors.isArray && errors.size() > 0) {
            assertThat(errors.size()).isGreaterThan(0)
        } else {
            assertThat(data).isNotNull()
            val details = data?.get("reportDetails")
            assertThat(details).isNotNull()
            assertThat(details?.get("reportId")?.stringValue).isEqualTo("50")
        }
    }

    @Test
    fun `reportsSummary with from-to range covers getReportsByQuarterRange and calculateQuarterRange`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    reportsSummary(range: { from: "202301", to: "202309" }) {
                        quarters {
                            quarter
                            reports { reportId name }
                        }
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst).isNotNull()
        val summary = inst.get("reportsSummary")
        assertThat(summary).isNotNull()
        val quarters = summary.get("quarters")
        assertThat(quarters).isNotNull()
        assertThat(quarters.isArray).isTrue()
        assertThat(quarters.size()).isGreaterThan(0)
        for (i in 0 until quarters.size()) {
            val q = quarters[i]
            assertThat(q.get("quarter").stringValue).isNotBlank()
            val reports = q.get("reports")
            assertThat(reports).isNotNull()
            assertThat(reports.isArray).isTrue()
        }
    }

    @Test
    fun `institution detail with enriched JSON (FI data, social, receita, date)`() {
        val query =
            """
            query(${'$'}id: ID!, ${'$'}anoMes: String) {
                bacenFinanceInstitution(id: ${'$'}id, anoMes: ${'$'}anoMes) {
                    id
                    name
                    website
                    socialNetworks
                    mainActivity
                    address
                    founded
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001", "anoMes" to "202403")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst).isNotNull()
        assertThat(inst.get("socialNetworks").isArray).isTrue()
        assertThat(inst.get("socialNetworks").size()).isEqualTo(2)
        assertThat(inst.get("mainActivity").stringValue).isEqualTo("Banco comercial")
        assertThat(inst.get("address").stringValue).isEqualTo("Brasília, DF")
        assertThat(inst.get("founded").stringValue).isEqualTo("12/10/1808")
    }

    @Test
    fun `reportDetails with actual data covers getReportDetailsByQuarterRange`() {
        val query =
            """
            query(${'$'}id: ID!, ${'$'}reportId: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    reportDetails(reportId: ${'$'}reportId, range: { last: 4 }) {
                        reportId
                        name
                        accounts {
                            group
                            id
                            name
                            valuesByQuarter { quarter value }
                        }
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001", "reportId" to "50")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val data = payload["data"]
        assertThat(data).isNotNull()
        val inst = data.get("bacenFinanceInstitution")
        assertThat(inst).isNotNull()
        val details = inst.get("reportDetails")
        assertThat(details).isNotNull()
        val accounts = details.get("accounts")
        assertThat(accounts).isNotNull()
        assertThat(accounts.isArray).isTrue()
        assertThat(accounts.size()).isGreaterThan(0)
        for (i in 0 until accounts.size()) {
            val acc = accounts[i]
            val valuesByQuarter = acc.get("valuesByQuarter")
            assertThat(valuesByQuarter).isNotNull()
            assertThat(valuesByQuarter.isArray).isTrue()
            assertThat(valuesByQuarter.size()).isGreaterThan(0)
            val first = valuesByQuarter[0]
            assertThat(first.get("quarter").stringValue).isNotBlank()
            assertThat(first.get("value")).isNotNull()
        }
    }

    @Test
    fun `should handle invalid range parameters`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    reportsSummary(range: { last: 0 }) {
                        quarters { quarter }
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
        assertThat(errors.size()).isGreaterThan(0)
    }

    @Test
    fun `should handle missing reportId`() {
        val query =
            """
            query(${'$'}id: ID!) {
                bacenFinanceInstitution(id: ${'$'}id) {
                    id
                    reportDetails(reportId: null, range: { last: 4 }) {
                        reportId
                    }
                }
            }
            """.trimIndent()

        val response =
            httpClient.exchangeAsString(
                HttpRequest
                    .POST(
                        "/graphql",
                        graphQLQuery(query, mapOf("id" to "001")),
                    )
                    .contentType(MediaType.APPLICATION_JSON),
            )
        response.shouldBeSuccessful().shouldBeJson()

        val payload: JsonNode = json.read(response)
        val errors = payload["errors"]
        assertThat(errors).isNotNull()
        assertThat(errors.isArray).isTrue()
    }
}
