package io.github.salomax.neotool.security.service

import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.repo.PermissionRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@Singleton
open class PermissionService(
    private val permissionRepository: PermissionRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findById(id: UUID): Permission? {
        return permissionRepository.findById(id).orElse(null)?.toDomain()
    }

    fun findByName(name: String): Permission? {
        return permissionRepository.findByName(name).orElse(null)?.toDomain()
    }

    fun findAll(): List<Permission> {
        return permissionRepository.findAll().map { it.toDomain() }
    }

    @Transactional
    open fun create(permission: Permission): Permission {
        val entity = permission.toEntity()
        val saved = permissionRepository.save(entity)
        logger.info { "Permission created: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun update(permission: Permission): Permission {
        val existing =
            permissionRepository.findById(
                permission.id ?: throw IllegalArgumentException("Permission ID is required for update"),
            )
                .orElseThrow { IllegalArgumentException("Permission not found with ID: ${permission.id}") }

        existing.name = permission.name
        existing.updatedAt = permission.updatedAt

        val saved = permissionRepository.update(existing)
        logger.info { "Permission updated: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun delete(id: UUID) {
        permissionRepository.deleteById(id)
        logger.info { "Permission deleted: ID $id" }
    }
}
