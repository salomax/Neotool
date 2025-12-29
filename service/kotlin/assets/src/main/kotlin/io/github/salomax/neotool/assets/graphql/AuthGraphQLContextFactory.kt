package io.github.salomax.neotool.assets.graphql

import graphql.GraphQLContext
import io.micronaut.configuration.graphql.GraphQLRequestBody
import io.micronaut.configuration.graphql.ws.GraphQLWsRequest
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.graphql.GraphQLContextFactory
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

/**
 * Factory for creating GraphQL context with authentication token.
 * Extracts the Bearer token from Authorization header and adds it to the GraphQL context.
 */
@Singleton
class AuthGraphQLContextFactory : GraphQLContextFactory {
    override fun createGraphQLContext(
        request: HttpRequest<*>,
        requestBody: GraphQLRequestBody,
        response: MutableHttpResponse<*>?,
    ): Publisher<GraphQLContext> {
        val context = GraphQLContext.newContext().build()

        // Extract token from Authorization header
        val authHeader = request.headers.get("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            context.put("token", token)
        }

        return Publishers.just(context)
    }

    override fun createGraphQLContext(request: HttpRequest<*>, requestBody: GraphQLWsRequest): Publisher<GraphQLContext> {
        val context = GraphQLContext.newContext().build()

        // Extract token from Authorization header for WebSocket requests
        val authHeader = request.headers.get("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.substring(7)
            context.put("token", token)
        }

        return Publishers.just(context)
    }
}
