package io.github.salomax.neotool.common.graphql

import graphql.schema.DataFetchingEnvironment
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.exception.AuthorizationDeniedException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider

/**
 * Helper utilities for GraphQL permission checking.
 * Provides reusable functions for enforcing authorization in GraphQL resolvers.
 */
object GraphQLPermissionHelper {
    /**
     * Helper function to enforce permission checks and pass the validated principal to the block.
     * This variant eliminates duplicate principal extraction by passing the same principal instance
     * that was used for permission checking directly to the resolver block.
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
     * @param env The GraphQL DataFetchingEnvironment
     * @param action The permission/action to check (e.g., "assets:asset:view")
     * @param requestPrincipalProvider The provider to extract principal from GraphQL context
     * @param authorizationChecker The checker to validate permissions
     * @param block The block to execute if authorization succeeds, receiving the validated principal
     * @return The result of executing the block
     * @throws AuthenticationRequiredException if no valid token is present (converted to GraphQL error)
     * @throws AuthorizationDeniedException if permission is denied (converted to GraphQL error)
     */
    fun <T> withPermissionAndPrincipal(
        env: DataFetchingEnvironment,
        action: String,
        requestPrincipalProvider: RequestPrincipalProvider,
        authorizationChecker: AuthorizationChecker,
        block: (RequestPrincipal) -> T,
    ): T {
        val principal = requestPrincipalProvider.fromGraphQl(env)
        authorizationChecker.require(principal, action)
        return block(principal)
    }
}
