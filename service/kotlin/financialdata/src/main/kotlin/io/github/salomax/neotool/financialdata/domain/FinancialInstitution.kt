package io.github.salomax.neotool.financialdata.domain

import com.fasterxml.jackson.databind.JsonNode
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionEntity
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
) {
    fun toEntity(): FinancialInstitutionEntity {
        return FinancialInstitutionEntity(
            id = this.id,
            parentCnpj = this.parentCnpj,
            legalName = this.legalName,
            tradeName = this.tradeName,
            emailDomain = this.emailDomain,
            website = this.website,
            assetId = this.assetId,
            socialNetwork = this.socialNetwork,
            receitaData = this.receitaData,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}
