package io.github.salomax.neotool.financialdata.repo.result

import java.math.BigDecimal

/**
 * Result object for bacen_bank_report data queries.
 * Contains structured report data from bacen_bank_report and related tables.
 */
data class BacenBankReportDataResult(
    val bacenCodInst: String,
    val anoMesRef: String,
    // numero_relatorio
    val reportNumber: String,
    // nome_relatorio
    val reportName: String?,
    val group: String?,
    // Original account number (conta) - kept for backward compatibility
    val account: String?,
    // Normalized canonical account name
    val canonicalAccountName: String?,
    val columnName: String?,
    // saldo
    val balance: BigDecimal?,
)
