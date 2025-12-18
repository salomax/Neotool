package io.github.salomax.neotool.example.domain

import io.github.salomax.neotool.example.dto.CustomerResponse
import io.github.salomax.neotool.example.entity.CustomerEntity
import java.time.Instant
import java.util.UUID

data class Customer(
    val id: UUID? = null,
    val name: String,
    val email: String,
    val status: CustomerStatus = CustomerStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0,
) {
    fun toEntity(): CustomerEntity {
        return CustomerEntity(
            id = this.id,
            name = this.name,
            email = this.email,
            status = this.status,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }

    fun toResponse(): CustomerResponse {
        return CustomerResponse(
            id = this.id,
            name = this.name,
            email = this.email,
            status = this.status.name,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            version = this.version,
        )
    }
}

enum class CustomerStatus {
    ACTIVE,
    INACTIVE,
    PENDING,
}
