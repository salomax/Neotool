package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing an account within a report.
 * Each account has its own group, account id, account name, and values by quarter.
 */
data class ReportAccount(
    /**
     * Group from bacen_bank_report_account
     */
    val group: String?,
    /**
     * Account id (canonical normalized name like "ATIVO_TOTAL") from bacen_bank_report_account_canonical
     */
    val accountId: String?,
    /**
     * Account name (display name from canonical account) from bacen_bank_report_account_canonical
     */
    val accountName: String?,
    /**
     * Values by quarter for this account
     */
    val valuesByQuarter: List<QuarterValue>,
)
