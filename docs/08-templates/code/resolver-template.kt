package io.github.salomax.neotool.{module}.graphql.resolvers

import io.github.salomax.neotool.common.graphql.CrudService
import io.github.salomax.neotool.common.graphql.GenericCrudResolver
import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import io.github.salomax.neotool.{module}.graphql.mapper.{EntityName}GraphQLMapper
import io.github.salomax.neotool.{module}.service.{EntityName}Service
import jakarta.inject.Singleton
import jakarta.validation.Validator
import java.util.UUID

/**
 * GraphQL resolver for {EntityName}.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - {DomainName} with your domain object name (e.g., Product, Customer)
 * - {entityName} with lowercase entity name (e.g., product, customer)
 */
@Singleton
class {EntityName}Resolver(
    private val {entityName}Service: {EntityName}Service,
    validator: Validator,
    private val mapper: {EntityName}GraphQLMapper,
) : GenericCrudResolver<{DomainName}, {EntityName}InputDTO, UUID>() {
    override val validator: Validator = validator
    override val service: CrudService<{DomainName}, UUID> = {EntityName}CrudService({entityName}Service)

    override fun mapToInputDTO(input: Map<String, Any?>): {EntityName}InputDTO {
        return mapper.mapToInputDTO(input)
    }

    override fun mapToEntity(
        dto: {EntityName}InputDTO,
        id: UUID?,
    ): {DomainName} {
        return mapper.mapToEntity(dto, id)
    }
}

/**
 * Adapter to make {EntityName}Service compatible with CrudService interface.
 */
class {EntityName}CrudService(
    private val {entityName}Service: {EntityName}Service,
) : CrudService<{DomainName}, UUID> {
    override fun create(entity: {DomainName}): {DomainName} = {entityName}Service.create(entity)

    override fun update(entity: {DomainName}): {DomainName}? = {entityName}Service.update(entity)

    override fun delete(id: UUID): Boolean {
        {entityName}Service.delete(id)
        return true
    }

    override fun getById(id: UUID): {DomainName}? = {entityName}Service.get(id)

    override fun list(): List<{DomainName}> = {entityName}Service.list()
}
