package io.github.salomax.neotool.security.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.payload.GraphQLPayloadFactory
import io.github.salomax.neotool.security.graphql.dto.AuthorizationResultDTO
import io.github.salomax.neotool.security.graphql.dto.GroupConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.GroupDTO
import io.github.salomax.neotool.security.graphql.dto.GroupEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.PageInfoDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionDTO
import io.github.salomax.neotool.security.graphql.dto.PermissionEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.RequestPasswordResetPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.RoleConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.RoleEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.dto.UserEdgeDTO
import io.github.salomax.neotool.security.graphql.mapper.GroupManagementMapper
import io.github.salomax.neotool.security.graphql.mapper.RoleManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import jakarta.inject.Singleton

/**
 * Security module wiring factory for GraphQL resolvers
 */
@Singleton
class SecurityWiringFactory(
    private val authResolver: SecurityAuthResolver,
    private val authorizationResolver: io.github.salomax.neotool.security.graphql.resolver.AuthorizationResolver,
    private val userManagementResolver: UserManagementResolver,
    private val groupManagementResolver: GroupManagementResolver,
    private val roleManagementResolver: RoleManagementResolver,
    private val permissionManagementResolver: PermissionManagementResolver,
    private val groupManagementMapper: GroupManagementMapper,
    private val roleManagementMapper: RoleManagementMapper,
    resolverRegistry: GraphQLResolverRegistry,
) : GraphQLWiringFactory() {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("auth", authResolver)
        resolverRegistry.register("authorization", authorizationResolver)
        resolverRegistry.register("userManagement", userManagementResolver)
        resolverRegistry.register("groupManagement", groupManagementResolver)
        resolverRegistry.register("roleManagement", roleManagementResolver)
        resolverRegistry.register("permissionManagement", permissionManagementResolver)
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "currentUser",
                createValidatedDataFetcher { env ->
                    // Extract token from GraphQL context
                    val token =
                        try {
                            env.graphQlContext.get<String?>("token")
                        } catch (e: Exception) {
                            null
                        }
                    authResolver.getCurrentUser(token)
                },
            )
            .dataFetcher(
                "checkPermission",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    val permission = getRequiredString(env, "permission")
                    val resourceId = env.getArgument<String?>("resourceId")
                    authorizationResolver.checkPermission(userId, permission, resourceId)
                },
            )
            .dataFetcher(
                "getUserPermissions",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    authorizationResolver.getUserPermissions(userId)
                },
            )
            .dataFetcher(
                "getUserRoles",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    authorizationResolver.getUserRoles(userId)
                },
            )
            .dataFetcher(
                "users",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    userManagementResolver.users(first, after, query)
                },
            )
            .dataFetcher(
                "groups",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    groupManagementResolver.groups(first, after, query)
                },
            )
            .dataFetcher(
                "roles",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    roleManagementResolver.roles(first, after, query)
                },
            )
            .dataFetcher(
                "permissions",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    permissionManagementResolver.permissions(first, after, query)
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "signIn",
                createMutationDataFetcher<SignInPayloadDTO>("signIn") { input ->
                    authResolver.signIn(input)
                },
            )
            .dataFetcher(
                "signInWithOAuth",
                createMutationDataFetcher<SignInPayloadDTO>("signInWithOAuth") { input ->
                    authResolver.signInWithOAuth(input)
                },
            )
            .dataFetcher(
                "signUp",
                createMutationDataFetcher<SignUpPayloadDTO>("signUp") { input ->
                    authResolver.signUp(input)
                },
            )
            .dataFetcher(
                "requestPasswordReset",
                createMutationDataFetcher<RequestPasswordResetPayloadDTO>("requestPasswordReset") { input ->
                    authResolver.requestPasswordReset(input)
                },
            )
            .dataFetcher(
                "resetPassword",
                createMutationDataFetcher<ResetPasswordPayloadDTO>("resetPassword") { input ->
                    authResolver.resetPassword(input)
                },
            )
            .dataFetcher(
                "enableUser",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    userManagementResolver.enableUser(userId)
                },
            )
            .dataFetcher(
                "disableUser",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    userManagementResolver.disableUser(userId)
                },
            )
            .dataFetcher(
                "createGroup",
                createMutationDataFetcher<GroupDTO>("createGroup") { input ->
                    try {
                        val dto = groupManagementMapper.mapToCreateGroupInputDTO(input)
                        val result = groupManagementResolver.createGroup(dto)
                        GraphQLPayloadFactory.success(result)
                    } catch (e: Exception) {
                        GraphQLPayloadFactory.error(e)
                    }
                },
            )
            .dataFetcher(
                "updateGroup",
                createValidatedDataFetcher { env ->
                    val groupId = getRequiredString(env, "groupId")
                    val inputMap =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                    val dto = groupManagementMapper.mapToUpdateGroupInputDTO(inputMap)
                    groupManagementResolver.updateGroup(groupId, dto)
                },
            )
            .dataFetcher(
                "deleteGroup",
                createValidatedDataFetcher { env ->
                    val groupId = getRequiredString(env, "groupId")
                    groupManagementResolver.deleteGroup(groupId)
                },
            )
            .dataFetcher(
                "createRole",
                createMutationDataFetcher<RoleDTO>("createRole") { input ->
                    try {
                        val dto = roleManagementMapper.mapToCreateRoleInputDTO(input)
                        val result = roleManagementResolver.createRole(dto)
                        GraphQLPayloadFactory.success(result)
                    } catch (e: Exception) {
                        GraphQLPayloadFactory.error(e)
                    }
                },
            )
            .dataFetcher(
                "updateRole",
                createValidatedDataFetcher { env ->
                    val roleId = getRequiredString(env, "roleId")
                    val inputMap =
                        env.getArgument<Map<String, Any?>>("input")
                            ?: throw IllegalArgumentException("input is required")
                    val dto = roleManagementMapper.mapToUpdateRoleInputDTO(inputMap)
                    roleManagementResolver.updateRole(roleId, dto)
                },
            )
            .dataFetcher(
                "deleteRole",
                createValidatedDataFetcher { env ->
                    val roleId = getRequiredString(env, "roleId")
                    roleManagementResolver.deleteRole(roleId)
                },
            )
            .dataFetcher(
                "assignPermissionToRole",
                createValidatedDataFetcher { env ->
                    val roleId = getRequiredString(env, "roleId")
                    val permissionId = getRequiredString(env, "permissionId")
                    roleManagementResolver.assignPermissionToRole(roleId, permissionId)
                },
            )
            .dataFetcher(
                "removePermissionFromRole",
                createValidatedDataFetcher { env ->
                    val roleId = getRequiredString(env, "roleId")
                    val permissionId = getRequiredString(env, "permissionId")
                    roleManagementResolver.removePermissionFromRole(roleId, permissionId)
                },
            )
            .dataFetcher(
                "assignRoleToUser",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    val roleId = getRequiredString(env, "roleId")
                    userManagementResolver.assignRoleToUser(userId, roleId)
                },
            )
            .dataFetcher(
                "removeRoleFromUser",
                createValidatedDataFetcher { env ->
                    val userId = getRequiredString(env, "userId")
                    val roleId = getRequiredString(env, "roleId")
                    userManagementResolver.removeRoleFromUser(userId, roleId)
                },
            )
            .dataFetcher(
                "assignRoleToGroup",
                createValidatedDataFetcher { env ->
                    val groupId = getRequiredString(env, "groupId")
                    val roleId = getRequiredString(env, "roleId")
                    groupManagementResolver.assignRoleToGroup(groupId, roleId)
                },
            )
            .dataFetcher(
                "removeRoleFromGroup",
                createValidatedDataFetcher { env ->
                    val groupId = getRequiredString(env, "groupId")
                    val roleId = getRequiredString(env, "roleId")
                    groupManagementResolver.removeRoleFromGroup(groupId, roleId)
                },
            )
    }

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder
            .type("User") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.id
                    },
                )
                type.dataFetcher(
                    "email",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.email
                    },
                )
                type.dataFetcher(
                    "displayName",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.displayName
                    },
                )
                type.dataFetcher(
                    "enabled",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.enabled ?: true
                    },
                )
                type.dataFetcher(
                    "roles",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.let { userManagementResolver.resolveUserRoles(it.id) } ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "groups",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.let { userManagementResolver.resolveUserGroups(it.id) } ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "permissions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        user?.let { userManagementResolver.resolveUserPermissions(it.id) } ?: emptyList()
                    },
                )
            }
            .type("SignInPayload") { type ->
                type.dataFetcher(
                    "token",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.token
                    },
                )
                type.dataFetcher(
                    "refreshToken",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.refreshToken
                    },
                )
                type.dataFetcher(
                    "user",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignInPayloadDTO>()
                        payload?.user
                    },
                )
            }
            .type("SignUpPayload") { type ->
                type.dataFetcher(
                    "token",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.token
                    },
                )
                type.dataFetcher(
                    "refreshToken",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.refreshToken
                    },
                )
                type.dataFetcher(
                    "user",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<SignUpPayloadDTO>()
                        payload?.user
                    },
                )
            }
            .type("RequestPasswordResetPayload") { type ->
                type.dataFetcher(
                    "success",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<RequestPasswordResetPayloadDTO>()
                        payload?.success
                    },
                )
                type.dataFetcher(
                    "message",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<RequestPasswordResetPayloadDTO>()
                        payload?.message
                    },
                )
            }
            .type("ResetPasswordPayload") { type ->
                type.dataFetcher(
                    "success",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<ResetPasswordPayloadDTO>()
                        payload?.success
                    },
                )
                type.dataFetcher(
                    "message",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val payload = env.getSource<ResetPasswordPayloadDTO>()
                        payload?.message
                    },
                )
            }
            .type("AuthorizationResult") { type ->
                type.dataFetcher(
                    "allowed",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val result = env.getSource<AuthorizationResultDTO>()
                        result?.allowed
                    },
                )
                type.dataFetcher(
                    "reason",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val result = env.getSource<AuthorizationResultDTO>()
                        result?.reason
                    },
                )
            }
            .type("Permission") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        permission?.id
                    },
                )
                type.dataFetcher(
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        permission?.name
                    },
                )
            }
            .type("Role") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        role?.id
                    },
                )
                type.dataFetcher(
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        role?.name
                    },
                )
                type.dataFetcher(
                    "permissions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        role?.id?.let { roleManagementResolver.resolveRolePermissions(it) } ?: emptyList()
                    },
                )
            }
            .type("Group") { type ->
                type.dataFetcher(
                    "id",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        group?.id
                    },
                )
                type.dataFetcher(
                    "name",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        group?.name
                    },
                )
                type.dataFetcher(
                    "description",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        group?.description
                    },
                )
                type.dataFetcher(
                    "roles",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        group?.let { groupManagementResolver.resolveGroupRoles(it.id) } ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "members",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        group?.let { groupManagementResolver.resolveGroupMembers(it.id) } ?: emptyList()
                    },
                )
            }
            .type("Permission") { type ->
                type.dataFetcher(
                    "roles",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        permission?.id?.let { permissionManagementResolver.resolvePermissionRoles(it) } ?: emptyList()
                    },
                )
            }
            // Relay pagination type resolvers
            .type("PageInfo") { type ->
                type.dataFetcher(
                    "hasNextPage",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val pageInfo = env.getSource<PageInfoDTO>()
                        pageInfo?.hasNextPage ?: false
                    },
                )
                type.dataFetcher(
                    "hasPreviousPage",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val pageInfo = env.getSource<PageInfoDTO>()
                        pageInfo?.hasPreviousPage ?: false
                    },
                )
                type.dataFetcher(
                    "startCursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val pageInfo = env.getSource<PageInfoDTO>()
                        pageInfo?.startCursor
                    },
                )
                type.dataFetcher(
                    "endCursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val pageInfo = env.getSource<PageInfoDTO>()
                        pageInfo?.endCursor
                    },
                )
            }
            .type("UserEdge") { type ->
                type.dataFetcher(
                    "node",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<UserEdgeDTO>()
                        edge?.node
                    },
                )
                type.dataFetcher(
                    "cursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<UserEdgeDTO>()
                        edge?.cursor
                    },
                )
            }
            .type("GroupEdge") { type ->
                type.dataFetcher(
                    "node",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<GroupEdgeDTO>()
                        edge?.node
                    },
                )
                type.dataFetcher(
                    "cursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<GroupEdgeDTO>()
                        edge?.cursor
                    },
                )
            }
            .type("RoleEdge") { type ->
                type.dataFetcher(
                    "node",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<RoleEdgeDTO>()
                        edge?.node
                    },
                )
                type.dataFetcher(
                    "cursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<RoleEdgeDTO>()
                        edge?.cursor
                    },
                )
            }
            .type("PermissionEdge") { type ->
                type.dataFetcher(
                    "node",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<PermissionEdgeDTO>()
                        edge?.node
                    },
                )
                type.dataFetcher(
                    "cursor",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val edge = env.getSource<PermissionEdgeDTO>()
                        edge?.cursor
                    },
                )
            }
            .type("UserConnection") { type ->
                type.dataFetcher(
                    "edges",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<UserConnectionDTO>()
                        connection?.edges ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "nodes",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<UserConnectionDTO>()
                        connection?.nodes ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "pageInfo",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<UserConnectionDTO>()
                        connection?.pageInfo
                    },
                )
            }
            .type("GroupConnection") { type ->
                type.dataFetcher(
                    "edges",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<GroupConnectionDTO>()
                        connection?.edges ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "nodes",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<GroupConnectionDTO>()
                        connection?.nodes ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "pageInfo",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<GroupConnectionDTO>()
                        connection?.pageInfo
                    },
                )
            }
            .type("RoleConnection") { type ->
                type.dataFetcher(
                    "edges",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<RoleConnectionDTO>()
                        connection?.edges ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "nodes",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<RoleConnectionDTO>()
                        connection?.nodes ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "pageInfo",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<RoleConnectionDTO>()
                        connection?.pageInfo
                    },
                )
            }
            .type("PermissionConnection") { type ->
                type.dataFetcher(
                    "edges",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<PermissionConnectionDTO>()
                        connection?.edges ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "nodes",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<PermissionConnectionDTO>()
                        connection?.nodes ?: emptyList()
                    },
                )
                type.dataFetcher(
                    "pageInfo",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<PermissionConnectionDTO>()
                        connection?.pageInfo
                    },
                )
            }
    }
}
