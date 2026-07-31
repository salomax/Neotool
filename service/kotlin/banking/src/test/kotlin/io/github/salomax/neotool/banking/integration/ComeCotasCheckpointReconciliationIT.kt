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
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.model.dto.investment.RecordInvestmentTransactionCommand
import io.github.salomax.neotool.banking.model.dto.investment.UpdateInvestmentTransactionCommand
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.banking.repo.investment.TaxRecalculationJobRepository
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.ComeCotasCheckpointService
import io.github.salomax.neotool.banking.service.investment.ComeCotasSupport
import io.github.salomax.neotool.banking.service.investment.InvestmentTransactionService
import io.github.salomax.neotool.banking.service.investment.PortfolioRecalculationService
import io.github.salomax.neotool.banking.service.investment.TaxRecalculationService
import io.micronaut.context.annotation.Replaces
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
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
    lateinit var checkpointRepository: ComeCotasCheckpointRepository

    @Inject
    lateinit var investmentTransactionRepository: InvestmentTransactionRepository

    @Inject
    lateinit var holdingRepository: HoldingRepository

    @Inject
    lateinit var taxRecalculationService: TaxRecalculationService

    @Inject
    lateinit var taxRecalculationJobRepository: TaxRecalculationJobRepository

    @Inject
    lateinit var portfolioRecalculationService: PortfolioRecalculationService

    @Inject
    lateinit var portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository

    @Inject
    lateinit var portfolioQuotaRepository: PortfolioQuotaRepository

    @Inject
    lateinit var assetDailyPriceRepository: AssetDailyPriceRepository

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
        val afterNovReconstructed =
            ComeCotasSupport.reconstructOpenLots(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity) }
        assertThat(afterNovReconstructed).isEqualByComparingTo("986.36363636")

        // Checkpoint 2 (May, NAV 1.20): computes from the surviving base at that checkpoint's NAV,
        // but nets against the amount already withheld at the prior checkpoint.
        processCheckpoint(holdingId, mayCheckpoint)

        val withholdings =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .filter { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
        assertThat(withholdings).hasSize(2)
        assertThat(withholdings.all { it.source == InvestmentSource.SYSTEM }).isTrue()

        // The May checkpoint runs on the surviving 986.36363636 quotas and only withholds the
        // incremental amount after netting the Nov checkpoint row.
        val mayQuotas = requireNotNull(withholdings.single { it.tradeDate == mayCheckpoint }.quantity)
        assertThat(mayQuotas).isEqualByComparingTo("12.15833333")
        val reconstructed =
            ComeCotasSupport.reconstructOpenLots(
                investmentTransactionRepository.findByHolding(holdingId, accountId),
            ).fold(BigDecimal.ZERO) { acc, lot -> acc.add(lot.quantity) }
        assertThat(reconstructed).isEqualByComparingTo("974.20530303")

        val persistedCheckpoints =
            checkpointRepository.findByHoldingAndDateRange(
                holdingId = holdingId,
                from = novCheckpoint,
                to = mayCheckpoint,
            )
        assertThat(persistedCheckpoints).hasSize(2)
        val mayCheckpointRow = persistedCheckpoints.single { it.checkpointDate == mayCheckpoint }
        assertThat(mayCheckpointRow.computedAmount).isEqualByComparingTo("29.59")
        assertThat(mayCheckpointRow.withheldAmount).isEqualByComparingTo("14.59")
    }

    @Test
    @DisplayName(
        "processing a later checkpoint nets prior withholding without charging the skipped one",
    )
    fun `processing a later checkpoint after skipping an intermediate one`() {
        val purchaseDate = LocalDate.of(2025, 6, 2)
        val firstProcessedCheckpoint = LocalDate.of(2025, 11, 28)
        val skippedCheckpoint = LocalDate.of(2026, 5, 29)
        val followingCheckpoint = LocalDate.of(2026, 11, 30)

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

        processCheckpoint(holdingId, firstProcessedCheckpoint)
        processCheckpoint(holdingId, followingCheckpoint)

        val checkpoints =
            checkpointRepository.findByHoldingAndDateRange(
                holdingId = holdingId,
                from = firstProcessedCheckpoint,
                to = followingCheckpoint,
            )
        assertThat(checkpoints).hasSize(2)
        val followingCheckpointRow = checkpoints.single { it.checkpointDate == followingCheckpoint }
        assertThat(followingCheckpointRow.computedAmount).isEqualByComparingTo("44.39")
        assertThat(followingCheckpointRow.withheldAmount).isEqualByComparingTo("29.39")

        val withholdings =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .filter { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
        assertThat(withholdings).hasSize(2)
        assertThat(withholdings.none { it.tradeDate == skippedCheckpoint }).isTrue()
        val withholding = withholdings.single { it.tradeDate == followingCheckpoint }
        assertThat(withholding.tradeDate).isEqualTo(followingCheckpoint)
        assertThat(withholding.source).isEqualTo(InvestmentSource.SYSTEM)
        assertThat(withholding.quantity).isEqualByComparingTo("22.60769231")
        assertThat(withholding.grossValue).isEqualByComparingTo("29.39")
    }

    @Test
    fun `historical purchase edit converges checkpoint transaction and quota history regardless of order`() {
        val editThenReprocess =
            runHistoricalEditScenario(
                preEditAction = { _, _ -> },
                editedQuantity = BigDecimal("1200.00000000"),
            )
        val reprocessThenEdit =
            runHistoricalEditScenario(
                preEditAction = { holdingId, checkpointDate ->
                    taxRecalculationService.requestRecalculation(
                        holdingId = holdingId,
                        accountId = accountId,
                        recalculateFrom = checkpointDate,
                        triggeredBy = "integration:no-op-prepass",
                    )
                    processPendingTaxRecalculationJobs(holdingId)
                    processPendingPortfolioJobs()
                },
                editedQuantity = BigDecimal("1200.00000000"),
            )

        assertThat(editThenReprocess).isEqualTo(reprocessThenEdit)
    }

    @Test
    fun `batch checkpoint lookup returns rows only for holdings with checkpoints`() {
        val purchaseDate = LocalDate.of(2025, 6, 2)
        val checkpointDate = LocalDate.of(2025, 11, 28)
        val secondAssetId = UUID.randomUUID()
        val secondFundCnpj = "98${UUID.randomUUID().toString().filter(Char::isDigit).padEnd(12, '0').take(12)}"

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id, name, asset_type, currency_code, fund_cnpj, created_at, updated_at
                ) VALUES (
                    '$secondAssetId', 'FUNDO TESTE RF 2', '${AssetType.MUTUAL_FUND.name}', 'BRL', '$secondFundCnpj', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }

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
        investmentTransactionService.record(
            command =
                RecordInvestmentTransactionCommand(
                    financialAccountId = financialAccountId,
                    assetId = secondAssetId,
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = BigDecimal("500.00000000"),
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = BigDecimal("500.00"),
                    netValue = BigDecimal("500.00"),
                    tradeDate = purchaseDate,
                    settlementDate = purchaseDate,
                    source = InvestmentSource.MANUAL,
                ),
            accountId = accountId,
            userId = userId,
        )

        val firstHoldingId =
            requireNotNull(
                holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId),
            ).id.let(::requireNotNull)
        val secondHoldingId =
            requireNotNull(
                holdingRepository.findByFinancialAccountAndAsset(financialAccountId, secondAssetId),
            ).id.let(::requireNotNull)

        processCheckpoint(firstHoldingId, checkpointDate)

        val checkpoints = checkpointRepository.findByHoldingIds(listOf(firstHoldingId, secondHoldingId))

        assertThat(checkpoints).extracting("holdingId").containsExactly(firstHoldingId)
        assertThat(checkpoints).extracting("checkpointDate").containsExactly(checkpointDate)
        assertThat(checkpoints.none { it.holdingId == secondHoldingId }).isTrue()
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

    private fun processPendingTaxRecalculationJobs(holdingId: UUID) {
        while (true) {
            val pending = taxRecalculationJobRepository.findPendingByHolding(holdingId) ?: break
            taxRecalculationService.process(requireNotNull(pending.id))
        }
    }

    private fun processPendingPortfolioJobs() {
        while (true) {
            val pending = portfolioRecalculationJobRepository.findPendingByAccount(accountId) ?: break
            portfolioRecalculationService.process(requireNotNull(pending.id))
        }
    }

    private fun upsertPrice(
        date: LocalDate,
        closePrice: String,
    ) {
        assetDailyPriceRepository.upsert(
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = date,
                closePrice = BigDecimal(closePrice),
                source = "TEST",
            ),
        )
    }

    private fun runHistoricalEditScenario(
        preEditAction: (UUID, LocalDate) -> Unit,
        editedQuantity: BigDecimal,
    ): RecalculationCascadeSnapshot {
        setUpFixture()
        val purchaseDate = LocalDate.of(2025, 6, 2)
        val checkpointDate = LocalDate.of(2025, 11, 28)
        val today = LocalDate.of(2026, 7, 30)

        upsertPrice(purchaseDate, "1.00")
        upsertPrice(checkpointDate, "1.10")
        upsertPrice(today, "1.20")

        val purchase =
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

        processPendingPortfolioJobs()
        processCheckpoint(holdingId, checkpointDate)
        processPendingPortfolioJobs()

        preEditAction(holdingId, checkpointDate)

        investmentTransactionService.update(
            id = requireNotNull(purchase.id),
            command =
                UpdateInvestmentTransactionCommand(
                    direction = InvestmentTransactionDirection.INFLOW,
                    type = InvestmentTransactionType.PURCHASE,
                    quantity = editedQuantity,
                    unitPrice = BigDecimal("1.00000000"),
                    grossValue = editedQuantity.setScale(2, RoundingMode.HALF_UP),
                    netValue = editedQuantity.setScale(2, RoundingMode.HALF_UP),
                    tradeDate = purchaseDate,
                    settlementDate = purchaseDate,
                ),
            accountId = accountId,
            userId = userId,
        )

        processPendingTaxRecalculationJobs(holdingId)
        processPendingPortfolioJobs()

        val checkpoint =
            checkpointRepository.findByHoldingAndDateRange(holdingId, checkpointDate, checkpointDate).single()
        val withholding =
            investmentTransactionRepository.findByHolding(holdingId, accountId)
                .single { it.type == InvestmentTransactionType.WITHHOLDING_TAX }
        val quotaSnapshot =
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                from = purchaseDate.minusDays(1),
                to = today,
            ).sortedBy { it.quotaDate }.map { row ->
                "${row.quotaDate}|${row.quotaValue}|${row.totalQuotas}|${row.totalPortfolioValue}"
            }

        return RecalculationCascadeSnapshot(
            checkpointComputedAmount = checkpoint.computedAmount,
            checkpointWithheldAmount = checkpoint.withheldAmount,
            withholdingGrossValue = withholding.grossValue,
            withholdingQuantity = requireNotNull(withholding.quantity),
            quotaSnapshot = quotaSnapshot,
        )
    }

    private data class RecalculationCascadeSnapshot(
        val checkpointComputedAmount: BigDecimal,
        val checkpointWithheldAmount: BigDecimal,
        val withholdingGrossValue: BigDecimal,
        val withholdingQuantity: BigDecimal,
        val quotaSnapshot: List<String>,
    )

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
            val nav =
                when {
                    !to.isAfter(LocalDate.of(2025, 12, 31)) -> BigDecimal("1.10")
                    !to.isAfter(LocalDate.of(2026, 5, 29)) -> BigDecimal("1.20")
                    else -> BigDecimal("1.30")
                }
            return listOf(to to nav)
        }
    }
}
