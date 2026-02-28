package io.github.salomax.neotool.security.service.management

import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.NotFoundException
import io.github.salomax.neotool.common.error.ValidationException
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.domain.rbac.Role
import io.github.salomax.neotool.security.error.SecurityErrorCode
import io.github.salomax.neotool.security.repo.RoleRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import mu.KotlinLogging
import java.util.UUID

@TraceExecution
@Singleton
open class RoleService(
    private val roleRepository: RoleRepository,
) {
    private val logger = KotlinLogging.logger {}

    fun findById(id: UUID): Role? = roleRepository.findById(id).orElse(null)?.toDomain()

    fun findByName(name: String): Role? = roleRepository.findByName(name).orElse(null)?.toDomain()

    fun findAll(): List<Role> = roleRepository.findAll().map { it.toDomain() }

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
            roleRepository
                .findById(
                    role.id
                        ?: throw ValidationException(
                            errorCode = CommonErrorCode.INVALID_INPUT,
                            field = "roleId",
                        ),
                ).orElseThrow {
                    NotFoundException(
                        errorCode = SecurityErrorCode.ROLE_NOT_FOUND,
                        field = "roleId",
                    )
                }

        existing.name = role.name
        existing.updatedAt = role.updatedAt

        val saved = roleRepository.update(existing)
        logger.info { "Role updated: ${saved.name} (ID: ${saved.id})" }
        return saved.toDomain()
    }

    @Transactional
    open fun delete(id: UUID) {
        roleRepository.deleteById(id)
        logger.info { "Role deleted: ID $id" }
    }
}
