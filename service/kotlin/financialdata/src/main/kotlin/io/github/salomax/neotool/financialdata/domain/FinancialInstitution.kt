package io.github.salomax.neotool.financialdata.domain

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class FinancialInstitution(
    val id: UUID? = null,
    val parentCnpj: String,
    val legalName: String? = null,
    val tradeName: String? = null,
    val emailDomain: String? = null,
    val website: String? = null,
    val assetId: UUID? = null,
    val socialNetwork: JsonNode? = null,
    val receitaData: JsonNode? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
