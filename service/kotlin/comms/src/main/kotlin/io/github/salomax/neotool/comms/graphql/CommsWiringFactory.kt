package io.github.salomax.neotool.comms.graphql

import graphql.scalars.ExtendedScalars
import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import io.github.salomax.neotool.comms.graphql.resolvers.CommsEmailResolver
import jakarta.inject.Singleton

@Singleton
class CommsWiringFactory(
    private val emailResolver: CommsEmailResolver,
) : GraphQLWiringFactory() {
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
    }

    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("requestEmailSend") { env ->
                val input = GraphQLArgumentUtils.getRequiredMap(env, "input")
                emailResolver.requestEmailSend(input)
            }
    }

    override fun registerCustomTypeResolvers(builder: RuntimeWiring.Builder): RuntimeWiring.Builder {
        return builder.scalar(ExtendedScalars.Json)
    }
}
