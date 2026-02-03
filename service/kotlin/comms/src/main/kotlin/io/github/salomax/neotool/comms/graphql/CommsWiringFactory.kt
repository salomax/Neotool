package io.github.salomax.neotool.comms.graphql

import graphql.scalars.ExtendedScalars
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.AuthenticatedGraphQLWiringFactory
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils.createValidatedDataFetcher
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.github.salomax.neotool.comms.domain.rbac.CommsPermissions
import io.github.salomax.neotool.comms.graphql.resolvers.CommsEmailResolver
import io.github.salomax.neotool.comms.graphql.resolvers.CommsTemplateResolver
import jakarta.inject.Singleton

@Singleton
class CommsWiringFactory(
    private val emailResolver: CommsEmailResolver,
    private val templateResolver: CommsTemplateResolver,
    requestPrincipalProvider: RequestPrincipalProvider,
    authorizationChecker: AuthorizationChecker,
) : AuthenticatedGraphQLWiringFactory(requestPrincipalProvider, authorizationChecker) {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "listTemplates",
                createValidatedDataFetcher { env ->
                    env.withPermission(CommsPermissions.COMMS_TEMPLATE_VIEW) {
                        val channel = GraphQLArgumentUtils.getRequiredString(env, "channel")
                        templateResolver.listTemplates(channel)
                    }
                },
            )
            .dataFetcher(
                "getTemplate",
                createValidatedDataFetcher { env ->
                    env.withPermission(CommsPermissions.COMMS_TEMPLATE_VIEW) {
                        val key = GraphQLArgumentUtils.getRequiredString(env, "key")
                        val channel = GraphQLArgumentUtils.getRequiredString(env, "channel")
                        templateResolver.getTemplate(key, channel)
                    }
                },
            )
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher(
                "requestEmailSend",
                createValidatedDataFetcher { env ->
                    env.withPermission(CommsPermissions.COMMS_EMAIL_SEND) {
                        val input = GraphQLArgumentUtils.getRequiredMap(env, "input")
                        emailResolver.requestEmailSend(input)
                    }
                },
            )
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder.scalar(ExtendedScalars.Json)
    }
}
