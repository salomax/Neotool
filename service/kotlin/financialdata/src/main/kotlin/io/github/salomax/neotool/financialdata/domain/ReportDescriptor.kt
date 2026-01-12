package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing a report descriptor (metadata only, no values).
 * Used in reportsSummary to list available reports without loading detailed values.
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
)
