package io.github.salomax.neotool.example.graphql.resolvers

import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.graphql.dto.ProductInputDTO
import io.github.salomax.neotool.example.service.ProductService
import io.github.salomax.neotool.graphql.GenericCrudResolver
import io.github.salomax.neotool.graphql.CrudService
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.*

/**
 * Product resolver using the generic enhanced CRUD pattern with automatic payload handling
 */
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    
    override val validator: Validator = validator
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)
    
    override fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return ProductInputDTO(
            name = extractField(input, "name"),
            sku = extractField(input, "sku"),
            priceCents = extractField(input, "priceCents", 0L),
            stock = extractField(input, "stock", 0)
        )
    }
    
    override fun mapToEntity(dto: ProductInputDTO, id: UUID?): Product {
        return Product(
            id = id,
            name = dto.name,
            sku = dto.sku,
            priceCents = dto.priceCents,
            stock = dto.stock
        )
    }
    
    /**
     * Extract field with type safety and default values
     */
    private fun <T> extractField(input: Map<String, Any?>, name: String, defaultValue: T? = null): T {
        @Suppress("UNCHECKED_CAST")
        return input[name] as? T ?: defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
    }
}

/**
 * Adapter to make ProductService compatible with CrudService interface
 */
class ProductCrudService(private val productService: ProductService) : CrudService<Product, UUID> {
    
    override fun create(entity: Product): Product = productService.create(entity)
    
    override fun update(entity: Product): Product? = productService.update(entity)
    
    override fun delete(id: UUID): Boolean {
        productService.delete(id)
        return true
    }
    
    override fun getById(id: UUID): Product? = productService.get(id)
    
    override fun list(): List<Product> = productService.list()
}
