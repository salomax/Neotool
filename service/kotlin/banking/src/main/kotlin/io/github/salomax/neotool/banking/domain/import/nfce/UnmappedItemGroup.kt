package io.github.salomax.neotool.banking.domain.import.nfce

data class UnmappedItemGroup(
    val rawDescription: String,
    val count: Long,
    val sampleItems: List<PurchaseItem>,
    val suggestedVariant: ProductVariant? = null,
    val confidence: Double? = null,
)
