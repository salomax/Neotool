package io.github.salomax.neotool.banking.domain.investmentimport

enum class InvestmentImportJobStatus {
    AWAITING_REVIEW,
    IMPORTING,
    DONE,
    FAILED,
    ;

    fun isTerminal(): Boolean = this == DONE || this == FAILED
}
