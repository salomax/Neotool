package io.github.salomax.neotool.banking.domain.portfolio

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class MarketDataSyncJobName(val jobName: String) {
    B3_COTAHIST("b3_cotahist"),
    CVM_NAV("cvm_nav"),
    INDICATORS("indicators"),
    B3_CORPORATE_EVENTS("b3_corporate_events"),
    ;

    companion object {
        fun from(jobName: String): MarketDataSyncJobName =
            entries.firstOrNull { it.jobName == jobName }
                ?: throw IllegalArgumentException("Unsupported market data sync job: $jobName")
    }
}

enum class MarketDataSyncStatus {
    IDLE,
    SYNCING,
    SUCCESS,
    FAILED,
}

data class MarketDataSyncControl(
    val jobName: MarketDataSyncJobName,
    val sourceLastDataDate: LocalDate? = null,
    val sourceLastRunAt: Instant? = null,
    val lastSyncedDate: LocalDate? = null,
    val lastCursor: String? = null,
    val lastSyncStartedAt: Instant? = null,
    val lastSyncCompletedAt: Instant? = null,
    val status: MarketDataSyncStatus = MarketDataSyncStatus.IDLE,
    val lastEventId: UUID? = null,
    val lastError: String? = null,
    val updatedAt: Instant = Instant.now(),
)
