package io.github.salomax.neotool.graphql

import graphql.schema.DataFetchingEnvironment
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import java.util.*

/**
 * Base interface for all GraphQL resolvers.
 * Ensures consistent error handling and validation across all modules.
 */
interface GraphQLResolver<T> {
    fun resolve(environment: DataFetchingEnvironment): T?
}

/**
 * Abstract base class for CRUD operations resolvers.
 * Provides standard patterns for Create, Read, Update, Delete operations.
 */
abstract class CrudResolver<Entity, InputDTO, ID> : GraphQLResolver<Entity> {

    protected abstract val validator: Validator
    protected abstract val service: CrudService<Entity, ID>
    
    /**
     * Default resolve implementation - can be overridden by subclasses
     */
    override fun resolve(environment: DataFetchingEnvironment): Entity? {
        // Default implementation - subclasses can override for specific behavior
        return null
    }
    
    /**
     * Standard create operation with validation
     */
    fun create(input: Map<String, Any?>): Entity {
        val dto = mapToInputDTO(input)
        validateInput(dto)
        val entity = mapToEntity(dto)
        return service.create(entity)
    }
    
    /**
     * Standard update operation with validation
     */
    fun update(id: String, input: Map<String, Any?>): Entity? {
        val dto = mapToInputDTO(input)
        validateInput(dto)
        val entity = mapToEntity(dto, parseId(id))
        return service.update(entity)
    }
    
    /**
     * Standard delete operation
     */
    fun delete(id: String): Boolean {
        return service.delete(parseId(id))
    }
    
    /**
     * Standard get by ID operation with graceful error handling
     * Following GraphQL best practices: invalid but well-formed inputs should return null, not errors
     */
    open fun getById(id: String): Entity? {
        return try {
            service.getById(parseId(id))
        } catch (e: Exception) {
            // Invalid ID format - return null instead of error (GraphQL best practice)
            null
        }
    }
    
    /**
     * Standard list operation
     */
    fun list(): List<Entity> {
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
     * Parse string ID to proper ID type
     */
    protected open fun parseId(id: String): ID {
        @Suppress("UNCHECKED_CAST")
        return try {
            // Try UUID first (most common)
            UUID.fromString(id) as ID
        } catch (e: IllegalArgumentException) {
            // Fall back to string for non-UUID types
            id as ID
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

/**
 * Service interface for CRUD operations
 */
interface CrudService<Entity, ID> {
    fun create(entity: Entity): Entity
    fun update(entity: Entity): Entity?
    fun delete(id: ID): Boolean
    fun getById(id: ID): Entity?
    fun list(): List<Entity>
}
