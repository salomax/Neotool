package io.github.salomax.neotool.banking.graphql.resolver

import io.github.salomax.neotool.banking.domain.issuer.IssuerCategory
import io.github.salomax.neotool.banking.domain.issuer.IssuerRepository
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
import jakarta.inject.Singleton

/**
 * GraphQL query resolver for [Issuer] operations.
 */
@Singleton
class IssuerResolver(
    private val issuerRepository: IssuerRepository,
) {
    fun listIssuers(categories: List<IssuerCategory>? = null): List<IssuerDTO> {
        val issuers =
            if (categories.isNullOrEmpty()) {
                issuerRepository.findAllActive()
            } else {
                issuerRepository.findActiveByCategories(categories)
            }

        return issuers.map { issuer ->
            IssuerDTO(
                id = issuer.id!!,
                name = issuer.name,
                domain = issuer.domain,
                compeCode = issuer.compeCode,
                ispb = issuer.ispb,
                countryCode = issuer.countryCode,
                logoKey = issuer.logoKey,
                category = issuer.category,
                logoAsset =
                    issuer.logoKey?.let { key ->
                        mapOf("__typename" to "Asset", "id" to key)
                    },
            )
        }
    }
}
