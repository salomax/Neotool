package io.github.salomax.neotool.graphql

import graphql.schema.DataFetchingEnvironment
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import io.github.salomax.neotool.graphql.payload.GraphQLPayload
import io.github.salomax.neotool.graphql.payload.GraphQLPayloadFactory
import io.micronaut.http.server.exceptions.NotFoundException
import java.util.*

/**
 * Generic CRUD resolver with built-in payload handling
 * This resolver automatically handles success/error payloads for all operations
 */
abstract class GenericCrudResolver<Entity, InputDTO, ID> : GraphQLResolver<Entity> {

    protected abstract val validator: Validator
    protected abstract val service: CrudService<Entity, ID>
    
    /**
     * Default resolve implementation - can be overridden by subclasses
     */
    override fun resolve(environment: DataFetchingEnvironment): Entity? {
        return null
    }
    
    /**
     * Create operation with automatic payload handling
     */
    fun create(input: Map<String, Any?>): GraphQLPayload<Entity> {
        return try {
            val dto = mapToInputDTO(input)
            validateInput(dto)
            val entity = mapToEntity(dto)
            val created = service.create(entity)
            GraphQLPayloadFactory.success(created)
        } catch (e: Exception) {
            GraphQLPayloadFactory.error(e)
        }
    }
    
    /**
     * Update operation with automatic payload handling
     */
    fun update(id: String, input: Map<String, Any?>): GraphQLPayload<Entity> {
        return try {
            val dto = mapToInputDTO(input)
            validateInput(dto)
            val entity = mapToEntity(dto, parseId(id))
            val updated = service.update(entity)
            if (updated != null) {
                GraphQLPayloadFactory.success(updated)
            } else {
                GraphQLPayloadFactory.error("id", "Resource not found", "NOT_FOUND")
            }
        } catch (e: Exception) {
            GraphQLPayloadFactory.error(e)
        }
    }
    
    /**
     * Delete operation with automatic payload handling (internal use)
     */
    fun deleteWithPayload(id: String): GraphQLPayload<Boolean> {
        return try {
            val deleted = service.delete(parseId(id))
            GraphQLPayloadFactory.success(deleted)
        } catch (e: Exception) {
            // Check if it's a "not found" exception by type
            when (e) {
                is NotFoundException -> GraphQLPayloadFactory.success(false)
                is NoSuchElementException -> GraphQLPayloadFactory.success(false)
                else -> GraphQLPayloadFactory.error(e)
            }
        }
    }
    
    /**
     * Delete operation that returns raw data for GraphQL
     */
    fun delete(id: String): Boolean {
        return try {
            val deleted = service.delete(parseId(id))
            deleted
        } catch (e: Exception) {
            // Check if it's a "not found" exception by type
            when (e) {
                is NotFoundException -> false
                is NoSuchElementException -> false
                else -> throw e
            }
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
    fun list(first: Int? = null, after: String? = null, last: Int? = null, before: String? = null): List<Entity> {
        // Simple implementation - return the list directly
        // TODO: Implement proper cursor-based pagination
        return service.list()
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
}