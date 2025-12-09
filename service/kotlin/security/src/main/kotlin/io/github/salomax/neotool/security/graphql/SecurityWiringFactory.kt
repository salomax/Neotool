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
import io.github.salomax.neotool.security.domain.rbac.SecurityPermissions
import io.github.salomax.neotool.security.graphql.dataloader.GroupMembersDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.GroupRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.PermissionRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.RolePermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.UserGroupsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.UserPermissionsDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.UserRolesDataLoader
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
import io.github.salomax.neotool.security.service.AuthorizationManager
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
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
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationManager: AuthorizationManager,
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

    /**
     * Helper function to enforce permission checks before executing a data fetcher block.
     * This helper can be reused for any GraphQL operation that requires authorization.
     * It extracts the principal from GraphQL context, validates permissions, and executes
     * the block only if authorization succeeds.
     *
     * Exceptions thrown by this method (AuthenticationRequiredException and AuthorizationDeniedException)
     * are automatically converted to user-friendly GraphQL error messages by SecurityGraphQLExceptionHandler:
     * - AuthenticationRequiredException → "Authentication required"
     * - AuthorizationDeniedException → "Permission denied: <action>"
     *
     * These errors are returned in the GraphQL response's errors array, and no stack traces
     * or sensitive information are exposed to the client.
     *
     * @param env The GraphQL DataFetchingEnvironment
     * @param action The permission/action to check (e.g., SecurityPermissions.SECURITY_USER_VIEW)
     * @param block The block to execute if authorization succeeds
     * @return The result of executing the block
     * @throws AuthenticationRequiredException if no valid token is present (converted to GraphQL error)
     * @throws AuthorizationDeniedException if permission is denied (converted to GraphQL error)
     */
    private fun <T> withPermission(
        env: DataFetchingEnvironment,
        action: String,
        block: () -> T,
    ): T {
        val principal = requestPrincipalProvider.fromGraphQl(env)
        authorizationManager.require(principal, action)
        return block()
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
                "user",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_VIEW) {
                        val id = getRequiredString(env, "id")
                        userManagementResolver.user(id)
                    }
                },
            )
            .dataFetcher(
                "users",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_VIEW) {
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        val query = env.getArgument<String?>("query")
                        val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                        userManagementResolver.users(first, after, query, orderBy)
                    }
                },
            )
            .dataFetcher(
                "group",
                createValidatedDataFetcher { env ->
                    val id = getRequiredString(env, "id")
                    groupManagementResolver.group(id)
                },
            )
            .dataFetcher(
                "groups",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                    groupManagementResolver.groups(first, after, query, orderBy)
                },
            )
            .dataFetcher(
                "role",
                createValidatedDataFetcher { env ->
                    val id = getRequiredString(env, "id")
                    roleManagementResolver.role(id)
                },
            )
            .dataFetcher(
                "roles",
                createValidatedDataFetcher { env ->
                    val first = env.getArgument<Int?>("first")
                    val after = env.getArgument<String?>("after")
                    val query = env.getArgument<String?>("query")
                    val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                    roleManagementResolver.roles(first, after, query, orderBy)
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
                "refreshAccessToken",
                createMutationDataFetcher<SignInPayloadDTO>("refreshAccessToken") { input ->
                    authResolver.refreshAccessToken(input)
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
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        userManagementResolver.enableUser(userId)
                    }
                },
            )
            .dataFetcher(
                "disableUser",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        userManagementResolver.disableUser(userId)
                    }
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
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        val roleId = getRequiredString(env, "roleId")
                        userManagementResolver.assignRoleToUser(userId, roleId)
                    }
                },
            )
            .dataFetcher(
                "removeRoleFromUser",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        val roleId = getRequiredString(env, "roleId")
                        userManagementResolver.removeRoleFromUser(userId, roleId)
                    }
                },
            )
            .dataFetcher(
                "assignGroupToUser",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        val groupId = getRequiredString(env, "groupId")
                        userManagementResolver.assignGroupToUser(userId, groupId)
                    }
                },
            )
            .dataFetcher(
                "removeGroupFromUser",
                createValidatedDataFetcher { env ->
                    withPermission(env, SecurityPermissions.SECURITY_USER_SAVE) {
                        val userId = getRequiredString(env, "userId")
                        val groupId = getRequiredString(env, "groupId")
                        userManagementResolver.removeGroupFromUser(userId, groupId)
                    }
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
                        // Defense in depth: require permission to view user roles
                        withPermission(env, SecurityPermissions.SECURITY_USER_VIEW) {
                            val user = env.getSource<UserDTO>()
                            if (user == null) {
                                emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>()
                            } else {
                                // Get DataLoader from context to batch requests
                                val dataLoader =
                                    env.getDataLoader<
                                        String,
                                        List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>,
                                    >(
                                        UserRolesDataLoader.KEY,
                                    )
                                // Load roles for this user (will be batched with other users)
                                dataLoader?.load(user.id)
                            }
                        }
                    },
                )
                type.dataFetcher(
                    "groups",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        // Defense in depth: require permission to view user groups
                        withPermission(env, SecurityPermissions.SECURITY_USER_VIEW) {
                            val user = env.getSource<UserDTO>()
                            if (user == null) {
                                emptyList<io.github.salomax.neotool.security.graphql.dto.GroupDTO>()
                            } else {
                                // Get DataLoader from context to batch requests
                                val dataLoader =
                                    env.getDataLoader<
                                        String,
                                        List<io.github.salomax.neotool.security.graphql.dto.GroupDTO>,
                                    >(
                                        UserGroupsDataLoader.KEY,
                                    )
                                // Load groups for this user (will be batched with other users)
                                dataLoader?.load(user.id)
                            }
                        }
                    },
                )
                type.dataFetcher(
                    "permissions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        // Defense in depth: require permission to view user permissions
                        withPermission(env, SecurityPermissions.SECURITY_USER_VIEW) {
                            val user = env.getSource<UserDTO>()
                            if (user == null) {
                                emptyList<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>()
                            } else {
                                // Get DataLoader from context to batch requests
                                val dataLoader =
                                    env.getDataLoader<
                                        String,
                                        List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>,
                                    >(
                                        UserPermissionsDataLoader.KEY,
                                    )
                                // Load permissions for this user (will be batched with other users)
                                dataLoader?.load(user.id)
                            }
                        }
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
                        if (role == null || role.id == null) {
                            emptyList<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>()
                        } else {
                            // Get DataLoader from context to batch requests
                            val dataLoader =
                                env.getDataLoader<
                                    String,
                                    List<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>,
                                >(
                                    RolePermissionsDataLoader.KEY,
                                )
                            // Load permissions for this role (will be batched with other roles)
                            dataLoader?.load(role.id)
                        }
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
                        if (group == null) {
                            emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>()
                        } else {
                            // Get DataLoader from context to batch requests
                            val dataLoader =
                                env.getDataLoader<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>(
                                    GroupRolesDataLoader.KEY,
                                )
                            // Load roles for this group (will be batched with other groups)
                            dataLoader?.load(group.id)
                        }
                    },
                )
                type.dataFetcher(
                    "members",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val group = env.getSource<GroupDTO>()
                        if (group == null) {
                            emptyList<io.github.salomax.neotool.security.graphql.dto.UserDTO>()
                        } else {
                            // Get DataLoader from context to batch requests
                            val dataLoader =
                                env.getDataLoader<String, List<io.github.salomax.neotool.security.graphql.dto.UserDTO>>(
                                    GroupMembersDataLoader.KEY,
                                )
                            // Load members for this group (will be batched with other groups)
                            dataLoader?.load(group.id)
                        }
                    },
                )
            }
            .type("Permission") { type ->
                type.dataFetcher(
                    "roles",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        if (permission == null || permission.id == null) {
                            emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>()
                        } else {
                            // Get DataLoader from context to batch requests
                            val dataLoader =
                                env.getDataLoader<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>(
                                    PermissionRolesDataLoader.KEY,
                                )
                            // Load roles for this permission (will be batched with other permissions)
                            dataLoader?.load(permission.id)
                        }
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
                    "pageInfo",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val connection = env.getSource<PermissionConnectionDTO>()
                        connection?.pageInfo
                    },
                )
            }
    }
}
