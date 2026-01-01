package io.github.salomax.neotool.security.graphql

import com.apollographql.federation.graphqljava.Federation
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.analysis.MaxQueryDepthInstrumentation
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.model.PrincipalEntity
import io.github.salomax.neotool.security.model.UserEntity
import io.github.salomax.neotool.security.repo.GroupRepository
import io.github.salomax.neotool.security.repo.PermissionRepository
import io.github.salomax.neotool.security.repo.PrincipalRepository
import io.github.salomax.neotool.security.repo.RoleRepository
import io.github.salomax.neotool.security.repo.UserRepository
import io.github.salomax.neotool.security.service.PrincipalType
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.util.UUID

@Factory
class SecurityGraphQLFactory(
    private val registry: TypeDefinitionRegistry,
    private val wiringFactory: SecurityWiringFactory,
    private val userRepository: UserRepository,
    private val groupRepository: GroupRepository,
    private val roleRepository: RoleRepository,
    private val permissionRepository: PermissionRepository,
    private val principalRepository: PrincipalRepository,
) {
    @Singleton
    fun graphQL(): graphql.GraphQL {
        val runtimeWiring = wiringFactory.build()

        // Federation requires fetchEntities and resolveEntityType even if not actively used
        val federatedSchema =
            Federation.transform(registry, runtimeWiring)
                .fetchEntities { env ->
                    val reps = env.getArgument<List<Map<String, Any>>>("representations")
                    reps?.map { rep ->
                        val id = rep["id"]
                        if (id == null) {
                            null
                        } else {
                            try {
                                when (rep["__typename"]) {
                                    "User" -> {
                                        val user = userRepository.findById(UUID.fromString(id.toString())).orElse(null)
                                        user?.let { userEntity: UserEntity ->
                                            val userId =
                                                userEntity.id ?: throw IllegalArgumentException(
                                                    "User must have an ID",
                                                )
                                            val enabled =
                                                principalRepository
                                                    .findByPrincipalTypeAndExternalId(
                                                        PrincipalType.USER,
                                                        userId.toString(),
                                                    )
                                                    .map { principal: PrincipalEntity -> principal.enabled }
                                                    .orElse(true)
                                            UserDTO(
                                                id = userId.toString(),
                                                email = userEntity.email,
                                                displayName = userEntity.displayName,
                                                enabled = enabled,
                                                createdAt = userEntity.createdAt.toString(),
                                                updatedAt = userEntity.updatedAt.toString(),
                                            )
                                        }
                                    }
                                    "Group" -> {
                                        val group =
                                            groupRepository.findById(
                                                UUID.fromString(id.toString()),
                                            )
                                                .orElse(null)
                                        group?.let {
                                            val groupDomain = it.toDomain()
                                            GroupDTO(
                                                id =
                                                    groupDomain.id?.toString()
                                                        ?: throw IllegalArgumentException("Group must have an ID"),
                                                name = groupDomain.name,
                                                description = groupDomain.description,
                                                createdAt = groupDomain.createdAt.toString(),
                                                updatedAt = groupDomain.updatedAt.toString(),
                                            )
                                        }
                                    }
                                    "Role" -> {
                                        val role =
                                            roleRepository.findById(
                                                UUID.fromString(id.toString()),
                                            )
                                                .orElse(null)
                                        role?.let {
                                            val roleDomain = it.toDomain()
                                            RoleDTO(
                                                id =
                                                    roleDomain.id?.toString()
                                                        ?: throw IllegalArgumentException("Role must have an ID"),
                                                name = roleDomain.name,
                                                createdAt = roleDomain.createdAt.toString(),
                                                updatedAt = roleDomain.updatedAt.toString(),
                                            )
                                        }
                                    }
                                    "Permission" -> {
                                        val permission =
                                            permissionRepository.findById(UUID.fromString(id.toString()))
                                                .orElse(null)
                                        permission?.let {
                                            val permissionDomain = it.toDomain()
                                            PermissionDTO(
                                                id =
                                                    permissionDomain.id?.toString()
                                                        ?: throw IllegalArgumentException("Permission must have an ID"),
                                                name = permissionDomain.name,
                                            )
                                        }
                                    }
                                    else -> null
                                }
                            } catch (e: Exception) {
                                // Log and return null if ID conversion fails
                                val logger = org.slf4j.LoggerFactory.getLogger(SecurityGraphQLFactory::class.java)
                                logger.debug(
                                    "Failed to fetch entity for federation: ${rep["__typename"]} with id: $id",
                                    e,
                                )
                                null
                            }
                        }
                    }
                }
                .resolveEntityType { env ->
                    val entity = env.getObject<Any?>()
                    val schema = env.schema

                    if (schema == null) {
                        throw IllegalStateException("GraphQL schema is null in resolveEntityType")
                    }

                    when (entity) {
                        is UserDTO ->
                            schema.getObjectType("User")
                                ?: throw IllegalStateException("User type not found in schema")
                        is GroupDTO ->
                            schema.getObjectType("Group")
                                ?: throw IllegalStateException("Group type not found in schema")
                        is RoleDTO ->
                            schema.getObjectType("Role")
                                ?: throw IllegalStateException("Role type not found in schema")
                        is PermissionDTO ->
                            schema.getObjectType("Permission")
                                ?: throw IllegalStateException("Permission type not found in schema")
                        else -> throw IllegalStateException(
                            "Unknown federated type for entity: ${entity?.javaClass?.name}",
                        )
                    }
                }
                .build()

        return graphql.GraphQL.newGraphQL(federatedSchema)
            .instrumentation(MaxQueryComplexityInstrumentation(100))
            .instrumentation(MaxQueryDepthInstrumentation(10))
            .defaultDataFetcherExceptionHandler(SecurityGraphQLExceptionHandler())
            .build()
    }
}
