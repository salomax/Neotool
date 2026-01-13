package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing detailed report information with multiple accounts.
 */
data class ReportDetails(
    /**
     * Report ID (numero_relatorio from bacen_bank_report)
     */
    val reportId: String,

    /**
     * Report name from bacen_bank_report_catalog
     */
    val name: String?,

    /**
     * List of accounts in this report, each with its own values by quarter
     */
    val accounts: List<ReportAccount>,
)
