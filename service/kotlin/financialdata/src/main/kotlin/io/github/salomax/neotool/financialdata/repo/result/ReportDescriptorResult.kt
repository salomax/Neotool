package io.github.salomax.neotool.financialdata.repo.result

/**
 * Result object for report descriptor queries (metadata only, no values).
 */
data class ReportDescriptorResult(
    val quarter: String, // "YYYYMM"
    val reportId: String, // numero_relatorio
    val name: String?, // nome_relatorio from catalog
    val group: String?,
    val accountNumber: String?,
    val columnName: String?,
)
