package io.github.salomax.neotool.financialdata.repo.result

import java.math.BigDecimal

/**
 * Result object for report value queries.
 */
data class ReportValueResult(
    val quarter: String, // "YYYYMM"
    val reportId: String, // numero_relatorio
    val name: String?, // nome_relatorio from catalog
    val accountGroup: String?,
    val accountId: String?,
    val accountName: String?,
    val value: BigDecimal?, // saldo from bacen_bank_report_data
)
