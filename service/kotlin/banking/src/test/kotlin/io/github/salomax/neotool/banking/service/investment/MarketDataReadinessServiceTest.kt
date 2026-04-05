package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncControl
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncStatus
import io.github.salomax.neotool.banking.repo.portfolio.MarketDataSyncControlRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class MarketDataReadinessServiceTest {
    private val syncControlRepository: MarketDataSyncControlRepository = mock()

    private val service = MarketDataReadinessService(syncControlRepository)

    @Test
    fun `uses minimum synced date across valuation datasets when snapshot dataset is fresh`() {
        whenever(syncControlRepository.findAll()).thenReturn(
            listOf(
                control(MarketDataSyncJobName.B3_COTAHIST, syncedDate = LocalDate.of(2026, 4, 2)),
                control(MarketDataSyncJobName.CVM_NAV, syncedDate = LocalDate.of(2026, 4, 1)),
                control(MarketDataSyncJobName.INDICATORS, syncedDate = LocalDate.of(2026, 4, 3)),
                control(
                    MarketDataSyncJobName.B3_CORPORATE_EVENTS,
                    syncedDate = LocalDate.of(2026, 1, 1),
                    sourceRunAt = Instant.parse("2026-04-03T08:00:00Z"),
                    lastSyncCompletedAt = Instant.parse("2026-04-03T08:01:00Z"),
                ),
            ),
        )

        val readiness = service.valuationReadiness()

        assertThat(readiness.isReady).isTrue()
        assertThat(readiness.safeValuationDate).isEqualTo(LocalDate.of(2026, 4, 1))
        assertThat(readiness.missingJobs).isEmpty()
    }

    @Test
    fun `blocks readiness when corporate events snapshot is stale relative to producer run`() {
        whenever(syncControlRepository.findAll()).thenReturn(
            listOf(
                control(MarketDataSyncJobName.B3_COTAHIST, syncedDate = LocalDate.of(2026, 4, 2)),
                control(MarketDataSyncJobName.CVM_NAV, syncedDate = LocalDate.of(2026, 4, 2)),
                control(MarketDataSyncJobName.INDICATORS, syncedDate = LocalDate.of(2026, 4, 2)),
                control(
                    MarketDataSyncJobName.B3_CORPORATE_EVENTS,
                    syncedDate = LocalDate.of(2026, 4, 2),
                    sourceRunAt = Instant.parse("2026-04-03T08:00:00Z"),
                    lastSyncCompletedAt = Instant.parse("2026-04-03T07:59:00Z"),
                ),
            ),
        )

        val readiness = service.valuationReadiness()

        assertThat(readiness.isReady).isFalse()
        assertThat(readiness.safeValuationDate).isEqualTo(LocalDate.of(2026, 4, 2))
        assertThat(readiness.missingJobs).containsExactly(MarketDataSyncJobName.B3_CORPORATE_EVENTS)
    }

    private fun control(
        jobName: MarketDataSyncJobName,
        syncedDate: LocalDate?,
        sourceRunAt: Instant? = null,
        lastSyncCompletedAt: Instant? = sourceRunAt,
    ) = MarketDataSyncControl(
        jobName = jobName,
        sourceLastDataDate = syncedDate,
        sourceLastRunAt = sourceRunAt,
        lastSyncedDate = syncedDate,
        lastSyncCompletedAt = lastSyncCompletedAt,
        status = MarketDataSyncStatus.SUCCESS,
    )
}
