package io.github.salomax.neotool.graphql

import graphql.schema.DataFetchingEnvironment

/**
 * Base interface for all GraphQL resolvers.
 * 
 * This interface provides a consistent contract for all GraphQL field resolvers.
 * It ensures that all resolvers can be invoked with a [DataFetchingEnvironment]
 * and return nullable results (following GraphQL's nullability semantics).
 * 
 * **Note:** For CRUD operations, prefer [GenericCrudResolver] which provides
 * a complete implementation with payload handling and validation.
 * 
 * **Usage:**
 * ```kotlin
 * class CustomResolver : GraphQLResolver<CustomType> {
 *     override fun resolve(environment: DataFetchingEnvironment): CustomType? {
 *         // Custom resolution logic
 *     }
 * }
 * ```
 */
interface GraphQLResolver<T> {
    fun resolve(environment: DataFetchingEnvironment): T?
}

/**
 * Service interface for CRUD operations.
 * 
 * This interface defines the standard contract for CRUD service implementations.
 * It's used by [GenericCrudResolver] to abstract data access operations.
 * 
 * **Usage:**
 * ```kotlin
 * class ProductCrudService(private val productService: ProductService) : CrudService<Product, UUID> {
 *     override fun create(entity: Product): Product = productService.create(entity)
 *     override fun update(entity: Product): Product? = productService.update(entity)
 *     override fun delete(id: UUID): Boolean = productService.delete(id)
 *     override fun getById(id: UUID): Product? = productService.get(id)
 *     override fun list(): List<Product> = productService.list()
 * }
 * ```
 */
interface CrudService<Entity, ID> {
    fun create(entity: Entity): Entity
    fun update(entity: Entity): Entity?
    fun delete(id: ID): Boolean
    fun getById(id: ID): Entity?
    fun list(): List<Entity>
}
