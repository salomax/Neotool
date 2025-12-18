package io.github.salomax.neotool.example.graphql.mapper

import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.graphql.dto.ProductInputDTO
import jakarta.inject.Singleton
import java.util.UUID

/**
 * Mapper for converting between GraphQL input maps, DTOs, and domain entities for Products.
 * Separates mapping concerns from resolver logic for better testability and maintainability.
 */
@Singleton
class ProductGraphQLMapper {
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
            // Handle numeric type conversions (GraphQL Int may come as Int but we need Long)
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
                    // Type doesn't match and can't convert, use default if provided, otherwise throw
                    defaultValue ?: throw IllegalArgumentException("Field '$name' is required")
                }
            }
        }
    }

    /**
     * Map GraphQL input map to ProductInputDTO
     * @param input The GraphQL input map
     * @return ProductInputDTO with extracted and validated fields
     */
    fun mapToInputDTO(input: Map<String, Any?>): ProductInputDTO {
        return ProductInputDTO(
            name = extractField(input, "name"),
            sku = extractField(input, "sku"),
            priceCents = extractField(input, "priceCents", 0L),
            stock = extractField(input, "stock", 0),
        )
    }

    /**
     * Map ProductInputDTO to Product domain entity
     * @param dto The input DTO
     * @param id Optional ID for updates (null for creates)
     * @return Product domain entity
     */
    fun mapToEntity(
        dto: ProductInputDTO,
        id: UUID? = null,
    ): Product {
        return Product(
            id = id,
            name = dto.name,
            sku = dto.sku,
            priceCents = dto.priceCents,
            stock = dto.stock,
        )
    }
}
