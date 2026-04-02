package io.github.salomax.neotool.banking.domain.import.nfce

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class NfceDocument(
    val id: UUID? = null,
    val transactionId: UUID,
    val accountId: UUID,
    val accessKey: String,
    val issuerCnpj: String,
    val issuerName: String,
    val issuerTradeName: String? = null,
    val issuerStateRegistration: String? = null,
    val issuerAddress: String? = null,
    val issuerPostalCode: String? = null,
    val emissionDate: Instant,
    val totalValue: BigDecimal,
    val totalTax: BigDecimal? = null,
    val buyerCpf: String? = null,
    val municipalityCode: String? = null,
    val municipality: String? = null,
    val state: String,
    val countryCode: String = "BR",
    val sourceData: Map<String, String?>? = null,
    val createdAt: Instant,
)
