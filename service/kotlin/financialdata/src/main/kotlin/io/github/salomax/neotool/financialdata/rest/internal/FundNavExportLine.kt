package io.github.salomax.neotool.financialdata.rest.internal

import io.micronaut.serde.annotation.Serdeable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Serdeable
data class FundNavExportLine(
    val cursor: String,
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
    val updatedAt: Instant,
)
