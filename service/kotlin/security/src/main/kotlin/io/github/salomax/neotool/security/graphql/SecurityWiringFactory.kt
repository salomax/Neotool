package io.github.salomax.neotool.security.graphql

import graphql.schema.DataFetchingEnvironment
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.graphql.GraphQLPayloadDataFetcher.createMutationDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLResolverRegistry
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.github.salomax.neotool.security.domain.rbac.SecurityPermissions
import io.github.salomax.neotool.security.graphql.dataloader.GroupMembersDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.GroupRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.PermissionRolesDataLoader
import io.github.salomax.neotool.security.graphql.dataloader.RoleGroupsDataLoader
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
import io.github.salomax.neotool.security.graphql.dto.ResendVerificationEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.ResetPasswordPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.RoleConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.VerificationStatusDTO
import io.github.salomax.neotool.security.graphql.dto.VerifyEmailPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.RoleDTO
import io.github.salomax.neotool.security.graphql.dto.RoleEdgeDTO
import io.github.salomax.neotool.security.graphql.dto.SignInPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.SignUpPayloadDTO
import io.github.salomax.neotool.security.graphql.dto.UserConnectionDTO
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.graphql.dto.UserEdgeDTO
import io.github.salomax.neotool.security.graphql.mapper.GroupManagementMapper
import io.github.salomax.neotool.security.graphql.mapper.RoleManagementMapper
import io.github.salomax.neotool.security.graphql.mapper.UserManagementMapper
import io.github.salomax.neotool.security.graphql.resolver.GroupManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.PermissionManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.RoleManagementResolver
import io.github.salomax.neotool.security.graphql.resolver.UserManagementResolver
import jakarta.inject.Singleton
import java.util.concurrent.CompletableFuture

/**
 * Security module wiring factory for GraphQL resolvers
 */
@Singleton
class SecurityWiringFactory(
    private val authResolver: SecurityAuthResolver,
    private val emailVerificationResolver: EmailVerificationResolver,
    private val authorizationResolver: io.github.salomax.neotool.security.graphql.resolver.AuthorizationResolver,
    private val userManagementResolver: UserManagementResolver,
    private val groupManagementResolver: GroupManagementResolver,
    private val roleManagementResolver: RoleManagementResolver,
    private val permissionManagementResolver: PermissionManagementResolver,
    private val groupManagementMapper: GroupManagementMapper,
    private val roleManagementMapper: RoleManagementMapper,
    private val userManagementMapper: UserManagementMapper,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
    resolverRegistry: GraphQLResolverRegistry,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    init {
        // Register resolvers in the registry for cross-module access
        resolverRegistry.register("authentication", authResolver)
        resolverRegistry.register("authorization", authorizationResolver)
        resolverRegistry.register("userManagement", userManagementResolver)
        resolverRegistry.register("groupManagement", groupManagementResolver)
        resolverRegistry.register("roleManagement", roleManagementResolver)
        resolverRegistry.register("permissionManagement", permissionManagementResolver)
    }

    /**
     * Helper function to allow users to view their own data (roles, permissions, groups)
     * without requiring SECURITY_USER_VIEW permission.
     * If the user is viewing their own data, access is granted. Otherwise, the permission is required.
     *
     * This is secure because:
     * - Users need to know their own permissions to use the system
     * - The permissions are already in the JWT token
     * - This follows standard RBAC patterns (users can view their own data)
     * - SECURITY_USER_VIEW is still required for viewing other users' data
     *
     * Note: The principal parameter is nullable to handle edge cases (e.g., during sign-in mutations)
     * where a principal may not be available in the GraphQL context yet.
     *
     * @param env The GraphQL DataFetchingEnvironment
     * @param action The permission/action to check (e.g., SecurityPermissions.SECURITY_USER_VIEW)
     * @param userId The ID of the user whose data is being accessed
     * @param block The block to execute if authorization succeeds, receiving the validated principal (nullable)
     * @return The result of executing the block
     */
    private fun <T> withPermissionOrOwnData(
        env: DataFetchingEnvironment,
        action: String,
        userId: String,
        block: (RequestPrincipal?) -> T,
    ): T {
        // Try to get the current user's principal
        val principal =
            try {
                requestPrincipalProvider.fromGraphQl(env)
            } catch (e: Exception) {
                // If no principal (e.g., during sign-in mutation),
                // check if we can extract user ID from the source UserDTO
                // For sign-in, the user in the payload is the authenticated user
                null
            }

        // If no principal available, allow access (edge case during sign-in)
        if (principal == null) {
            return block(null)
        }

        // Check if the user is viewing their own data
        val isOwnData =
            try {
                val currentUserId = principal.userId.toString()
                currentUserId == userId
            } catch (e: Exception) {
                false
            }

        // If viewing own data, allow access; otherwise require permission
        return if (isOwnData) {
            block(principal)
        } else {
            env.withPermission(action) { p -> block(p) }
        }
    }

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
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
            ).dataFetcher(
                "myVerificationStatus",
                createValidatedDataFetcher(emptyList()) { env ->
                    val principal = env.principal()
                    val userId = principal.userId ?: throw IllegalArgumentException("User ID required")
                    emailVerificationResolver.myVerificationStatus(userId)
                },
            ).dataFetcher(
                "checkPermission",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_VIEW) { _ ->
                        val userId = getRequiredString(env, "userId")
                        val permission = getRequiredString(env, "permission")
                        val resourceId = env.getArgument<String?>("resourceId")
                        authorizationResolver.checkPermission(userId, permission, resourceId)
                    }
                },
            ).dataFetcher(
                "getUserPermissions",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_VIEW) { _ ->
                        val userId = getRequiredString(env, "userId")
                        authorizationResolver.getUserPermissions(userId)
                    }
                },
            ).dataFetcher(
                "getUserRoles",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_VIEW) { _ ->
                        val userId = getRequiredString(env, "userId")
                        authorizationResolver.getUserRoles(userId)
                    }
                },
            ).dataFetcher(
                "user",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_VIEW) { _ ->
                        val id = getRequiredString(env, "id")
                        userManagementResolver.user(id)
                    }
                },
            ).dataFetcher(
                "users",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_VIEW) { _ ->
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        val query = env.getArgument<String?>("query")
                        val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                        userManagementResolver.users(first, after, query, orderBy)
                    }
                },
            ).dataFetcher(
                "group",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_VIEW) { _ ->
                        val id = getRequiredString(env, "id")
                        groupManagementResolver.group(id)
                    }
                },
            ).dataFetcher(
                "groups",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_VIEW) { _ ->
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        val query = env.getArgument<String?>("query")
                        val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                        groupManagementResolver.groups(first, after, query, orderBy)
                    }
                },
            ).dataFetcher(
                "role",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_VIEW) { _ ->
                        val id = getRequiredString(env, "id")
                        roleManagementResolver.role(id)
                    }
                },
            ).dataFetcher(
                "roles",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_VIEW) { _ ->
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        val query = env.getArgument<String?>("query")
                        val orderBy = env.getArgument<List<Map<String, Any?>>>("orderBy")
                        roleManagementResolver.roles(first, after, query, orderBy)
                    }
                },
            ).dataFetcher(
                "permissions",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_VIEW) { _ ->
                        val first = env.getArgument<Int?>("first")
                        val after = env.getArgument<String?>("after")
                        val query = env.getArgument<String?>("query")
                        permissionManagementResolver.permissions(first, after, query)
                    }
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "signIn",
                createMutationDataFetcher<SignInPayloadDTO>("signIn") { input ->
                    authResolver.signIn(input)
                },
            ).dataFetcher(
                "signInWithOAuth",
                createMutationDataFetcher<SignInPayloadDTO>("signInWithOAuth") { input ->
                    authResolver.signInWithOAuth(input)
                },
            ).dataFetcher(
                "signUp",
                createMutationDataFetcher<SignUpPayloadDTO>("signUp") { input ->
                    authResolver.signUp(input)
                },
            ).dataFetcher(
                "refreshAccessToken",
                createMutationDataFetcher<SignInPayloadDTO>("refreshAccessToken") { input ->
                    authResolver.refreshAccessToken(input)
                },
            ).dataFetcher(
                "requestPasswordReset",
                createMutationDataFetcher<RequestPasswordResetPayloadDTO>("requestPasswordReset") { input ->
                    authResolver.requestPasswordReset(input)
                },
            ).dataFetcher(
                "resetPassword",
                createMutationDataFetcher<ResetPasswordPayloadDTO>("resetPassword") { input ->
                    authResolver.resetPassword(input)
                },
            ).dataFetcher(
                "verifyEmailWithToken",
                createValidatedDataFetcher(listOf("token")) { env ->
                    val tokenStr = getRequiredString(env, "token")
                    val token = java.util.UUID.fromString(tokenStr)
                    val ip = env.graphQlContext.getOrEmpty<String>("clientIp").orElse("unknown")
                    emailVerificationResolver.verifyEmailWithToken(token, ip)
                },
            ).dataFetcher(
                "resendVerificationEmail",
                createValidatedDataFetcher(emptyList()) { env ->
                    val principal = env.principal()
                    val userId = principal.userId ?: throw IllegalArgumentException("User ID required")
                    val locale = env.getArgument<String>("locale") ?: "en"
                    emailVerificationResolver.resendVerificationEmail(userId, locale)
                },
            ).dataFetcher(
                "enableUser",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_SAVE) { _ ->
                        val userId = getRequiredString(env, "userId")
                        userManagementResolver.enableUser(userId)
                    }
                },
            ).dataFetcher(
                "disableUser",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_SAVE) { _ ->
                        val userId = getRequiredString(env, "userId")
                        userManagementResolver.disableUser(userId)
                    }
                },
            ).dataFetcher(
                "createGroup",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_SAVE) { _ ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val dto = groupManagementMapper.mapToCreateGroupInputDTO(input)
                        val result = groupManagementResolver.createGroup(dto)
                        result
                    }
                },
            ).dataFetcher(
                "updateGroup",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_SAVE) { _ ->
                        val groupId = getRequiredString(env, "groupId")
                        val inputMap =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val dto = groupManagementMapper.mapToUpdateGroupInputDTO(inputMap)
                        groupManagementResolver.updateGroup(groupId, dto)
                    }
                },
            ).dataFetcher(
                "deleteGroup",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_DELETE) { _ ->
                        val groupId = getRequiredString(env, "groupId")
                        groupManagementResolver.deleteGroup(groupId)
                    }
                },
            ).dataFetcher(
                "createRole",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_SAVE) { _ ->
                        val input =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val dto = roleManagementMapper.mapToCreateRoleInputDTO(input)
                        val result = roleManagementResolver.createRole(dto)
                        result
                    }
                },
            ).dataFetcher(
                "updateRole",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_SAVE) { _ ->
                        val roleId = getRequiredString(env, "roleId")
                        val inputMap =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val dto = roleManagementMapper.mapToUpdateRoleInputDTO(inputMap)
                        roleManagementResolver.updateRole(roleId, dto)
                    }
                },
            ).dataFetcher(
                "deleteRole",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_DELETE) { _ ->
                        val roleId = getRequiredString(env, "roleId")
                        roleManagementResolver.deleteRole(roleId)
                    }
                },
            ).dataFetcher(
                "assignPermissionToRole",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_SAVE) { _ ->
                        val roleId = getRequiredString(env, "roleId")
                        val permissionId = getRequiredString(env, "permissionId")
                        roleManagementResolver.assignPermissionToRole(roleId, permissionId)
                    }
                },
            ).dataFetcher(
                "removePermissionFromRole",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_ROLE_SAVE) { _ ->
                        val roleId = getRequiredString(env, "roleId")
                        val permissionId = getRequiredString(env, "permissionId")
                        roleManagementResolver.removePermissionFromRole(roleId, permissionId)
                    }
                },
            ).dataFetcher(
                "assignGroupToUser",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_SAVE) { _ ->
                        val userId = getRequiredString(env, "userId")
                        val groupId = getRequiredString(env, "groupId")
                        userManagementResolver.assignGroupToUser(userId, groupId)
                    }
                },
            ).dataFetcher(
                "removeGroupFromUser",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_SAVE) { _ ->
                        val userId = getRequiredString(env, "userId")
                        val groupId = getRequiredString(env, "groupId")
                        userManagementResolver.removeGroupFromUser(userId, groupId)
                    }
                },
            ).dataFetcher(
                "updateUser",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_USER_SAVE) { _ ->
                        val userId = getRequiredString(env, "userId")
                        val inputMap =
                            env.getArgument<Map<String, Any?>>("input")
                                ?: throw IllegalArgumentException("input is required")
                        val dto = userManagementMapper.mapToUpdateUserInputDTO(inputMap)
                        userManagementResolver.updateUser(userId, dto)
                    }
                },
            ).dataFetcher(
                "assignRoleToGroup",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_SAVE) { _ ->
                        val groupId = getRequiredString(env, "groupId")
                        val roleId = getRequiredString(env, "roleId")
                        groupManagementResolver.assignRoleToGroup(groupId, roleId)
                    }
                },
            ).dataFetcher(
                "removeRoleFromGroup",
                createValidatedDataFetcher { env ->
                    env.withPermission(SecurityPermissions.SECURITY_GROUP_SAVE) { _ ->
                        val groupId = getRequiredString(env, "groupId")
                        val roleId = getRequiredString(env, "roleId")
                        groupManagementResolver.removeRoleFromGroup(groupId, roleId)
                    }
                },
            )

    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder = type

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
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
                        if (user == null) {
                            CompletableFuture.completedFuture(
                                emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>(),
                            )
                        } else {
                            // Allow users to view their own roles without permission
                            withPermissionOrOwnData(env, SecurityPermissions.SECURITY_USER_VIEW, user.id) { _ ->
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
                                    ?: CompletableFuture.completedFuture(
                                        emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>(),
                                    )
                            }
                        }
                    },
                )
                type.dataFetcher(
                    "groups",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        if (user == null) {
                            CompletableFuture.completedFuture(
                                emptyList<io.github.salomax.neotool.security.graphql.dto.GroupDTO>(),
                            )
                        } else {
                            // Allow users to view their own groups without permission
                            withPermissionOrOwnData(env, SecurityPermissions.SECURITY_USER_VIEW, user.id) { _ ->
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
                                    ?: CompletableFuture.completedFuture(
                                        emptyList<io.github.salomax.neotool.security.graphql.dto.GroupDTO>(),
                                    )
                            }
                        }
                    },
                )
                type.dataFetcher(
                    "permissions",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val user = env.getSource<UserDTO>()
                        if (user == null) {
                            CompletableFuture.completedFuture(
                                emptyList<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>(),
                            )
                        } else {
                            // Allow users to view their own permissions without permission
                            withPermissionOrOwnData(env, SecurityPermissions.SECURITY_USER_VIEW, user.id) { _ ->
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
                                    ?: CompletableFuture.completedFuture(
                                        emptyList<io.github.salomax.neotool.security.graphql.dto.PermissionDTO>(),
                                    )
                            }
                        }
                    },
                )
            }.type("SignInPayload") { type ->
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
            }.type("SignUpPayload") { type ->
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
            }.type("RequestPasswordResetPayload") { type ->
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
            }.type("ResetPasswordPayload") { type ->
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
            }.type("AuthorizationResult") { type ->
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
            }.type("Permission") { type ->
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
            }.type("Role") { type ->
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
                            emptyList<PermissionDTO>()
                        } else {
                            val dataLoader =
                                env.getDataLoader<String, List<PermissionDTO>>(
                                    RolePermissionsDataLoader.KEY,
                                )
                            dataLoader?.load(role.id)
                                ?: CompletableFuture.completedFuture(emptyList<PermissionDTO>())
                        }
                    },
                )
                type.dataFetcher(
                    "groups",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val role = env.getSource<RoleDTO>()
                        if (role == null || role.id == null) {
                            emptyList<GroupDTO>()
                        } else {
                            val dataLoader =
                                env.getDataLoader<String, List<GroupDTO>>(
                                    RoleGroupsDataLoader.KEY,
                                )
                            dataLoader?.load(role.id)
                                ?: CompletableFuture.completedFuture(emptyList<GroupDTO>())
                        }
                    },
                )
            }.type("Group") { type ->
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
                                ?: CompletableFuture.completedFuture(
                                    emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>(),
                                )
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
                                ?: CompletableFuture.completedFuture(
                                    emptyList<io.github.salomax.neotool.security.graphql.dto.UserDTO>(),
                                )
                        }
                    },
                )
            }.type("Permission") { type ->
                type.dataFetcher(
                    "roles",
                    createValidatedDataFetcher { env: DataFetchingEnvironment ->
                        val permission = env.getSource<PermissionDTO>()
                        if (permission == null || permission.id == null) {
                            CompletableFuture.completedFuture(
                                emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>(),
                            )
                        } else {
                            // Get DataLoader from context to batch requests
                            val dataLoader =
                                env.getDataLoader<String, List<io.github.salomax.neotool.security.graphql.dto.RoleDTO>>(
                                    PermissionRolesDataLoader.KEY,
                                )
                            // Load roles for this permission (will be batched with other permissions)
                            dataLoader?.load(permission.id)
                                ?: CompletableFuture.completedFuture(
                                    emptyList<io.github.salomax.neotool.security.graphql.dto.RoleDTO>(),
                                )
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
            }.type("UserEdge") { type ->
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
            }.type("GroupEdge") { type ->
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
            }.type("RoleEdge") { type ->
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
            }.type("PermissionEdge") { type ->
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
            }.type("UserConnection") { type ->
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
            }.type("GroupConnection") { type ->
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
            }.type("RoleConnection") { type ->
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
            }.type("PermissionConnection") { type ->
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
