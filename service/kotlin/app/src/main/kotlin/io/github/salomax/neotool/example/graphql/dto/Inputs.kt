package io.github.salomax.neotool.example.graphql.dto

import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * DTOs used for GraphQL inputs. Bean Validation annotations ensure proper constraints.
 */
@Introspected
@Serdeable
data class ProductInputDTO(
    @field:NotBlank(message = "name must not be blank")
    var name: String = "",
    @field:NotBlank(message = "sku must not be blank")
    var sku: String = "",
    @field:Min(value = 0, message = "priceCents must be >= 0")
    var priceCents: Long = 0,
    @field:Min(value = 0, message = "stock must be >= 0")
    var stock: Int = 0,
) : BaseInputDTO()

@Introspected
@Serdeable
data class CustomerInputDTO(
    @field:NotBlank(message = "Customer name is required")
    var name: String = "",
    @field:Email(message = "Email must be valid")
    var email: String = "",
    @field:Pattern(
        regexp = "ACTIVE|INACTIVE|PENDING",
        message = "❌ Invalid status. Must be one of: ACTIVE, INACTIVE, PENDING",
    )
    var status: String = "ACTIVE",
) : BaseInputDTO()
