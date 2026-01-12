package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing a report descriptor.
 *
 * In reportsSummary: Only reportId and name are populated.
 * In reportDetails: All fields are populated including group, accountNumber, columnName.
 */
data class ReportDescriptor(
    /**
     * Report ID (numero_relatorio from bacen_bank_report)
     */
    val reportId: String,

    /**
     * Report name from bacen_bank_report_catalog
     */
    val name: String?,

    /**
     * Group from bacen_bank_report_account.
     * Only present in reportDetails, not in reportsSummary.
     */
    val group: String? = null,

    /**
     * Account number (conta) from bacen_bank_report_account.
     * Only present in reportDetails, not in reportsSummary.
     */
    val accountNumber: String? = null,

    /**
     * Column name (nome_coluna) from bacen_bank_report_account.
     * Only present in reportDetails, not in reportsSummary.
     */
    val columnName: String? = null,
)
