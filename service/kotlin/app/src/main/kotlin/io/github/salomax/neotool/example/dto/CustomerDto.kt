package io.github.salomax.neotool.example.dto

import io.github.salomax.neotool.example.domain.Customer
import io.github.salomax.neotool.example.domain.CustomerStatus
import io.micronaut.serde.annotation.Serdeable
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

@Serdeable
data class CreateCustomerRequest(
    @field:NotBlank(message = "Customer name is required")
    val name: String,
    
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,
    
    @field:Pattern(
        regexp = "ACTIVE|INACTIVE|PENDING",
        message = "Status must be one of: ACTIVE, INACTIVE, PENDING"
    )
    val status: String = "ACTIVE"
) {
  fun toDomain(): Customer {
    return Customer(
      name = this.name,
      email = this.email,
      status = CustomerStatus.valueOf(this.status)
    )
  }
}

@Serdeable
data class UpdateCustomerRequest(
    @field:NotBlank(message = "Customer name is required")
    val name: String,
    
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,
    
    @field:Pattern(
        regexp = "ACTIVE|INACTIVE|PENDING",
        message = "Status must be one of: ACTIVE, INACTIVE, PENDING"
    )
    val status: String,

    val version: Long
) {
  fun toDomain(id: UUID): Customer {
    return Customer(
      id = id,
      name = this.name,
      email = this.email,
      status = try {
        CustomerStatus.valueOf(this.status)
      } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid status: ${this.status}. Must be one of: ${CustomerStatus.values().joinToString(", ")}")
      },
      updatedAt = Instant.now(),
      version = this.version,
    )
  }
}

@Serdeable
data class CustomerResponse(
    val id: UUID?,
    val name: String,
    val email: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long
)

@Serdeable
data class CustomerListResponse(
    val customers: List<CustomerResponse>,
    val total: Int
)
