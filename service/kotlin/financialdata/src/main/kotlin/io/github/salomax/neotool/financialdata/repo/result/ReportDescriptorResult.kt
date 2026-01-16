package io.github.salomax.neotool.financialdata.repo.result

/**
 * Result object for report descriptor queries (metadata only, no values).
 */
data class ReportDescriptorResult(
    // "YYYYMM"
    val quarter: String,
    // numero_relatorio
    val reportId: String,
    // nome_relatorio from catalog
    val name: String?,
    val group: String?,
    val accountId: String?,
    val accountName: String?,
)
