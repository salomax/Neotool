package io.github.salomax.neotool.banking.domain.investment

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class TaxRecalculationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

data class TaxRecalculationJob(
    val id: UUID? = null,
    val holdingId: UUID,
    val financialAccountId: UUID,
    val accountId: UUID,
    val status: TaxRecalculationStatus,
    val recalculateFrom: LocalDate,
    val triggeredBy: String,
    val daysProcessed: Int? = null,
    val errorCode: String? = null,
    val errorDetails: String? = null,
    val processingNodeId: String? = null,
    val lastHeartbeatAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
