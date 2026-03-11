package io.github.salomax.neotool.banking.domain.import

import io.micronaut.serde.annotation.Serdeable

/**
 * Lifecycle status of an import job.
 */
@Serdeable
enum class ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVIEW_REQUIRED,
    ;

    fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, REVIEW_REQUIRED)
}
