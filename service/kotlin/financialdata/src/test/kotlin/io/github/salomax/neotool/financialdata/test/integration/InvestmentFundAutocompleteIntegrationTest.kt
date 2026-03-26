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

@MicronautTest(startApplication = true)
@DisplayName("Investment Fund Autocomplete Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("integration")
@Tag("graphql")
@Tag("financial-data")
class InvestmentFundAutocompleteIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUpTestData() {
        entityManager.runTransaction {
            insertFund(
                cnpj = "11328519000100",
                name = "TREND OURO DÓLAR FUNDO DE INVESTIMENTO FINANCEIRO MULTIMERCADO - RESPONSABILIDADE LIMITADA",
                assetType = "MULTI_MARKET_FUND",
            )
            insertFund(
                cnpj = "22328519000100",
                name = "V8 MERCURY FUNDO DE INVESTIMENTO FINANCEIRO RENDA FIXA CRÉDITO PRIVADO LONGO PRAZO - RESP LIMITADA",
                assetType = "FIXED_INCOME_FUND",
            )
            insertFund(
                cnpj = "33328519000100",
                name = "NORTE LONG BIAS MASTER CLASSE DE INVESTIMENTO MULTIMERCADO - RESP LIMITADA",
                assetType = "MULTI_MARKET_FUND",
            )
            insertFund(
                cnpj = "44328519000100",
                name = "ALPHA FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO EM AÇÕES",
                assetType = "EQUITY_FUND",
            )
            insertFund(
                cnpj = "55328519000100",
                name = "BETA FUNDO DE INVESTIMENTO FINANCEIRO EM COTAS",
                assetType = "MUTUAL_FUND",
            )
            insertFund(
                cnpj = "66328519000100",
                name = "GAMMA FUNDO DE INVESTIMENTO FINANCEIRO EM AÇÕES",
                assetType = "EQUITY_FUND",
            )
        }
    }

    @AfterEach
    fun cleanupTestData() {
        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                DELETE FROM financial_data.investment_asset
                WHERE fund_cnpj IN (
                    '11328519000100',
                    '22328519000100',
                    '33328519000100',
                    '44328519000100',
                    '55328519000100',
                    '66328519000100'
                )
                """.trimIndent(),
            ).executeUpdate()
        }
    }

    @Test
    fun `autocomplete matches abbreviated mutual fund names`() {
        val suggestions = searchSuggestions("Trend Ouro Dólar FIF Multi RL")

        assertThat(suggestions).contains(
            "TREND OURO DÓLAR FUNDO DE INVESTIMENTO FINANCEIRO MULTIMERCADO - RESPONSABILIDADE LIMITADA",
        )
    }

    @Test
    fun `autocomplete matches abbreviation tokens regardless of order`() {
        val suggestions = searchSuggestions("V8 Mercury LP RL CP RF FIF")

        assertThat(suggestions).contains(
            "V8 MERCURY FUNDO DE INVESTIMENTO FINANCEIRO RENDA FIXA CRÉDITO PRIVADO LONGO PRAZO - RESP LIMITADA",
        )
    }

    @Test
    fun `autocomplete matches class and multimarket aliases`() {
        val suggestions = searchSuggestions("Norte Long Bias CIC FIM RL")

        assertThat(suggestions).contains(
            "NORTE LONG BIAS MASTER CLASSE DE INVESTIMENTO MULTIMERCADO - RESP LIMITADA",
        )
    }

    @Test
    fun `autocomplete matches fic and fia aliases`() {
        val suggestions = searchSuggestions("Alpha FIC FIA")

        assertThat(suggestions).contains(
            "ALPHA FUNDO DE INVESTIMENTO EM COTAS DE FUNDOS DE INVESTIMENTO EM AÇÕES",
        )
    }

    @Test
    fun `autocomplete matches fif cotas and fif acoes aliases`() {
        val cotasSuggestions = searchSuggestions("Beta FIF Cotas")
        val acoesSuggestions = searchSuggestions("Gamma FIF Ações")

        assertThat(cotasSuggestions).contains(
            "BETA FUNDO DE INVESTIMENTO FINANCEIRO EM COTAS",
        )
        assertThat(acoesSuggestions).contains(
            "GAMMA FUNDO DE INVESTIMENTO FINANCEIRO EM AÇÕES",
        )
    }

    private fun insertFund(
        cnpj: String,
        name: String,
        assetType: String,
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO financial_data.investment_asset
                (id, name, asset_type, fund_cnpj, is_active, source, currency_code, lot_size, price_factor,
                 fund_search_text, fund_search_tokens, created_at, updated_at)
            VALUES
                (
                    gen_random_uuid(),
                    :name,
                    :assetType,
                    :cnpj,
                    true,
                    'CVM',
                    'BRL',
                    1,
                    1,
                    financial_data.build_fund_search_text(:name),
                    financial_data.build_fund_search_tokens(:name),
                    NOW(),
                    NOW()
                )
            ON CONFLICT (fund_cnpj)
            DO UPDATE SET
                name = EXCLUDED.name,
                asset_type = EXCLUDED.asset_type,
                is_active = EXCLUDED.is_active,
                fund_search_text = EXCLUDED.fund_search_text,
                fund_search_tokens = EXCLUDED.fund_search_tokens,
                updated_at = NOW()
            """.trimIndent(),
        )
            .setParameter("name", name)
            .setParameter("assetType", assetType)
            .setParameter("cnpj", cnpj)
            .executeUpdate()
    }

    private fun searchSuggestions(queryValue: String): List<String> {
        val query =
            """
            query SearchInvestmentFundsAutocomplete(${'$'}query: String!) {
                searchInvestmentFundsAutocomplete(query: ${'$'}query) {
                    suggestions {
                        name
                    }
                }
            }
            """.trimIndent()

        val request =
            HttpRequest.POST(
                "/graphql",
                mapOf(
                    "query" to query,
                    "variables" to mapOf("query" to queryValue),
                ),
            ).contentType(MediaType.APPLICATION_JSON)

        val response = httpClient.exchangeAsString(request)
        response
            .shouldBeSuccessful()
            .shouldBeJson()
            .shouldHaveNonEmptyBody()

        val payload: JsonNode = json.read(response)
        payload["errors"].assertNoErrors()

        val suggestions = payload["data"]["searchInvestmentFundsAutocomplete"]["suggestions"]
        return (0 until suggestions.size()).mapNotNull { suggestions[it]["name"]?.stringValue }
    }
}
