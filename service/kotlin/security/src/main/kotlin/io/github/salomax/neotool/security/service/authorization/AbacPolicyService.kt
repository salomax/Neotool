package io.github.salomax.neotool.security.service.authorization

import io.github.salomax.neotool.security.domain.abac.AbacPolicy
import io.github.salomax.neotool.security.repo.AbacPolicyRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@Singleton
open class AbacPolicyService(
    private val abacPolicyRepository: AbacPolicyRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findById(id: UUID): AbacPolicy? = abacPolicyRepository.findById(id).orElse(null)?.toDomain()

    fun findByName(name: String): AbacPolicy? = abacPolicyRepository.findByName(name).orElse(null)?.toDomain()

    fun findAll(): List<AbacPolicy> = abacPolicyRepository.findAll().map { it.toDomain() }

    fun findActivePolicies(): List<AbacPolicy> = abacPolicyRepository.findActivePolicies().map { it.toDomain() }

    @Transactional
    open fun create(policy: AbacPolicy): AbacPolicy {
        val entity = policy.toEntity()
        val saved = abacPolicyRepository.save(entity)
        logger.info { "ABAC policy created: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun update(policy: AbacPolicy): AbacPolicy {
        val existing =
            abacPolicyRepository
                .findById(
                    policy.id ?: throw IllegalArgumentException("Policy ID is required for update"),
                ).orElseThrow { IllegalArgumentException("Policy not found with ID: ${policy.id}") }

        existing.name = policy.name
        existing.description = policy.description
        existing.effect = policy.effect
        existing.condition = policy.condition
        existing.version = policy.version
        existing.isActive = policy.isActive
        existing.updatedAt = policy.updatedAt

        val saved = abacPolicyRepository.update(existing)
        logger.info { "ABAC policy updated: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun delete(id: UUID) {
        abacPolicyRepository.deleteById(id)
        logger.info { "ABAC policy deleted: ID $id" }
    }
}

