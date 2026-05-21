package io.github.salomax.neotool.banking.graphql.product

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.domain.import.nfce.Product
import io.github.salomax.neotool.banking.domain.import.nfce.ProductAlias
import io.github.salomax.neotool.banking.domain.import.nfce.ProductVariant
import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.model.dto.imports.nfce.CreateProductCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.CreateProductVariantCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.UpdateProductCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.UpdateProductVariantCommand
import io.github.salomax.neotool.banking.service.import.nfce.ProductAnalyticsService
import io.github.salomax.neotool.banking.service.import.nfce.ProductCatalogService
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

@Singleton
class ProductResolver(
    private val productCatalogService: ProductCatalogService,
    private val productAnalyticsService: ProductAnalyticsService,
) {
    fun products(
        search: String?,
        category: String?,
        principal: RequestPrincipal,
    ): List<ProductDTO> {
        val accountId = requireAccountId(principal)
        val products = productCatalogService.listProducts(accountId, search, category)
        val variantsByProduct = productCatalogService.listVariantsForProducts(accountId, products.mapNotNull { it.id })
        val productSummaries = productAnalyticsService.getProductSummaries(accountId, products.mapNotNull { it.id })
        val variantSummaries =
            productAnalyticsService.getVariantSummaries(
                accountId,
                variantsByProduct.values.flatten().mapNotNull { it.id },
            )
        return products.map {
            it.toDTO(
                variants = variantsByProduct[requireNotNull(it.id)].orEmpty(),
                productSummary = productSummaries[requireNotNull(it.id)],
                variantSummaries = variantSummaries,
            )
        }
    }

    fun product(
        id: String,
        principal: RequestPrincipal,
    ): ProductDTO? {
        val accountId = requireAccountId(principal)
        val product = productCatalogService.getProduct(UUID.fromString(id), accountId) ?: return null
        val variants =
            productCatalogService.listVariantsForProducts(
                accountId,
                listOf(requireNotNull(product.id)),
            )[requireNotNull(product.id)].orEmpty()
        val productSummary =
            productAnalyticsService.getProductSummaries(
                accountId,
                listOf(requireNotNull(product.id)),
            )[requireNotNull(product.id)]
        val variantSummaries = productAnalyticsService.getVariantSummaries(accountId, variants.mapNotNull { it.id })
        return product.toDTO(variants, productSummary, variantSummaries)
    }

    fun productVariants(
        search: String?,
        productId: String?,
        limit: Int?,
        principal: RequestPrincipal,
    ): List<ProductVariantDTO> {
        val accountId = requireAccountId(principal)
        val variants =
            if (productId != null) {
                productCatalogService.listVariantsForProducts(
                    accountId,
                    listOf(UUID.fromString(productId)),
                ).values.flatten()
            } else {
                productCatalogService.searchVariants(accountId, search, limit ?: 12)
            }
        val variantSummaries = productAnalyticsService.getVariantSummaries(accountId, variants.mapNotNull { it.id })
        return variants.map { it.toDTO(variantSummaries[requireNotNull(it.id)]) }
    }

    fun productPriceHistory(
        productId: String,
        variantId: String?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        principal: RequestPrincipal,
    ): List<PricePointDTO> {
        val accountId = requireAccountId(principal)
        return productAnalyticsService.getPriceHistory(
            accountId = accountId,
            productId = UUID.fromString(productId),
            variantId = variantId?.let(UUID::fromString),
            startDate = startDate,
            endDate = endDate,
        ).map { point ->
            PricePointDTO(
                date = point.date,
                unitPrice = point.unitPrice.toDouble(),
                storeName = point.storeName,
                municipality = point.municipality,
                state = point.state,
            )
        }
    }

    fun productConsumptionTrend(
        productId: String,
        periodMonths: Int?,
        principal: RequestPrincipal,
    ): ConsumptionTrendDTO {
        val accountId = requireAccountId(principal)
        val trend =
            productAnalyticsService.getConsumptionTrend(
                accountId = accountId,
                productId = UUID.fromString(productId),
                periodMonths = periodMonths ?: 6,
            )
        return ConsumptionTrendDTO(
            productId = trend.productId,
            periodMonths = trend.periodMonths,
            totalQuantity = trend.totalQuantity.toDouble(),
            totalSpent = trend.totalSpent.toDouble(),
            purchaseCount = trend.purchaseCount.toInt(),
            months =
                trend.months.map { month ->
                    ConsumptionTrendPointDTO(
                        month = month.month,
                        totalQuantity = month.totalQuantity.toDouble(),
                        totalSpent = month.totalSpent.toDouble(),
                        purchaseCount = month.purchaseCount.toInt(),
                    )
                },
        )
    }

    fun productStoreComparison(
        productId: String,
        startDate: LocalDate?,
        endDate: LocalDate?,
        principal: RequestPrincipal,
    ): List<StorePriceComparisonDTO> {
        val accountId = requireAccountId(principal)
        return productAnalyticsService.getStorePriceComparisons(
            accountId = accountId,
            productId = UUID.fromString(productId),
            startDate = startDate,
            endDate = endDate,
        ).map { comparison ->
            StorePriceComparisonDTO(
                storeName = comparison.storeName,
                municipality = comparison.municipality,
                state = comparison.state,
                averageUnitPrice = comparison.averageUnitPrice?.toDouble(),
                purchaseCount = comparison.purchaseCount.toInt(),
                lastPurchaseDate = comparison.lastPurchaseDate,
            )
        }
    }

    fun spendingByNcm(
        depth: Int?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        principal: RequestPrincipal,
    ): List<NcmSpendingDTO> {
        val accountId = requireAccountId(principal)
        return productAnalyticsService.getSpendingByNcm(
            accountId = accountId,
            depth = depth ?: 4,
            startDate = startDate,
            endDate = endDate,
        ).map { spending ->
            NcmSpendingDTO(
                ncmCode = spending.ncmCode,
                ncmDescription = spending.ncmDescription,
                totalSpent = spending.totalSpent.toDouble(),
                itemCount = spending.itemCount.toInt(),
                months =
                    spending.months.map { month ->
                        NcmMonthlySpendingDTO(
                            month = month.month,
                            totalSpent = month.totalSpent.toDouble(),
                            itemCount = month.itemCount.toInt(),
                        )
                    },
            )
        }
    }

    fun purchaseItems(
        transactionId: String,
        principal: RequestPrincipal,
    ): List<PurchaseItemDTO> {
        val accountId = requireAccountId(principal)
        val items = productCatalogService.getPurchaseItems(UUID.fromString(transactionId), accountId)
        return buildPurchaseItemDTOs(accountId, items)
    }

    fun nfceDocument(
        transactionId: String,
        principal: RequestPrincipal,
    ): NfceDocumentDTO? {
        val accountId = requireAccountId(principal)
        val txId = UUID.fromString(transactionId)
        val document = productCatalogService.getNfceDocument(txId, accountId) ?: return null
        val items = productCatalogService.getPurchaseItems(txId, accountId)
        return document.toDTO(buildPurchaseItemDTOs(accountId, items))
    }

    fun unmappedItems(
        limit: Int?,
        principal: RequestPrincipal,
    ): List<UnmappedItemGroupDTO> {
        val accountId = requireAccountId(principal)
        return productCatalogService.listUnmappedItems(accountId, limit ?: 20)
            .map { group ->
                UnmappedItemGroupDTO(
                    rawDescription = group.rawDescription,
                    count = group.count.toInt(),
                    suggestedVariant = group.suggestedVariant?.toDTO(),
                    confidence = group.confidence?.toFloat(),
                    sampleItems = buildPurchaseItemDTOs(accountId, group.sampleItems),
                )
            }
    }

    fun createProduct(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ProductDTO {
        val accountId = requireAccountId(principal)
        val product =
            productCatalogService.createProduct(
                accountId = accountId,
                command =
                    CreateProductCommand(
                        name = input.requiredString("name"),
                        ncmCode = input.optionalString("ncmCode"),
                        defaultUnit = input.optionalString("defaultUnit"),
                        category = input.optionalString("category"),
                    ),
            )
        return product.toDTO(emptyList(), null, emptyMap())
    }

    fun updateProduct(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ProductDTO {
        val accountId = requireAccountId(principal)
        val product =
            productCatalogService.updateProduct(
                id = UUID.fromString(id),
                accountId = accountId,
                command =
                    UpdateProductCommand(
                        name = input.optionalString("name"),
                        ncmCode = input.optionalString("ncmCode"),
                        defaultUnit = input.optionalString("defaultUnit"),
                        category = input.optionalString("category"),
                    ),
            )
        val variants =
            productCatalogService.listVariantsForProducts(
                accountId,
                listOf(requireNotNull(product.id)),
            )[requireNotNull(product.id)].orEmpty()
        val productSummary =
            productAnalyticsService.getProductSummaries(
                accountId,
                listOf(requireNotNull(product.id)),
            )[requireNotNull(product.id)]
        val variantSummaries = productAnalyticsService.getVariantSummaries(accountId, variants.mapNotNull { it.id })
        return product.toDTO(variants, productSummary, variantSummaries)
    }

    fun createProductVariant(
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ProductVariantDTO {
        val accountId = requireAccountId(principal)
        val variant =
            productCatalogService.createVariant(
                accountId = accountId,
                command =
                    CreateProductVariantCommand(
                        productId = UUID.fromString(input.requiredString("productId")),
                        name = input.requiredString("name"),
                        brand = input.optionalString("brand"),
                        ean = input.optionalString("ean"),
                        quantityValue = input.optionalBigDecimal("quantityValue"),
                        quantityUnit = input.optionalString("quantityUnit"),
                        normalizedQuantityValue = input.optionalBigDecimal("normalizedQuantityValue"),
                        normalizedQuantityUnit = input.optionalString("normalizedQuantityUnit"),
                    ),
            )
        return variant.toDTO()
    }

    fun updateProductVariant(
        id: String,
        input: Map<String, Any?>,
        principal: RequestPrincipal,
    ): ProductVariantDTO {
        val accountId = requireAccountId(principal)
        val variant =
            productCatalogService.updateVariant(
                id = UUID.fromString(id),
                accountId = accountId,
                command =
                    UpdateProductVariantCommand(
                        productId = input.optionalString("productId")?.let(UUID::fromString),
                        name = input.optionalString("name"),
                        brand = input.optionalString("brand"),
                        ean = input.optionalString("ean"),
                        quantityValue = input.optionalBigDecimal("quantityValue"),
                        quantityUnit = input.optionalString("quantityUnit"),
                        normalizedQuantityValue = input.optionalBigDecimal("normalizedQuantityValue"),
                        normalizedQuantityUnit = input.optionalString("normalizedQuantityUnit"),
                    ),
            )
        return variant.toDTO(
            productAnalyticsService.getVariantSummaries(
                accountId,
                listOf(requireNotNull(variant.id)),
            )[requireNotNull(variant.id)],
        )
    }

    fun mapItemToVariant(
        rawDescription: String,
        variantId: String,
        principal: RequestPrincipal,
    ): ProductAliasDTO {
        val accountId = requireAccountId(principal)
        val alias =
            productCatalogService.mapRawDescriptionToVariant(
                accountId,
                rawDescription,
                UUID.fromString(variantId),
            )
        val variant = productCatalogService.getVariant(alias.productVariantId, accountId)
        return alias.toDTO(variant?.toDTO())
    }

    fun unmapItem(
        aliasId: String,
        principal: RequestPrincipal,
    ): Boolean = productCatalogService.unmapAlias(requireAccountId(principal), UUID.fromString(aliasId))

    private fun buildPurchaseItemDTOs(
        accountId: UUID,
        items: List<PurchaseItem>,
    ): List<PurchaseItemDTO> {
        val variantsById = productCatalogService.getVariantsByIds(accountId, items.mapNotNull { it.productVariantId })
        return items.map { item ->
            val variant = item.productVariantId?.let(variantsById::get)
            item.toDTO(variant?.toDTO())
        }
    }

    private fun Product.toDTO(
        variants: List<ProductVariant>,
        productSummary: io.github.salomax.neotool.banking.service.import.nfce.ProductSummary?,
        variantSummaries: Map<UUID, io.github.salomax.neotool.banking.service.import.nfce.VariantPriceSummary>,
    ): ProductDTO =
        ProductDTO(
            id = requireNotNull(id),
            name = name,
            ncmCode = ncmCode,
            defaultUnit = defaultUnit,
            category = category,
            variants = variants.map { variant -> variant.toDTO(variantSummaries[requireNotNull(variant.id)]) },
            averagePrice = productSummary?.averagePrice?.toDouble(),
            totalSpent = productSummary?.totalSpent?.toDouble() ?: 0.0,
            purchaseCount = productSummary?.purchaseCount?.toInt() ?: 0,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ProductVariant.toDTO(
        summary: io.github.salomax.neotool.banking.service.import.nfce.VariantPriceSummary? = null,
    ): ProductVariantDTO =
        ProductVariantDTO(
            id = requireNotNull(id),
            productId = productId,
            name = name,
            brand = brand,
            ean = ean,
            quantityValue = quantityValue?.toDouble(),
            quantityUnit = quantityUnit,
            normalizedQuantityValue = normalizedQuantityValue?.toDouble(),
            normalizedQuantityUnit = normalizedQuantityUnit,
            averageUnitPrice = summary?.averageUnitPrice?.toDouble(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun ProductAlias.toDTO(productVariant: ProductVariantDTO?): ProductAliasDTO =
        ProductAliasDTO(
            id = requireNotNull(id),
            productVariantId = productVariantId,
            productVariant = productVariant,
            rawDescription = rawDescription,
            source = source,
            confidence = confidence?.toDouble(),
            confirmed = confirmed,
            createdAt = createdAt,
        )

    private fun NfceDocument.toDTO(items: List<PurchaseItemDTO>): NfceDocumentDTO =
        NfceDocumentDTO(
            id = requireNotNull(id),
            transactionId = transactionId,
            accessKey = accessKey,
            issuerCnpj = issuerCnpj,
            issuerName = issuerName,
            issuerTradeName = issuerTradeName,
            issuerStateRegistration = issuerStateRegistration,
            issuerAddress = issuerAddress,
            issuerPostalCode = issuerPostalCode,
            emissionDate = emissionDate,
            totalValue = totalValue.toDouble(),
            totalTax = totalTax?.toDouble(),
            buyerCpf = buyerCpf,
            municipalityCode = municipalityCode,
            municipality = municipality,
            state = state,
            countryCode = countryCode,
            sourceUrl = sourceData?.get("url"),
            items = items,
            createdAt = createdAt,
        )

    private fun PurchaseItem.toDTO(productVariant: ProductVariantDTO?): PurchaseItemDTO {
        val normalizedUnitPrice =
            if (productVariant?.normalizedQuantityValue != null && productVariant.normalizedQuantityValue > 0.0) {
                effectivePrice.divide(
                    BigDecimal.valueOf(productVariant.normalizedQuantityValue),
                    4,
                    RoundingMode.HALF_UP,
                ).toDouble()
            } else {
                null
            }
        return PurchaseItemDTO(
            id = requireNotNull(id),
            transactionId = transactionId,
            productVariantId = productVariantId,
            productVariant = productVariant,
            sequence = sequence,
            rawDescription = rawDescription,
            ncmCode = ncmCode,
            ean = ean,
            issuerProductCode = issuerProductCode,
            quantity = quantity.toDouble(),
            unit = unit,
            unitPrice = unitPrice.toDouble(),
            totalPrice = totalPrice.toDouble(),
            discount = discount?.toDouble(),
            effectivePrice = effectivePrice.toDouble(),
            normalizedUnitPrice = normalizedUnitPrice,
            taxAmount = taxAmount?.toDouble(),
            createdAt = createdAt,
        )
    }

    private fun requireAccountId(principal: RequestPrincipal): UUID =
        requireNotNull(principal.currentAccountId) { "No active account in token" }

    private fun Map<String, Any?>.requiredString(key: String): String =
        (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("$key is required")

    private fun Map<String, Any?>.optionalString(key: String): String? =
        (this[key] as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun Map<String, Any?>.optionalBigDecimal(key: String): BigDecimal? =
        when (val value = this[key]) {
            null -> null
            is BigDecimal -> value
            is Number -> BigDecimal(value.toString())
            is String -> value.trim().takeIf { it.isNotEmpty() }?.let(::BigDecimal)
            else -> throw IllegalArgumentException("$key must be numeric")
        }
}
