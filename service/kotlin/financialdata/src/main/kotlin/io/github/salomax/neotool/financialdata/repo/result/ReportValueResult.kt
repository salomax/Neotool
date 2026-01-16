package io.github.salomax.neotool.financialdata.repo.result

import java.math.BigDecimal

/**
 * Result object for report value queries.
 */
data class ReportValueResult(
    // "YYYYMM"
    val quarter: String,
    // numero_relatorio
    val reportId: String,
    // nome_relatorio from catalog
    val name: String?,
    val accountGroup: String?,
    val accountId: String?,
    val accountName: String?,
    // saldo from bacen_bank_report_data
    val value: BigDecimal?,
)
