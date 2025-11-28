package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging

@Singleton
open class RoleService(
    private val roleRepository: RoleRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findById(id: Int): Role? {
        return roleRepository.findById(id).orElse(null)?.toDomain()
    }

    fun findByName(name: String): Role? {
        return roleRepository.findByName(name).orElse(null)?.toDomain()
    }

    fun findAll(): List<Role> {
        return roleRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    open fun create(role: Role): Role {
        val entity = role.toEntity()
        val saved = roleRepository.save(entity)
        logger.info { "Role created: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun update(role: Role): Role {
        val existing =
            roleRepository.findById(role.id ?: throw IllegalArgumentException("Role ID is required for update"))
                .orElseThrow { IllegalArgumentException("Role not found with ID: ${role.id}") }

        existing.name = role.name
        existing.updatedAt = role.updatedAt

        val saved = roleRepository.update(existing)
        logger.info { "Role updated: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun delete(id: Int) {
        roleRepository.deleteById(id)
        logger.info { "Role deleted: ID $id" }
    }
}
