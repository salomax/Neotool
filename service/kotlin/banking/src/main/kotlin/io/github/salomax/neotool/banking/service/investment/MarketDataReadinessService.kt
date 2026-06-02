package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.portfolio.MarketDataSyncJobName
import io.github.salomax.neotool.banking.repo.portfolio.MarketDataSyncControlRepository
import jakarta.inject.Singleton
import java.time.LocalDate
import java.time.ZoneOffset

data class MarketDataValuationReadiness(
    val safeValuationDate: LocalDate?,
    val missingJobs: List<MarketDataSyncJobName>,
) {
    val isReady: Boolean
        get() = safeValuationDate != null && missingJobs.isEmpty()
}

@Singleton
class MarketDataReadinessService(
    private val syncControlRepository: MarketDataSyncControlRepository,
) {
    fun valuationReadiness(): MarketDataValuationReadiness {
        val controls = syncControlRepository.findAll().associateBy { it.jobName }
        val dateDrivenJobs =
            listOf(
                MarketDataSyncJobName.B3_COTAHIST,
                MarketDataSyncJobName.CVM_NAV,
                MarketDataSyncJobName.INDICATORS,
            )
        val snapshotJobs = listOf(MarketDataSyncJobName.B3_CORPORATE_EVENTS)
        val missingDateDrivenJobs = dateDrivenJobs.filter { controls[it]?.lastSyncedDate == null }
        val safeDate =
            if (missingDateDrivenJobs.isNotEmpty()) {
                null
            } else {
                dateDrivenJobs.mapNotNull { controls[it]?.lastSyncedDate }.minOrNull()
            }
        val missingSnapshotJobs =
            snapshotJobs.filter { jobName ->
                val control = controls[jobName] ?: return@filter true
                val sourceRunAt = control.sourceLastRunAt ?: return@filter true
                val syncCompletedAt = control.lastSyncCompletedAt ?: return@filter true
                if (syncCompletedAt.isBefore(sourceRunAt)) {
                    return@filter true
                }
                safeDate?.let { sourceRunAt.atZone(ZoneOffset.UTC).toLocalDate().isBefore(it) } ?: false
            }
        return MarketDataValuationReadiness(
            safeValuationDate = safeDate,
            missingJobs = missingDateDrivenJobs + missingSnapshotJobs,
        )
    }
}
