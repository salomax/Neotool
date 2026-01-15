package io.github.salomax.neotool.financialdata.domain

/**
 * Domain object representing a report descriptor.
 *
 * In reportsSummary: Only reportId and name are populated.
 * In reportDetails: All fields are populated including group, accountId, accountName.
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
     * Account id (normalized account name like "ATIVO_TOTAL") from bacen_bank_report_account.
     * Only present in reportDetails, not in reportsSummary.
     */
    val accountId: String? = null,

    /**
     * Account name (display name from nome_coluna) from bacen_bank_report_account.
     * Only present in reportDetails, not in reportsSummary.
     */
    val accountName: String? = null,
)
