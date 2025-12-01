package io.github.salomax.neotool.{module}.graphql.mapper

import io.github.salomax.neotool.{module}.domain.{DomainName}
import io.github.salomax.neotool.{module}.graphql.dto.{EntityName}InputDTO
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between GraphQL input maps, DTOs, and domain entities for {EntityName}.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - {DomainName} with your domain object name (e.g., Product, Customer)
 */
@Singleton
class {EntityName}GraphQLMapper {
    /**
     * Extract field with type safety and default values.
     */
    inline fun <reified T> extractField(
        input: Map<String, Any?>,
        name: String,
        defaultValue: T? = null,
    ): T {
        val value = input[name]
        if (value == null) {
            return defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }

        return if (value is T) {
            value
        } else {
            // Handle numeric type conversions
            when {
                T::class == Long::class && value is Number -> {
                    @Suppress("UNCHECKED_CAST")
                    value.toLong() as T
                }
                T::class == Int::class && value is Number -> {
                    @Suppress("UNCHECKED_CAST")
                    value.toInt() as T
                }
                else -> {
                    defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
                }
            }
        }
    }

    /**
     * Map GraphQL input map to {EntityName}InputDTO.
     */
    fun mapToInputDTO(input: Map<String, Any?>): {EntityName}InputDTO {
        return {EntityName}InputDTO(
            name = extractField(input, "name"),
            // Extract all fields here
            // Example: priceCents = extractField(input, "priceCents", 0L),
        )
    }

    /**
     * Map {EntityName}InputDTO to {DomainName} domain entity.
     */
    fun mapToEntity(
        dto: {EntityName}InputDTO,
        id: UUID? = null,
    ): {DomainName} {
        return {DomainName}(
            id = id,
            name = dto.name,
            // Map all fields here
        )
    }
}
