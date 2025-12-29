package io.github.salomax.neotool.common.graphql

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.security.service.AuthorizationChecker
import io.github.salomax.neotool.security.service.RequestPrincipal
import io.github.salomax.neotool.security.service.RequestPrincipalProvider
import io.github.salomax.neotool.security.service.exception.AuthenticationRequiredException
import io.github.salomax.neotool.security.service.exception.AuthorizationDeniedException

/**
 * Base class for GraphQL wiring factories that require authentication and authorization.
 * This class enforces that permission checking dependencies are always available.
 *
 * Use this instead of [GraphQLWiringFactory] when your module requires permission checks.
 *
 * Benefits:
 * - Type-safe: Dependencies are required in constructor, no nullable properties
 * - Clean API: Extension functions for `withPermission()` and `principal()` on DataFetchingEnvironment
 * - Separation of concerns: All auth code is contained in this class, not in base class
 * - Enterprise-grade: Explicit contracts, easy to test and mock
 *
 * Example usage:
 * ```kotlin
 * @Singleton
 * class AssetWiringFactory(
 *     private val queryResolver: AssetQueryResolver,
 *     requestPrincipalProvider: RequestPrincipalProvider,
 *     authorizationChecker: AuthorizationChecker,
 *     resolverRegistry: GraphQLResolverRegistry,
 * ) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
 *
 *     override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder) = type
 *         .dataFetcher("asset", createValidatedDataFetcher { env ->
 *             env.withPermission(AssetPermissions.ASSETS_ASSET_VIEW) { principal ->
 *                 val id = getRequiredString(env, "id")
 *                 queryResolver.asset(id, principal.userId.toString())
 *             }
 *         })
 * }
 * ```
 */
abstract class AuthenticatedGraphQLWiringFactory(
    protected val requestPrincipalProvider: RequestPrincipalProvider,
    protected val authorizationChecker: AuthorizationChecker,
) : GraphQLWiringFactory() {

    /**
     * Extension function for permission checking that passes the validated principal to the block.
     * Uses the injected requestPrincipalProvider and authorizationChecker.
     *
     * This ensures the same principal instance is used for both permission checking and resolver
     * execution, preventing potential drift if RequestPrincipalProvider ever becomes contextual
     * (e.g., rotating tokens mid-request).
     *
     * Even if your resolver block doesn't need the principal, use this function and ignore the parameter
     * to guarantee consistency.
     *
     * Exceptions thrown by this method (AuthenticationRequiredException and AuthorizationDeniedException)
     * are automatically converted to user-friendly GraphQL error messages by SecurityGraphQLExceptionHandler:
     * - AuthenticationRequiredException → "Authentication required"
     * - AuthorizationDeniedException → "Permission denied: <action>"
     *
     * These errors are returned in the GraphQL response's errors array, and no stack traces
     * or sensitive information are exposed to the client.
     *
     * @param action The permission/action to check (e.g., "assets:asset:view")
     * @param block The block to execute if authorization succeeds, receiving the validated principal
     * @return The result of executing the block
     * @throws AuthenticationRequiredException if no valid token is present (converted to GraphQL error)
     * @throws AuthorizationDeniedException if permission is denied (converted to GraphQL error)
     */
    protected fun <T> DataFetchingEnvironment.withPermission(
        action: String,
        block: (RequestPrincipal) -> T,
    ): T {
        return GraphQLPermissionHelper.withPermissionAndPrincipal(
            this,
            action,
            requestPrincipalProvider,
            authorizationChecker,
            block,
        )
    }

    /**
     * Helper function to enforce permission checks with principal (full version with explicit dependencies).
     * Use this if you need to use different providers/checkers for specific calls.
     *
     * @param env The GraphQL DataFetchingEnvironment
     * @param action The permission/action to check (e.g., "assets:asset:view")
     * @param requestPrincipalProvider The provider to extract principal from GraphQL context
     * @param authorizationChecker The checker to validate permissions
     * @param block The block to execute if authorization succeeds, receiving the validated principal
     * @return The result of executing the block
     * @throws AuthenticationRequiredException if no valid token is present (converted to GraphQL error)
     * @throws AuthorizationDeniedException if permission is denied (converted to GraphQL error)
     */
    protected fun <T> withPermissionAndPrincipal(
        env: DataFetchingEnvironment,
        action: String,
        requestPrincipalProvider: RequestPrincipalProvider,
        authorizationChecker: AuthorizationChecker,
        block: (RequestPrincipal) -> T,
    ): T {
        return GraphQLPermissionHelper.withPermissionAndPrincipal(
            env,
            action,
            requestPrincipalProvider,
            authorizationChecker,
            block,
        )
    }

    /**
     * Extension function to get the current principal from GraphQL context.
     * Caches the principal in the context to avoid revalidation.
     *
     * @return The authenticated request principal
     * @throws AuthenticationRequiredException if no valid token is present
     */
    protected fun DataFetchingEnvironment.principal(): RequestPrincipal {
        return requestPrincipalProvider.fromGraphQl(this)
    }
}

