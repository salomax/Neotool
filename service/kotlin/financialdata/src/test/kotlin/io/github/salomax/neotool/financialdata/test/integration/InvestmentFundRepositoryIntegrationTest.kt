package io.github.salomax.neotool.financialdata.test.integration

import io.github.salomax.neotool.common.test.integration.BaseIntegrationTest
import io.github.salomax.neotool.common.test.integration.PostgresIntegrationTest
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.github.salomax.neotool.financialdata.repo.InvestmentFundRepositoryImpl
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("InvestmentFundRepository Integration Tests")
@Tag("integration")
@Tag("financial-data")
class InvestmentFundRepositoryIntegrationTest : BaseIntegrationTest(), PostgresIntegrationTest {
    @Inject
    lateinit var entityManager: EntityManager

    @Inject
    lateinit var repository: InvestmentFundRepositoryImpl

    private val fundId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @AfterEach
    fun cleanupTestData() {
        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                DELETE FROM financial_data.investment_asset
                WHERE id = :id
                """.trimIndent(),
            )
                .setParameter("id", fundId)
                .executeUpdate()
        }
    }

    @Test
    fun `findFundById returns raw RCVM175 fields without coercion`() {
        entityManager.runTransaction {
            entityManager.createNativeQuery(
                """
                INSERT INTO financial_data.investment_asset
                    (id, name, asset_type, fund_cnpj, is_active, manager, administrador,
                     tributacao_longo_prazo, publico_alvo, patrimonio_liquido,
                     data_patrimonio_liquido, cnpj_auditor, auditor, cnpj_custodiante,
                     custodiante, source, currency_code, lot_size, price_factor,
                     fund_search_text, fund_search_tokens, created_at, updated_at)
                VALUES
                    (:id, :name, :assetType, :cnpj, true, :manager, :administrador,
                     :tributacaoLongoPrazo, :publicoAlvo, :patrimonioLiquido,
                     :dataPatrimonioLiquido, :cnpjAuditor, :auditor, :cnpjCustodiante,
                     :custodiante, 'CVM', 'BRL', 1, 1,
                     financial_data.build_fund_search_text(:name),
                     financial_data.build_fund_search_tokens(:name),
                     NOW(), NOW())
                """.trimIndent(),
            )
                .setParameter("id", fundId)
                .setParameter("name", "FUNDO TESTE RCVM175")
                .setParameter("assetType", "FIXED_INCOME_FUND")
                .setParameter("cnpj", "12345678000190")
                .setParameter("manager", "Gestor Correto")
                .setParameter("administrador", "Administrador Correto")
                .setParameter("tributacaoLongoPrazo", "S")
                .setParameter("publicoAlvo", "Investidores em Geral")
                .setParameter("patrimonioLiquido", "12345.67")
                .setParameter("dataPatrimonioLiquido", "2026-07-28")
                .setParameter("cnpjAuditor", "11.222.333/0001-44")
                .setParameter("auditor", "Auditor XPTO")
                .setParameter("cnpjCustodiante", "99.888.777/0001-66")
                .setParameter("custodiante", "Custodiante XPTO")
                .executeUpdate()
        }

        val fund = repository.findFundById(fundId)

        assertThat(fund).isNotNull
        assertThat(fund!!.manager).isEqualTo("Gestor Correto")
        assertThat(fund.administrador).isEqualTo("Administrador Correto")
        assertThat(fund.tributacaoLongoPrazo).isEqualTo("S")
        assertThat(fund.publicoAlvo).isEqualTo("Investidores em Geral")
        assertThat(fund.patrimonioLiquido).isEqualTo("12345.67")
        assertThat(fund.dataPatrimonioLiquido).isEqualTo("2026-07-28")
        assertThat(fund.cnpjAuditor).isEqualTo("11.222.333/0001-44")
        assertThat(fund.auditor).isEqualTo("Auditor XPTO")
        assertThat(fund.cnpjCustodiante).isEqualTo("99.888.777/0001-66")
        assertThat(fund.custodiante).isEqualTo("Custodiante XPTO")
    }
}
