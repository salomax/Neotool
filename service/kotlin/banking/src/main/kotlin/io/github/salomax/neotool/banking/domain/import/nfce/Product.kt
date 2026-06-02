package io.github.salomax.neotool.banking.domain.import.nfce

import java.time.Instant
import java.util.UUID

data class Product(
    val id: UUID? = null,
    val accountId: UUID,
    val name: String,
    val ncmCode: String? = null,
    val defaultUnit: String? = null,
    val category: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)
