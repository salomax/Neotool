package io.github.salomax.neotool.banking.domain.billimport

enum class BillImportJobStatus {
    PENDING,
    EXTRACTING,
    EXTRACTED,
    LLM_PROCESSING,
    AWAITING_REVIEW,
    IMPORTING,
    DONE,
    FAILED,
}
