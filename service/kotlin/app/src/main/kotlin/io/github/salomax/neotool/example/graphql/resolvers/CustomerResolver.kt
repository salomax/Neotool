package io.github.salomax.neotool.example.graphql.resolvers

import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.graphql.dto.CustomerInputDTO
import io.github.salomax.neotool.example.graphql.mapper.CustomerGraphQLMapper
import io.github.salomax.neotool.example.service.CustomerService
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID

/**
 * Customer resolver using the generic enhanced CRUD pattern with automatic payload handling.
 * Delegates mapping logic to CustomerGraphQLMapper for separation of concerns.
 */
@Singleton
class CustomerResolver(
    customerService: CustomerService,
    override val validator: Validator,
) : GenericCrudResolver<Customer, CustomerInputDTO, UUID>() {
    private val customerCrudService = CustomerCrudService(customerService)
    override val service: CrudService<Customer, UUID> = customerCrudService

    // Create mapper with function to fetch existing entities for version management
    private val mapper = CustomerGraphQLMapper { id -> customerCrudService.getById(id) }

    override fun mapToInputDTO(input: Map<String, Any?>): CustomerInputDTO {
        return mapper.mapToInputDTO(input)
    }

    override fun mapToEntity(
        dto: CustomerInputDTO,
        id: UUID?,
    ): Customer {
        return mapper.mapToEntity(dto, id)
    }
}

/**
 * Adapter to make CustomerService compatible with CrudService interface
 */
class CustomerCrudService(private val customerService: CustomerService) : CrudService<Customer, UUID> {
    override fun create(entity: Customer): Customer = customerService.create(entity)

    override fun update(entity: Customer): Customer? = customerService.update(entity)

    override fun delete(id: UUID): Boolean {
        customerService.delete(id)
        return true
    }

    override fun getById(id: UUID): Customer? = customerService.get(id)

    override fun list(): List<Customer> = customerService.list()
}
