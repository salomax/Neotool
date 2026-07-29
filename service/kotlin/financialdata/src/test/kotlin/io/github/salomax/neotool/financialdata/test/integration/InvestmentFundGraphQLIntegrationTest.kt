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
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Investment Fund GraphQL Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
class InvestmentFundGraphQLIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var entityManager: EntityManager

    private val longProfileFundId: UUID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val shortProfileFundId: UUID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    @BeforeEach
    fun setUpTestData() {
        entityManager.runTransaction {
            insertFund(
                id = longProfileFundId,
                cnpj = "10101010000101",
                name = "ALFA FUNDO DE INVESTIMENTO RENDA FIXA CURTO PRAZO",
                rawTaxMaturity = "S",
                patrimonioLiquido = "12345.67",
                dataPatrimonioLiquido = "2026-07-28",
                cnpjAuditor = "11.222.333/0001-44",
                auditor = "Auditor Alfa",
                cnpjCustodiante = "99.888.777/0001-66",
                custodiante = "Custodiante Alfa",
                publicoAlvo = "Investidores em Geral",
            )
            insertFund(
                id = shortProfileFundId,
                cnpj = "20202020000102",
                name = "OMEGA FUNDO DE INVESTIMENTO RENDA FIXA LONGO PRAZO",
                rawTaxMaturity = "N",
                patrimonioLiquido = "76543.21",
                dataPatrimonioLiquido = "2026-07-27",
                cnpjAuditor = "22.333.444/0001-55",
                auditor = "Auditor Omega",
                cnpjCustodiante = "88.777.666/0001-55",
                custodiante = "Custodiante Omega",
                publicoAlvo = "Investidores Qualificados",
            )

            insertNav(longProfileFundId, "2026-07-28", "1.23", "12345.67")
            insertNav(shortProfileFundId, "2026-07-28", "2.34", "76543.21")
        }
    }

    @AfterEach
    fun cleanupTestData() {
        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                DELETE FROM financial_data.asset_daily_price
                WHERE asset_id IN (:longProfileFundId, :shortProfileFundId)
                """.trimIndent(),
            )
                .setParameter("longProfileFundId", longProfileFundId)
                .setParameter("shortProfileFundId", shortProfileFundId)
                .executeUpdate()

            entityManager.createNativeQuery(
                """
                DELETE FROM financial_data.investment_asset
                WHERE id IN (:longProfileFundId, :shortProfileFundId)
                """.trimIndent(),
            )
                .setParameter("longProfileFundId", longProfileFundId)
                .setParameter("shortProfileFundId", shortProfileFundId)
                .executeUpdate()
        }
    }

    @Test
    fun `investmentFund exposes raw fields and raw-driven long tax profile`() {
        val query =
            """
            query(${'$'}id: ID!) {
                investmentFund(id: ${'$'}id) {
                    id
                    patrimonioLiquido
                    dataPatrimonioLiquido
                    custodiante
                    cnpjCustodiante
                    auditor
                    cnpjAuditor
                    publicoAlvo
                    taxProfile
                    latestNav {
                        closePrice
                        totalNetAssets
                    }
                }
            }
            """.trimIndent()

        val payload = executeGraphQL(query, mapOf("id" to longProfileFundId.toString()))
        val fund = payload["data"]["investmentFund"]

        assertThat(fund["id"].stringValue).isEqualTo(longProfileFundId.toString())
        assertThat(fund["patrimonioLiquido"].stringValue).isEqualTo("12345.67")
        assertThat(fund["dataPatrimonioLiquido"].stringValue).isEqualTo("2026-07-28")
        assertThat(fund["custodiante"].stringValue).isEqualTo("Custodiante Alfa")
        assertThat(fund["cnpjCustodiante"].stringValue).isEqualTo("99.888.777/0001-66")
        assertThat(fund["auditor"].stringValue).isEqualTo("Auditor Alfa")
        assertThat(fund["cnpjAuditor"].stringValue).isEqualTo("11.222.333/0001-44")
        assertThat(fund["publicoAlvo"].stringValue).isEqualTo("Investidores em Geral")
        assertThat(fund["taxProfile"].stringValue).isEqualTo("COME_COTAS_LONG")
        assertThat(fund["latestNav"]["closePrice"].doubleValue).isEqualTo(1.23)
        assertThat(fund["latestNav"]["totalNetAssets"].doubleValue).isEqualTo(12345.67)
    }

    @Test
    fun `investmentFundMarketSnapshots prefers raw short-term signal over long name marker`() {
        val query =
            """
            query(${'$'}cnpjs: [String!]!) {
                investmentFundMarketSnapshots(cnpjs: ${'$'}cnpjs) {
                    cnpj
                    taxProfile
                }
            }
            """.trimIndent()

        val payload = executeGraphQL(query, mapOf("cnpjs" to listOf("20202020000102")))
        val snapshots = payload["data"]["investmentFundMarketSnapshots"]

        assertThat(snapshots.isArray).isTrue()
        assertThat(snapshots.size()).isEqualTo(1)
        assertThat(snapshots[0]["cnpj"].stringValue).isEqualTo("20202020000102")
        assertThat(snapshots[0]["taxProfile"].stringValue).isEqualTo("COME_COTAS_SHORT")
    }

    private fun insertFund(
        id: UUID,
        cnpj: String,
        name: String,
        rawTaxMaturity: String,
        patrimonioLiquido: String,
        dataPatrimonioLiquido: String,
        cnpjAuditor: String,
        auditor: String,
        cnpjCustodiante: String,
        custodiante: String,
        publicoAlvo: String,
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO financial_data.investment_asset
                (id, name, asset_type, fund_cnpj, is_active, tributacao_longo_prazo,
                 publico_alvo, patrimonio_liquido, data_patrimonio_liquido, cnpj_auditor,
                 auditor, cnpj_custodiante, custodiante, source, currency_code, lot_size,
                 price_factor, fund_search_text, fund_search_tokens, created_at, updated_at)
            VALUES
                (:id, :name, 'FIXED_INCOME_FUND', :cnpj, true, :rawTaxMaturity,
                 :publicoAlvo, :patrimonioLiquido, :dataPatrimonioLiquido, :cnpjAuditor,
                 :auditor, :cnpjCustodiante, :custodiante, 'CVM', 'BRL', 1, 1,
                 financial_data.build_fund_search_text(:name),
                 financial_data.build_fund_search_tokens(:name),
                 NOW(), NOW())
            """.trimIndent(),
        )
            .setParameter("id", id)
            .setParameter("name", name)
            .setParameter("cnpj", cnpj)
            .setParameter("rawTaxMaturity", rawTaxMaturity)
            .setParameter("publicoAlvo", publicoAlvo)
            .setParameter("patrimonioLiquido", patrimonioLiquido)
            .setParameter("dataPatrimonioLiquido", dataPatrimonioLiquido)
            .setParameter("cnpjAuditor", cnpjAuditor)
            .setParameter("auditor", auditor)
            .setParameter("cnpjCustodiante", cnpjCustodiante)
            .setParameter("custodiante", custodiante)
            .executeUpdate()
    }

    private fun insertNav(
        assetId: UUID,
        priceDate: String,
        closePrice: String,
        totalNetAssets: String,
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO financial_data.asset_daily_price
                (asset_id, price_date, close_price, volume, source, created_at)
            VALUES
                (:assetId, :priceDate, :closePrice, :totalNetAssets, 'CVM', NOW())
            """.trimIndent(),
        )
            .setParameter("assetId", assetId)
            .setParameter("priceDate", LocalDate.parse(priceDate))
            .setParameter("closePrice", BigDecimal(closePrice))
            .setParameter("totalNetAssets", BigDecimal(totalNetAssets))
            .executeUpdate()
    }

    private fun executeGraphQL(
        query: String,
        variables: Map<String, Any>,
    ): JsonNode {
        val request =
            HttpRequest.POST(
                "/graphql",
                mapOf(
                    "query" to query,
                    "variables" to variables,
                ),
            ).contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()
        return payload
    }
}
