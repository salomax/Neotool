package io.github.salomax.neotool.assistant.graphql

import graphql.schema.idl.TypeRuntimeWiring
import io.github.salomax.neotool.assistant.graphql.resolvers.AssistantResolver
import io.github.salomax.neotool.common.graphql.GraphQLArgumentUtils
import io.github.salomax.neotool.common.graphql.GraphQLWiringFactory
import jakarta.inject.Singleton

@Singleton
class AssistantWiringFactory(
    private val assistantResolver: AssistantResolver
) : GraphQLWiringFactory() {
    
    override fun registerQueryResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("conversation") { env ->
                val sessionId = GraphQLArgumentUtils.getRequiredString(env, "sessionId")
                assistantResolver.conversation(sessionId)
            }
    }
    
    override fun registerMutationResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        return type
            .dataFetcher("chat") { env ->
                val input = GraphQLArgumentUtils.getRequiredMap(env, "input")
                assistantResolver.chat(input)
            }
            .dataFetcher("clearConversation") { env ->
                val sessionId = GraphQLArgumentUtils.getRequiredString(env, "sessionId")
                assistantResolver.clearConversation(sessionId)
            }
    }
    
    override fun registerSubscriptionResolvers(type: TypeRuntimeWiring.Builder): TypeRuntimeWiring.Builder {
        // No subscriptions for now
        return type
    }
}

