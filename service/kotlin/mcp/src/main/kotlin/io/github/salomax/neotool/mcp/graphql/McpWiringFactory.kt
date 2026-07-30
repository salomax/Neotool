package io.github.salomax.neotool.mcp.graphql

import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getArgumentOrDefault
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.getRequiredString
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import jakarta.inject.Singleton

@Singleton
class McpWiringFactory(
    private val resolver: McpConnectionResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type.dataFetcher(
            "recentActivity",
            createValidatedDataFetcher(
                requiredArgs = listOf("userId", "clientId"),
                operation = "mcp.recentActivity",
            ) { env ->
                // This path is intentionally service-token-only and does not use AuthorizationChecker.
                // The resolver enforces delegated user/account matching directly at the trust boundary.
                val principal = env.principal()
                resolver.recentActivity(
                    principal = principal,
                    userId = getRequiredString(env, "userId"),
                    clientId = getRequiredString(env, "clientId"),
                    first = getArgumentOrDefault(env, "first", 20),
                )
            },
        )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type.dataFetcher(
            "revokeConnection",
            createValidatedDataFetcher(
                requiredArgs = listOf("userId", "clientId", "accountId"),
                operation = "mcp.revokeConnection",
            ) { env ->
                // This path is intentionally service-token-only and does not use AuthorizationChecker.
                // The resolver enforces delegated user/account matching directly at the trust boundary.
                val principal = env.principal()
                resolver.revokeConnection(
                    principal = principal,
                    userId = getRequiredString(env, "userId"),
                    clientId = getRequiredString(env, "clientId"),
                    accountId = getRequiredString(env, "accountId"),
                )
            },
        )
}
