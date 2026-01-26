package io.github.salomax.neotool.financialdata.service

import io.github.salomax.neotool.financialdata.domain.RatingIssuer
import io.github.salomax.neotool.financialdata.repo.RatingIssuerRepository
import jakarta.inject.Singleton

@Singleton
open class RatingIssuerService(
    private val repository: RatingIssuerRepository,
) {
    /**
     * Retrieves all enabled rating issuers.
     */
    fun getEnabledIssuers(): List<RatingIssuer> {
        return repository.findByEnabledTrue().map { it.toDomain() }
    }

    /**
     * Retrieves rating issuers by their IDs.
     */
    fun getIssuersByIds(ids: Collection<java.util.UUID>): Map<java.util.UUID, RatingIssuer> {
        if (ids.isEmpty()) {
            return emptyMap()
        }
        return repository.findByIdIn(ids).associateBy { it.id!! }.mapValues { it.value.toDomain() }
    }
}
