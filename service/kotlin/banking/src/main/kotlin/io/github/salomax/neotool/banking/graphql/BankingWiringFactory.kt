package io.github.salomax.neotool.banking.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
import io.github.salomax.neotool.banking.graphql.dto.IssuerDTO
import io.github.salomax.neotool.banking.graphql.resolver.IssuerResolver
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import jakarta.inject.Singleton

/**
 * GraphQL wiring factory for the banking subgraph.
 *
 * Query access is protected by permissions (e.g. [BankingPermissions.BANKING_ISSUER_VIEW]).
 * [Issuer.logoAsset] is resolved here as a federation stub pointing to the assets subgraph.
 */
@Singleton
class BankingWiringFactory(
    private val issuerResolver: IssuerResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {

    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder =
        type
            .dataFetcher(
                "issuers",
                createValidatedDataFetcher { env ->
                    env.withPermission(BankingPermissions.BANKING_ISSUER_VIEW) { _ ->
                        issuerResolver.listIssuers()
                    }
                },
            )

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder = type

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder =
        builder
            .type("Issuer") { type ->
                type.dataFetcher(
                    "logoAsset",
                    createValidatedDataFetcher { env ->
                        val issuer = env.getSource<IssuerDTO>()
                        issuer?.logoAsset
                    },
                )
            }
}
