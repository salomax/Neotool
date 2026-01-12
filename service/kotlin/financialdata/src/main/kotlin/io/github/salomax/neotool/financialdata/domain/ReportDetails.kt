package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing detailed report information with values by quarter.
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
     * Group from bacen_bank_report_account
     */
    val group: String?,

    /**
     * Account number (conta) from bacen_bank_report_account
     */
    val accountNumber: String?,

    /**
     * Column name (nome_coluna) from bacen_bank_report_account
     */
    val columnName: String?,

    /**
     * Values by quarter for this report
     */
    val valuesByQuarter: List<QuarterValue>,
)
