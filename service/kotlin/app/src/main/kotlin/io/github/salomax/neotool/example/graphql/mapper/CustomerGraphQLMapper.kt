package io.github.salomax.neotool.example.graphql.mapper

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.github.salomax.neotool.example.graphql.dto.CustomerInputDTO
import java.util.UUID

/**
 * Mapper for converting between GraphQL input maps, DTOs, and domain entities for Customers.
 * Separates mapping concerns from resolver logic for better testability and maintainability.
 *
 * Note: The getExistingEntity function is provided to allow fetching existing entities for
 * version management during updates. This keeps the mapper testable without tight coupling.
 *
 * This class is not a singleton because it needs resolver-specific dependencies.
 * Instances are created by the resolver.
 */
class CustomerGraphQLMapper(
    private val getExistingEntity: (UUID) -> Customer? = { null },
) {
    /**
     * Extract field with type safety and default values
     * @param input The input map from GraphQL
     * @param name The field name to extract
     * @param defaultValue Optional default value if field is missing or null
     * @return The extracted value or default, or throws if required field is missing
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
            // Type doesn't match, use default if provided, otherwise throw
            defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
        }
    }

    /**
     * Map GraphQL input map to CustomerInputDTO
     * @param input The GraphQL input map
     * @return CustomerInputDTO with extracted and validated fields
     */
    fun mapToInputDTO(input: Map<String, Any?>): CustomerInputDTO {
        return CustomerInputDTO(
            name = extractField(input, "name"),
            email = extractField(input, "email"),
            status = extractField(input, "status", "ACTIVE"),
        )
    }

    /**
     * Map CustomerInputDTO to Customer domain entity
     * For updates, fetches the existing entity to preserve version for optimistic locking.
     * @param dto The input DTO
     * @param id Optional ID for updates (null for creates)
     * @return Customer domain entity
     */
    fun mapToEntity(
        dto: CustomerInputDTO,
        id: UUID? = null,
    ): Customer {
        // For updates, we need to fetch the existing entity to get the current version
        val existingEntity =
            if (id != null) {
                getExistingEntity(id)
            } else {
                null
            }

        return Customer(
            id = id,
            name = dto.name,
            email = dto.email,
            status =
                try {
                    CustomerStatus.valueOf(dto.status)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Invalid status: ${dto.status}. Must be one of: ${CustomerStatus.values().joinToString(", ")}",
                    )
                },
            version = existingEntity?.version ?: 0,
        )
    }
}
