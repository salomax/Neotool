package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetDailyPrice
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioFlowEvent
import io.github.salomax.neotool.banking.domain.portfolio.InvestmentPortfolioQuota
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkCode
import io.github.salomax.neotool.banking.domain.portfolio.MarketBenchmarkDaily
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioDimensionType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioFlowEventType
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationJob
import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.repo.portfolio.AssetDailyPriceRepository
import io.github.salomax.neotool.banking.repo.portfolio.MarketBenchmarkDailyRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioFlowEventRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioQuotaRepository
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Portfolio repositories IT")
class PortfolioRepositoriesIT : BankingPostgresIntegrationTest() {
    @Inject
    lateinit var assetDailyPriceRepository: AssetDailyPriceRepository

    @Inject
    lateinit var portfolioQuotaRepository: PortfolioQuotaRepository

    @Inject
    lateinit var portfolioFlowEventRepository: PortfolioFlowEventRepository

    @Inject
    lateinit var portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository

    @Inject
    lateinit var marketBenchmarkDailyRepository: MarketBenchmarkDailyRepository

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var assetId: UUID

    @BeforeEach
    fun insertFixtures() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        assetId = UUID.randomUUID()

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id,
                    account_id,
                    created_by_user_id,
                    type,
                    subtype,
                    name,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$financialAccountId',
                    '$accountId',
                    '$userId',
                    'INVESTMENT',
                    'BROKERAGE',
                    'Portfolio IT Broker',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )

            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id,
                    name,
                    asset_type,
                    currency_code,
                    created_at,
                    updated_at
                ) VALUES (
                    '$assetId',
                    'Portfolio IT Asset',
                    'ETF',
                    'BRL',
                    NOW(),
                    NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `asset daily price upsert is idempotent and latest before returns most recent row`() {
        assetDailyPriceRepository.upsert(
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = LocalDate.of(2026, 1, 2),
                closePrice = BigDecimal("100.12345678"),
                source = "B3",
            ),
        )
        assetDailyPriceRepository.upsert(
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = LocalDate.of(2026, 1, 2),
                closePrice = BigDecimal("101.00000000"),
                source = "B3",
            ),
        )
        assetDailyPriceRepository.upsert(
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = LocalDate.of(2026, 1, 3),
                closePrice = BigDecimal("102.00000000"),
                source = "B3",
            ),
        )

        val exact = assetDailyPriceRepository.findByAssetAndDate(assetId, LocalDate.of(2026, 1, 2))
        val latest = assetDailyPriceRepository.findLatestBefore(assetId, LocalDate.of(2026, 1, 4))
        val range =
            assetDailyPriceRepository.findByAssetAndDateRange(
                assetId = assetId,
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2026, 1, 31),
            )

        assertThat(exact).isNotNull()
        assertThat(exact!!.closePrice).isEqualByComparingTo("101.00000000")
        assertThat(latest).isNotNull()
        assertThat(latest!!.priceDate).isEqualTo(LocalDate.of(2026, 1, 3))
        assertThat(range).hasSize(2)
    }

    @Test
    fun `portfolio quota upsert normalizes portfolio dimension key and delete from date removes future rows`() {
        portfolioQuotaRepository.upsert(
            InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                quotaDate = LocalDate.of(2026, 1, 2),
                quotaValue = BigDecimal("1.00000000"),
                totalQuotas = BigDecimal("100.00000000"),
                totalPortfolioValue = BigDecimal("100.00"),
            ),
        )
        portfolioQuotaRepository.upsert(
            InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                quotaDate = LocalDate.of(2026, 1, 2),
                quotaValue = BigDecimal("1.02000000"),
                totalQuotas = BigDecimal("100.00000000"),
                totalPortfolioValue = BigDecimal("102.00"),
            ),
        )
        portfolioQuotaRepository.upsert(
            InvestmentPortfolioQuota(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "EQUITY",
                quotaDate = LocalDate.of(2026, 1, 3),
                quotaValue = BigDecimal("1.05000000"),
                totalQuotas = BigDecimal("50.00000000"),
                totalPortfolioValue = BigDecimal("52.50"),
            ),
        )

        val latestPortfolio =
            portfolioQuotaRepository.findLatestBefore(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.PORTFOLIO,
                dimensionKey = null,
                date = LocalDate.of(2026, 1, 31),
            )
        val groupSeries =
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "EQUITY",
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2026, 1, 31),
            )
        val deleted =
            portfolioQuotaRepository.deleteFromDate(
                financialAccountId = financialAccountId,
                from = LocalDate.of(2026, 1, 3),
            )
        val afterDelete =
            portfolioQuotaRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                dimensionType = PortfolioDimensionType.ASSET_GROUP,
                dimensionKey = "EQUITY",
                from = LocalDate.of(2026, 1, 1),
                to = LocalDate.of(2026, 1, 31),
            )

        assertThat(latestPortfolio).isNotNull()
        assertThat(latestPortfolio!!.quotaValue).isEqualByComparingTo("1.02000000")
        assertThat(groupSeries).hasSize(1)
        assertThat(deleted).isEqualTo(1)
        assertThat(afterDelete).isEmpty()
    }

    @Test
    fun `portfolio flow events can be saved and queried by exact day or range`() {
        val event =
            portfolioFlowEventRepository.save(
                InvestmentPortfolioFlowEvent(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    eventDate = LocalDate.of(2026, 2, 10),
                    eventType = PortfolioFlowEventType.DEPOSIT,
                    grossAmount = BigDecimal("500.00"),
                    portfolioValueBefore = BigDecimal("1000.00"),
                    quotaValueAtEvent = BigDecimal("1.10000000"),
                    quotasTransacted = BigDecimal("454.54545454"),
                    relatedHoldingId = null,
                    sourceReferenceId = UUID.randomUUID(),
                    sourceReferenceType = "MANUAL",
                ),
            )

        val sameDay =
            portfolioFlowEventRepository.findByAccountAndDate(
                financialAccountId = financialAccountId,
                date = LocalDate.of(2026, 2, 10),
            )
        val range =
            portfolioFlowEventRepository.findByAccountAndDateRange(
                financialAccountId = financialAccountId,
                from = LocalDate.of(2026, 2, 1),
                to = LocalDate.of(2026, 2, 28),
            )

        assertThat(event.id).isNotNull()
        assertThat(sameDay).hasSize(1)
        assertThat(range).hasSize(1)
        assertThat(range.single().grossAmount).isEqualByComparingTo("500.00")
    }

    @Test
    fun `recalculation jobs expose pending and latest status`() {
        val created =
            portfolioRecalculationJobRepository.save(
                PortfolioRecalculationJob(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    status = PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = LocalDate.of(2026, 1, 1),
                    triggeredBy = "holding_created:${UUID.randomUUID()}",
                ),
            )

        val pending = portfolioRecalculationJobRepository.findPendingByAccount(accountId)
        val updated =
            portfolioRecalculationJobRepository.update(
                created.copy(
                    status = PortfolioRecalculationStatus.COMPLETED,
                    daysProcessed = 10,
                    startedAt = Instant.parse("2026-01-10T10:00:00Z"),
                    completedAt = Instant.parse("2026-01-10T10:05:00Z"),
                    updatedAt = Instant.parse("2026-01-10T10:05:00Z"),
                ),
            )
        val latest = portfolioRecalculationJobRepository.findLatestByAccount(accountId)
        val pendingAfterCompletion = portfolioRecalculationJobRepository.findPendingByAccount(accountId)

        assertThat(pending).isNotNull()
        assertThat(pending!!.status).isEqualTo(PortfolioRecalculationStatus.PENDING)
        assertThat(updated.status).isEqualTo(PortfolioRecalculationStatus.COMPLETED)
        assertThat(latest).isNotNull()
        assertThat(latest!!.status).isEqualTo(PortfolioRecalculationStatus.COMPLETED)
        assertThat(pendingAfterCompletion).isNull()
    }

    @Test
    fun `tryClaimJob does not allow concurrent processing jobs for the same account`() {
        val firstPending =
            portfolioRecalculationJobRepository.save(
                PortfolioRecalculationJob(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    status = PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = LocalDate.of(2026, 1, 1),
                    triggeredBy = "holding_created:${UUID.randomUUID()}",
                ),
            )
        val secondPending =
            portfolioRecalculationJobRepository.save(
                PortfolioRecalculationJob(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    status = PortfolioRecalculationStatus.PENDING,
                    recalculateFrom = LocalDate.of(2026, 1, 2),
                    triggeredBy = "holding_created:${UUID.randomUUID()}",
                ),
            )

        val firstClaim = portfolioRecalculationJobRepository.tryClaimJob(requireNotNull(firstPending.id), "node-a")
        val secondClaimWhileFirstProcessing =
            portfolioRecalculationJobRepository.tryClaimJob(requireNotNull(secondPending.id), "node-b")
        requireNotNull(firstClaim)

        assertThat(secondClaimWhileFirstProcessing).isNull()
    }

    @Test
    fun `benchmark daily upsert returns ordered series`() {
        marketBenchmarkDailyRepository.upsert(
            MarketBenchmarkDaily(
                benchmarkCode = MarketBenchmarkCode.CDI,
                priceDate = LocalDate.of(2026, 3, 1),
                dailyFactor = BigDecimal("1.0005000000"),
                cumulativeFrom = BigDecimal("1.00050000"),
                source = "BACEN",
            ),
        )
        marketBenchmarkDailyRepository.upsert(
            MarketBenchmarkDaily(
                benchmarkCode = MarketBenchmarkCode.CDI,
                priceDate = LocalDate.of(2026, 3, 2),
                dailyFactor = BigDecimal("1.0004000000"),
                cumulativeFrom = BigDecimal("1.00090020"),
                source = "BACEN",
            ),
        )
        marketBenchmarkDailyRepository.upsert(
            MarketBenchmarkDaily(
                benchmarkCode = MarketBenchmarkCode.CDI,
                priceDate = LocalDate.of(2026, 3, 2),
                dailyFactor = BigDecimal("1.0004500000"),
                cumulativeFrom = BigDecimal("1.00095022"),
                source = "BACEN",
            ),
        )

        val series =
            marketBenchmarkDailyRepository.findByCodeAndDateRange(
                code = MarketBenchmarkCode.CDI,
                from = LocalDate.of(2026, 3, 1),
                to = LocalDate.of(2026, 3, 31),
            )

        assertThat(series).hasSize(2)
        assertThat(series.map { it.priceDate }).containsExactly(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 3, 2),
        )
        assertThat(series.last().dailyFactor).isEqualByComparingTo("1.0004500000")
    }

    private fun openConnection() =
        getPgContainer().let { container ->
            DriverManager.getConnection(
                buildJdbcUrlWithSslDisabled(container.jdbcUrl),
                container.username,
                container.password,
            )
        }
}
