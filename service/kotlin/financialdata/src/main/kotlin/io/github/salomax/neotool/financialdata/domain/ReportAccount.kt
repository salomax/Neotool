package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing an account within a report.
 * Each account has its own group, account number, column name, and values by quarter.
 */
data class ReportAccount(
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
     * Values by quarter for this account
     */
    val valuesByQuarter: List<QuarterValue>,
)
