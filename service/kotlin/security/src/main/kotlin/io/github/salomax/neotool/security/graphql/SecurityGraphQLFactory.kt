package io.github.salomax.neotool.security.graphql

import graphql.schema.idl.RuntimeWiring
import graphql.schema.idl.TypeDefinitionRegistry
import io.github.salomax.neotool.graphql.BaseGraphQLFactory
import io.github.salomax.neotool.security.graphql.dto.UserDTO
import io.github.salomax.neotool.security.repo.UserRepository
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton
import java.util.UUID

/**
 * GraphQL factory for the Security service (federated subgraph).
 * 
 * This service owns User entities and authentication operations.
 * It exposes its own GraphQL endpoint that will be composed by Apollo Router.
 * 
 * This follows true GraphQL Federation patterns where each service
 * has its own GraphQLFactory that handles only its own entities.
 */
@Factory
class SecurityGraphQLFactory(
    schemaRegistry: TypeDefinitionRegistry,
    wiringFactory: SecurityWiringFactory,
    private val userRepository: UserRepository
) : BaseGraphQLFactory(
    schemaRegistry = schemaRegistry,
    runtimeWiring = wiringFactory.build(),
    serviceName = "Security"
) {
    
    @Singleton
    fun graphQL(): graphql.GraphQL {
        return buildGraphQL()
    }
    
    /**
     * Fetch an entity by its typename and key fields.
     * 
     * This service handles User entities.
     */
    override fun fetchEntity(typename: String, keyFields: Map<String, Any>): Any? {
        if (typename != "User") {
            logger.debug("Unknown entity type for Security service: $typename")
            return null
        }
        
        val id = extractId(keyFields) ?: return null
        
        return try {
            userRepository.findById(UUID.fromString(id))
                .orElse(null)
                ?.let { 
                    UserDTO(
                        id = it.id.toString(),
                        email = it.email,
                        displayName = it.displayName
                    )
                }
        } catch (e: Exception) {
            logger.debug("Failed to fetch entity for federation: User with id: $id", e)
            null
        }
    }
    
    /**
     * Resolve the GraphQL type name for an entity object.
     * 
     * This service handles User entities.
     */
    override fun resolveEntityType(entity: Any): String? {
        return when (entity) {
            is UserDTO -> "User"
            else -> null
        }
    }
}

