package io.github.salomax.neotool.banking.domain.import

/**
 * Lifecycle status of an import job.
 */
enum class ImportJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REVIEW_REQUIRED,
;

    fun isTerminal(): Boolean = this in setOf(COMPLETED, FAILED, REVIEW_REQUIRED)
}
