package io.github.salomax.neotool.financialdata.domain

import java.time.Instant
import java.util.UUID

data class BacenInstitutionReport(
    val id: UUID? = null,
    val codInst: String,
    val anoMes: String,
    val nomeRelatorio: String,
    val numeroRelatorio: String,
    val tipoInstituicao: Int,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
