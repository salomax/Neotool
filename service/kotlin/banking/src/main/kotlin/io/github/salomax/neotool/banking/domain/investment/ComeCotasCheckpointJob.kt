package io.github.salomax.neotool.banking.domain.investment

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ComeCotasCheckpointJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}

data class ComeCotasCheckpointJob(
    val id: UUID? = null,
    val holdingId: UUID,
    val financialAccountId: UUID,
    val accountId: UUID,
    val checkpointDate: LocalDate,
    val status: ComeCotasCheckpointJobStatus,
    val processingNodeId: String? = null,
    val lastHeartbeatAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val nextAttemptAt: Instant? = null,
    val attemptCount: Int = 0,
    val errorCode: String? = null,
    val errorDetails: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
