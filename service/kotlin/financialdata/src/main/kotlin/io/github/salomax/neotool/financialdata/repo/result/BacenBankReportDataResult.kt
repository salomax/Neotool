package io.github.salomax.neotool.financialdata.repo.result

import java.math.BigDecimal

/**
 * Result object for bacen_bank_report data queries.
 * Contains structured report data from bacen_bank_report and related tables.
 */
data class BacenBankReportDataResult(
    val bacenCodInst: String,
    val anoMesRef: String,
    val reportNumber: String, // numero_relatorio
    val reportName: String?, // nome_relatorio
    val group: String?,
    val account: String?, // Original account number (conta) - kept for backward compatibility
    val canonicalAccountName: String?, // Normalized canonical account name
    val columnName: String?,
    val balance: BigDecimal?, // saldo
)
