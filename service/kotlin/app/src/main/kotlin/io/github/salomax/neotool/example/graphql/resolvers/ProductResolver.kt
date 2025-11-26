package io.github.salomax.neotool.example.graphql.resolvers

import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.graphql.dto.ProductInputDTO
import io.github.salomax.neotool.example.graphql.mapper.ProductGraphQLMapper
import io.github.salomax.neotool.example.service.ProductService
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID

/**
 * Product resolver using the generic enhanced CRUD pattern with automatic payload handling.
 * Delegates mapping logic to ProductGraphQLMapper for separation of concerns.
 */
@Singleton
class ProductResolver(
    private val productService: ProductService,
    validator: Validator,
    private val mapper: ProductGraphQLMapper,
) : GenericCrudResolver<Product, ProductInputDTO, UUID>() {
    override val validator: Validator = validator
    override val service: CrudService<Product, UUID> = ProductCrudService(productService)

    override fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return mapper.mapToInputDTO(input)
    }

    override fun mapToEntity(
        dto: ProductInputDTO,
        id: UUID?,
    ): Product {
        return mapper.mapToEntity(dto, id)
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
