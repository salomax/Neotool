package io.github.salomax.neotool.graphql

import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.util.*

/**
 * Enhanced GraphQL resolver following big tech best practices
 */
abstract class EnhancedCrudResolver<Entity, InputDTO, ID> : GraphQLResolver<Entity> {

    protected abstract val validator: Validator
    protected abstract val service: CrudService<Entity, ID>
    
    /**
     * Default resolve implementation - can be overridden by subclasses
     */
    override fun resolve(environment: DataFetchingEnvironment): Entity? {
        return null
    }
    
    /**
     * Create operation with proper error handling following Relay pattern
     */
    fun create(input: Map<String, Any?>): Any {
        return try {
            val dto = mapToInputDTO(input)
            validateInput(dto)
            val entity = mapToEntity(dto)
            val created = service.create(entity)
            createSuccessPayload(created)
        } catch (e: ConstraintViolationException) {
            createErrorPayload(e)
        } catch (e: Exception) {
            createErrorPayload(e)
        }
    }
    
    /**
     * Update operation with proper error handling
     */
    fun update(id: String, input: Map<String, Any?>): Any {
        return try {
            val dto = mapToInputDTO(input)
            validateInput(dto)
            val entity = mapToEntity(dto, parseId(id))
            val updated = service.update(entity)
            createSuccessPayload(updated)
        } catch (e: ConstraintViolationException) {
            createErrorPayload(e)
        } catch (e: Exception) {
            createErrorPayload(e)
        }
    }
    
    /**
     * Delete operation with proper error handling
     */
    fun delete(id: String): Any {
        return try {
            val deleted = service.delete(parseId(id))
            createDeleteSuccessPayload(deleted)
        } catch (e: Exception) {
            createErrorPayload(e)
        }
    }
    
    /**
     * Get by ID with graceful null handling
     */
    fun getById(id: String): Entity? {
        return try {
            service.getById(parseId(id))
        } catch (e: IllegalArgumentException) {
            // Invalid ID format - return null instead of error (GraphQL best practice)
            null
        }
    }
    
    /**
     * List operation with pagination support
     */
    fun list(first: Int? = null, after: String? = null, last: Int? = null, before: String? = null): Any {
        // TODO: Implement cursor-based pagination
        val entities = service.list()
        return createConnection(entities, first, after, last, before)
    }
    
    /**
     * Validate input DTO using Bean Validation
     */
    protected fun validateInput(dto: InputDTO) {
        val violations = validator.validate(dto)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
    }
    
    /**
     * Parse string ID to proper ID type with graceful error handling
     */
    protected open fun parseId(id: String): ID {
        @Suppress("UNCHECKED_CAST")
        return try {
            UUID.fromString(id) as ID
        } catch (e: IllegalArgumentException) {
            // For UUID types, throw error for invalid format
            throw IllegalArgumentException("Invalid UUID format: '$id'")
        }
    }
    
    /**
     * Map GraphQL input to DTO - must be implemented by concrete resolvers
     */
    protected abstract fun mapToInputDTO(input: Map<String, Any?>): InputDTO
    
    /**
     * Map DTO to Entity - must be implemented by concrete resolvers
     */
    protected abstract fun mapToEntity(dto: InputDTO, id: ID? = null): Entity
    
    /**
     * Create success payload following Relay pattern
     */
    protected abstract fun createSuccessPayload(entity: Entity?): Any
    
    /**
     * Create error payload following Relay pattern
     */
    protected abstract fun createErrorPayload(exception: Exception): Any
    
    /**
     * Create success payload for delete operations
     */
    protected abstract fun createDeleteSuccessPayload(deleted: Boolean): Any
    
    /**
     * Create connection for pagination
     */
    protected abstract fun createConnection(
        entities: List<Entity>, 
        first: Int?, 
        after: String?, 
        last: Int?, 
        before: String?
    ): Any
}
