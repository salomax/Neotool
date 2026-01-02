package io.github.salomax.neotool.financialdata.domain

import com.fasterxml.jackson.databind.JsonNode
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionEntity
import java.time.Instant

data class FinancialInstitution(
    val parentCnpj: String,
    val legalName: String? = null,
    val tradeName: String? = null,
    val emailDomain: String? = null,
    val website: String? = null,
    val logoStorageKey: String? = null,
    val socialNetwork: JsonNode? = null,
    val receitaData: JsonNode? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
) {
    fun toEntity(): FinancialInstitutionEntity {
        return FinancialInstitutionEntity(
            parentCnpj = this.parentCnpj,
            legalName = this.legalName,
            tradeName = this.tradeName,
            emailDomain = this.emailDomain,
            website = this.website,
            logoStorageKey = this.logoStorageKey,
            socialNetwork = this.socialNetwork,
            receitaData = this.receitaData,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
        )
    }
}

