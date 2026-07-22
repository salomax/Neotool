package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.client.investment.financialdata.FinancialDataFundMarketDataProvider
import io.github.salomax.neotool.banking.client.investment.financialdata.FundMarketDataProvider
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.domain.investment.FundMarketNav
import io.github.salomax.neotool.banking.domain.investment.FundMarketSnapshot
import io.github.salomax.neotool.banking.domain.investment.FundTaxProfile
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.service.investment.ComeCotasCheckpointService
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.LocalDate
import java.util.UUID

/**
 * End-to-end reconciliation for the come-cotas surviving-quota fix (GH-408).
 *
 * Unlike [ComeCotasReconciliationIT] — which validates the reconstruction bookkeeping with a
 * pre-computed `quotasDeducted` — this test drives the full checkpoint pipeline twice
 * (`ComeCotasCheckpointService.process` → `compute` → `ConfirmWithholdingTaxService`), letting the
 * second checkpoint's `quotasDeducted` be derived from the NAV on the base that survived the first
 * checkpoint. It proves the two halves stitched together: NAV-based computation taxing only the
 * surviving quotas.
 */
@MicronautTest(startApplication = true)
@DisplayName("Come-cotas checkpoint compute reconciliation integration")
class ComeCotasCheckpointReconciliationIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var investmentTransactionService: InvestmentTransactionService

    @Inject
    lateinit var checkpointService: ComeCotasCheckpointService

    @Inject
    lateinit var jobRepository: ComeCotasCheckpointJobRepository

    @Inject
    lateinit var investmentTransactionRepository: InvestmentTransactionRepository

    @Inject
    lateinit var holdingRepository: HoldingRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var assetId: UUID
    private lateinit var fundCnpj: String

    override fun getProperties(): MutableMap<String, String> {
        val props = super.getProperties()
        // Drive the checkpoints manually — keep the background jobs from claiming them.
        props["banking.come-cotas.sweep.enabled"] = "false"
        props["banking.come-cotas.pending-dispatcher.enabled"] = "false"
        props["banking.come-cotas.zombie-recovery.enabled"] = "false"
        props["kafka.consumers.come-cotas-checkpoint.enabled"] = "false"
        props["banking.come-cotas.inline-max-checkpoints"] = "0"
        return props
    }

    @BeforeEach
    fun setUpFixture() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        assetId = UUID.randomUUID()
        fundCnpj = "12${UUID.randomUUID().toString().filter(Char::isDigit).padEnd(12, '0').take(12)}"

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                ) VALUES (
                    '$financialAccountId', '$accountId', '$userId', 'INVESTMENT', 'BROKERAGE', 'Broker', 'BRL', NOW(), NOW()
                )
                """.trimIndent(),
            )

            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id, name, asset_type, currency_code, fund_cnpj, created_at, updated_at
                ) VALUES (
                    '$assetId', 'FUNDO TESTE RF', '${AssetType.MUTUAL_FUND.name}', 'BRL', '$fundCnpj', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `two sequential checkpoints materialize come-cotas on the surviving quota base`() {
        val purchaseDate = LocalDate.of(2025, 6, 2)
        val novCheckpoint = LocalDate.of(2025, 11, 28)
        val mayCheckpoint = LocalDate.of(2026, 5, 29)

        investmentTransactionService.record(
            command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = assetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("1000.00000000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("1000.00"),
                    netValue = BigDecimal("1000.00"),
                    tradeDate = purchaseDate,
                    settlementDate = purchaseDate,
                    source = InvestmentSource.MANUAL,
                ),
            accountId = accountId,
            userId = userId,
        )

        val holdingId =
            requireNotNull(
                holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId),
            ).id.let(::requireNotNull)

        // Checkpoint 1 (Nov, NAV 1.10): IR 15.00 → 13.63636364 quotas removed.
        processCheckpoint(holdingId, novCheckpoint)
        val afterNov = requireNotNull(holdingRepository.findById(holdingId, accountId))
        assertThat(afterNov.quantity).isEqualByComparingTo("986.36363636")

        // Checkpoint 2 (May, NAV 1.20): must tax only the 986.36363636 quotas that survived Nov.
        processCheckpoint(holdingId, mayCheckpoint)
        val afterMay = requireNotNull(holdingRepository.findById(holdingId, accountId))

        val withholdings =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .filter { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
        assertThat(withholdings).hasSize(2)
        assertThat(withholdings.all { it.source == InvestmentSource.SYSTEM }).isTrue()

        // Surviving-base method yields 12.15833333 quotas. On the original (unreduced) base the
        // bug would deduct 12.50000000, leaving 973.86363636 — this assertion pins the fix.
        val mayQuotas = requireNotNull(withholdings.single { it.tradeDate == mayCheckpoint }.quantity)
        assertThat(mayQuotas).isEqualByComparingTo("12.15833333")
        assertThat(afterMay.quantity).isEqualByComparingTo("974.20530303")

        // Valuation consistency: the reconstructed open lots stay aligned with holding.quantity.
        val reconstructed =
            ComeCotasSupport.reconstructOpenLots(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity) }
        assertThat(reconstructed).isEqualByComparingTo(afterMay.quantity)
    }

    private fun processCheckpoint(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ) {
        val job =
            jobRepository.findByHoldingAndCheckpointDate(holdingId, checkpointDate)
                ?: jobRepository.save(
                    ComeCotasCheckpointJob(
                        holdingId = holdingId,
                        financialAccountId = financialAccountId,
                        accountId = accountId,
                        checkpointDate = checkpointDate,
                        status = ComeCotasCheckpointJobStatus.PENDING,
                    ),
                )
        checkpointService.process(requireNotNull(job.id))
    }

    private fun openConnection() =
        getPgContainer().let { container ->
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            )
        }

    /**
     * Deterministic market data so `compute` reads a known NAV per checkpoint:
     * 1.10 up to end-2025 (Nov checkpoint), 1.20 afterwards (May checkpoint).
     */
    @Singleton
    @Replaces(FinancialDataFundMarketDataProvider::class)
    class StubFundMarketDataProvider : FundMarketDataProvider {
        override fun getSnapshotsByCnpj(cnpjs: Set<String>): Map<String, FundMarketSnapshot> =
            cnpjs.associateWith { cnpj ->
                FundMarketSnapshot(
                    fundCnpj = cnpj,
                    assetType = AssetType.MUTUAL_FUND.name,
                    taxProfile = FundTaxProfile.COME_COTAS_LONG,
                    latestNav = FundMarketNav(date = LocalDate.of(2026, 5, 29), closePrice = BigDecimal("1.20")),
                )
            }

        override fun getNavSeries(
            cnpj: String,
            from: LocalDate,
            to: LocalDate,
        ): List<Pair<LocalDate, BigDecimal>> {
            val nav = if (!to.isAfter(LocalDate.of(2025, 12, 31))) BigDecimal("1.10") else BigDecimal("1.20")
            return listOf(to to nav)
        }
    }
}
