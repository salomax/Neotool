package io.github.salomax.neotool.banking.domain.pdfimport

enum class PdfImportJobStatus {
    PENDING,
    EXTRACTING,
    EXTRACTED,
    LLM_PROCESSING,
    AWAITING_REVIEW,
    IMPORTING,
    DONE,
    FAILED,
}

