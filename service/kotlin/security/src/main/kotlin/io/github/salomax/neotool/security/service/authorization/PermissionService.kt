package io.github.salomax.neotool.security.service.authorization

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.domain.rbac.Permission
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.repo.PermissionRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@TraceExecution
@Singleton
open class PermissionService(
    private val permissionRepository: PermissionRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findById(id: UUID): Permission? = permissionRepository.findById(id).orElse(null)?.toDomain()

    fun findByName(name: String): Permission? = permissionRepository.findByName(name).orElse(null)?.toDomain()

    fun findAll(): List<Permission> = permissionRepository.findAll().map { it.toDomain() }

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
            permissionRepository
                .findById(
                    permission.id
                        ?: throw ValidationException(
                            errorCode = CommonErrorCode.INVALID_INPUT,
                            field = "permissionId",
                        ),
                ).orElseThrow {
                    NotFoundException(
                        errorCode = SecurityErrorCode.PERMISSION_NOT_FOUND,
                        field = "permissionId",
                    )
                }

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
