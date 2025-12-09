package io.github.salomax.neotool.{module}.graphql.dto

import io.github.salomax.neotool.common.graphql.BaseInputDTO
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Email

/**
 * Input DTO for {EntityName} GraphQL mutations.
 * 
 * Replace:
 * - {module} with your module name (app, security, assistant, etc.)
 * - {EntityName} with your entity name (e.g., Product, Customer)
 * - Add/remove fields and validation annotations as needed
 */
@Introspected
@Serdeable
data class {EntityName}InputDTO(
    @field:NotBlank(message = "name must not be blank")
    var name: String = "",
    // Add your fields here with validation
    // Example:
    // @field:Email(message = "Email must be valid")
    // var email: String = "",
    // @field:Min(value = 0, message = "priceCents must be >= 0")
    // var priceCents: Long = 0,
) : BaseInputDTO()
