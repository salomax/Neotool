package io.github.salomax.neotool.example.graphql.dto

import io.github.salomax.neotool.example.domain.Product
import io.github.salomax.neotool.example.domain.Customer
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import io.github.salomax.neotool.common.graphql.BaseInputDTO
import jakarta.validation.constraints.*

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
    var stock: Int = 0
) : BaseInputDTO()

@Introspected
@Serdeable
data class CustomerInputDTO(
    @field:NotBlank(message = "Customer name is required")
    var name: String = "",
    @field:Email(message = "Email must be valid")
    var email: String = "",
    @field:Pattern(regexp = "ACTIVE|INACTIVE|PENDING", message = "❌ Invalid status. Must be one of: ACTIVE, INACTIVE, PENDING")
    var status: String = "ACTIVE"
) : BaseInputDTO()

/**
 * Enums for GraphQL type safety
 */
enum class CustomerStatus {
    ACTIVE,
    INACTIVE,
    PENDING
}

/**
 * Payload types following Relay pattern for GraphQL best practices
 */
@Introspected
@Serdeable
data class ProductPayload(
    val product: Product? = null,
    val errors: List<UserError> = emptyList()
)

@Introspected
@Serdeable
data class CustomerPayload(
    val customer: Customer? = null,
    val errors: List<UserError> = emptyList()
)

@Introspected
@Serdeable
data class UserError(
    val field: List<String>,
    val message: String
)

/**
 * Connection types for pagination following Relay pattern
 */
@Introspected
@Serdeable
data class ProductConnection(
    val edges: List<ProductEdge>,
    val pageInfo: PageInfo,
    val totalCount: Int
)

@Introspected
@Serdeable
data class ProductEdge(
    val node: Product,
    val cursor: String
)

@Introspected
@Serdeable
data class CustomerConnection(
    val edges: List<CustomerEdge>,
    val pageInfo: PageInfo,
    val totalCount: Int
)

@Introspected
@Serdeable
data class CustomerEdge(
    val node: Customer,
    val cursor: String
)

@Introspected
@Serdeable
data class PageInfo(
    val hasNextPage: Boolean,
    val hasPreviousPage: Boolean,
    val startCursor: String? = null,
    val endCursor: String? = null
)

/**
 * Authentication DTOs
 */
@Introspected
@Serdeable
data class SignInInputDTO(
    @field:Email(message = "Email must be valid")
    @field:NotBlank(message = "Email is required")
    var email: String = "",
    @field:NotBlank(message = "Password is required")
    var password: String = "",
    var rememberMe: Boolean? = false
) : BaseInputDTO()

@Introspected
@Serdeable
data class SignInPayloadDTO(
    val token: String,
    val refreshToken: String? = null,
    val user: UserDTO
)

@Introspected
@Serdeable
data class UserDTO(
    val id: String,
    val email: String,
    val displayName: String? = null
)
