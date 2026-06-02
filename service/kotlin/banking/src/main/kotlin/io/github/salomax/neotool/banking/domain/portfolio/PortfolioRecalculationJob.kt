package io.github.salomax.neotool.banking.domain.portfolio

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PortfolioRecalculationJob(
    val id: UUID? = null,
    val financialAccountId: UUID? = null,
    val accountId: UUID,
    val status: PortfolioRecalculationStatus,
    val recalculateFrom: LocalDate,
    val triggeredBy: String,
    val daysProcessed: Int? = null,
    /** Machine-readable code for UI i18n (aligned with [io.github.salomax.neotool.common.error.ErrorCode]). */
    val errorCode: String? = null,
    /** Optional technical detail for support/logs — not intended as the primary user-facing string. */
    val errorDetails: String? = null,
    /** Identifies the pod/instance that atomically claimed this job. Null when not PROCESSING. */
    val processingNodeId: String? = null,
    /** Updated every heartbeat interval during the day loop. Used to detect zombie jobs. */
    val lastHeartbeatAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
