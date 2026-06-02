package io.github.salomax.neotool.banking.service.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument
import io.github.salomax.neotool.banking.domain.import.nfce.Product
import io.github.salomax.neotool.banking.domain.import.nfce.ProductAlias
import io.github.salomax.neotool.banking.domain.import.nfce.ProductAliasSource
import io.github.salomax.neotool.banking.domain.import.nfce.ProductVariant
import io.github.salomax.neotool.banking.domain.import.nfce.PurchaseItem
import io.github.salomax.neotool.banking.domain.import.nfce.UnmappedItemGroup
import io.github.salomax.neotool.banking.model.dto.imports.nfce.CreateProductCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.CreateProductVariantCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.UpdateProductCommand
import io.github.salomax.neotool.banking.model.dto.imports.nfce.UpdateProductVariantCommand
import io.github.salomax.neotool.banking.repo.import.nfce.NfceDocumentRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductAliasRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductRepository
import io.github.salomax.neotool.banking.repo.import.nfce.ProductVariantRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemEntityRepository
import io.github.salomax.neotool.banking.repo.import.nfce.PurchaseItemRepository
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

@Singleton
open class ProductCatalogService(
    private val productRepository: ProductRepository,
    private val productVariantRepository: ProductVariantRepository,
    private val productAliasRepository: ProductAliasRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val purchaseItemEntityRepository: PurchaseItemEntityRepository,
    private val nfceDocumentRepository: NfceDocumentRepository,
) {
    fun listProducts(
        accountId: UUID,
        search: String? = null,
        category: String? = null,
    ): List<Product> = productRepository.list(accountId, search, category)

    fun listVariantsForProducts(
        accountId: UUID,
        productIds: Collection<UUID>,
    ): Map<UUID, List<ProductVariant>> =
        productVariantRepository.findByProductIds(productIds, accountId).groupBy { it.productId }

    fun searchVariants(
        accountId: UUID,
        search: String?,
        limit: Int = 12,
    ): List<ProductVariant> = productVariantRepository.search(accountId, search, limit)

    fun getProduct(
        id: UUID,
        accountId: UUID,
    ): Product? = productRepository.findById(id, accountId)

    fun getVariant(
        id: UUID,
        accountId: UUID,
    ): ProductVariant? = productVariantRepository.findById(id, accountId)

    fun getVariantsByIds(
        accountId: UUID,
        ids: Collection<UUID>,
    ): Map<UUID, ProductVariant> =
        productVariantRepository.findByIds(
            ids,
            accountId,
        ).associateBy { requireNotNull(it.id) }

    fun getProductsByIds(
        accountId: UUID,
        ids: Collection<UUID>,
    ): Map<UUID, Product> = productRepository.findByIds(ids, accountId).associateBy { requireNotNull(it.id) }

    fun getPurchaseItems(
        transactionId: UUID,
        accountId: UUID,
    ): List<PurchaseItem> = purchaseItemRepository.findByTransactionId(transactionId, accountId)

    fun getNfceDocument(
        transactionId: UUID,
        accountId: UUID,
    ): NfceDocument? = nfceDocumentRepository.findByTransactionId(transactionId, accountId)

    fun listUnmappedItems(
        accountId: UUID,
        limit: Int = 20,
    ): List<UnmappedItemGroup> =
        purchaseItemEntityRepository.findUnmappedGroups(accountId, limit.coerceIn(1, 100)).map { row ->
            val rawDescription = row.getRawDescription()
            UnmappedItemGroup(
                rawDescription = rawDescription,
                count = row.getItemCount(),
                sampleItems = purchaseItemRepository.findTop3UnmappedByRawDescription(accountId, rawDescription),
            )
        }

    @Transactional
    open fun createProduct(
        accountId: UUID,
        command: CreateProductCommand,
    ): Product {
        val now = Instant.now()
        val name = command.name.trim()
        require(name.isNotBlank()) { "Product name must not be blank" }
        ensureUniqueProductName(accountId, name, null)
        return productRepository.save(
            Product(
                accountId = accountId,
                name = name,
                ncmCode = command.ncmCode.normalizeCode(),
                defaultUnit = command.defaultUnit.normalizeText(uppercase = true),
                category = command.category.normalizeText(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    open fun updateProduct(
        id: UUID,
        accountId: UUID,
        command: UpdateProductCommand,
    ): Product {
        val existing = requireNotNull(productRepository.findById(id, accountId)) { "Product not found" }
        val name = command.name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name
        ensureUniqueProductName(accountId, name, id)
        return productRepository.save(
            existing.copy(
                name = name,
                ncmCode = command.ncmCode.normalizeCode(existing.ncmCode),
                defaultUnit = command.defaultUnit.normalizeText(existing.defaultUnit, uppercase = true),
                category = command.category.normalizeText(existing.category),
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun createVariant(
        accountId: UUID,
        command: CreateProductVariantCommand,
    ): ProductVariant {
        val product = requireNotNull(productRepository.findById(command.productId, accountId)) { "Product not found" }
        val now = Instant.now()
        val name = command.name.trim()
        require(name.isNotBlank()) { "Variant name must not be blank" }
        val normalized =
            normalizeQuantity(
                quantityValue = command.quantityValue,
                quantityUnit = command.quantityUnit,
                normalizedQuantityValue = command.normalizedQuantityValue,
                normalizedQuantityUnit = command.normalizedQuantityUnit,
            )
        return productVariantRepository.save(
            ProductVariant(
                productId = requireNotNull(product.id),
                accountId = accountId,
                name = name,
                brand = command.brand.normalizeText(),
                ean = command.ean.normalizeText(),
                quantityValue = command.quantityValue,
                quantityUnit = command.quantityUnit.normalizeText(uppercase = true),
                normalizedQuantityValue = normalized.first,
                normalizedQuantityUnit = normalized.second,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    @Transactional
    open fun updateVariant(
        id: UUID,
        accountId: UUID,
        command: UpdateProductVariantCommand,
    ): ProductVariant {
        val existing = requireNotNull(productVariantRepository.findById(id, accountId)) { "Variant not found" }
        val targetProductId = command.productId ?: existing.productId
        requireNotNull(productRepository.findById(targetProductId, accountId)) { "Product not found" }
        val quantityValue = command.quantityValue ?: existing.quantityValue
        val quantityUnit = command.quantityUnit.normalizeText(existing.quantityUnit, uppercase = true)
        val normalized =
            normalizeQuantity(
                quantityValue = quantityValue,
                quantityUnit = quantityUnit,
                normalizedQuantityValue = command.normalizedQuantityValue ?: existing.normalizedQuantityValue,
                normalizedQuantityUnit =
                    command.normalizedQuantityUnit.normalizeText(
                        existing.normalizedQuantityUnit,
                        uppercase = true,
                    ),
            )
        val updatedName = command.name?.trim()?.takeIf { it.isNotEmpty() } ?: existing.name
        require(updatedName.isNotBlank()) { "Variant name must not be blank" }
        return productVariantRepository.save(
            existing.copy(
                productId = targetProductId,
                name = updatedName,
                brand = command.brand.normalizeText(existing.brand),
                ean = command.ean.normalizeText(existing.ean),
                quantityValue = quantityValue,
                quantityUnit = quantityUnit,
                normalizedQuantityValue = normalized.first,
                normalizedQuantityUnit = normalized.second,
                updatedAt = Instant.now(),
            ),
        )
    }

    @Transactional
    open fun mapRawDescriptionToVariant(
        accountId: UUID,
        rawDescription: String,
        variantId: UUID,
    ): ProductAlias {
        val variant = requireNotNull(productVariantRepository.findById(variantId, accountId)) { "Variant not found" }
        val normalizedDescription = rawDescription.trim()
        require(normalizedDescription.isNotBlank()) { "rawDescription must not be blank" }
        val existing = productAliasRepository.findByAccountIdAndRawDescription(accountId, normalizedDescription)
        val alias =
            productAliasRepository.save(
                ProductAlias(
                    id = existing?.id,
                    productVariantId = requireNotNull(variant.id),
                    accountId = accountId,
                    rawDescription = normalizedDescription,
                    source = ProductAliasSource.MANUAL,
                    confidence = null,
                    confirmed = true,
                    createdAt = existing?.createdAt ?: Instant.now(),
                ),
            )
        purchaseItemEntityRepository.mapUnmappedItemsToVariant(
            accountId,
            normalizedDescription,
            requireNotNull(alias.productVariantId),
        )
        return alias
    }

    @Transactional
    open fun unmapAlias(
        accountId: UUID,
        aliasId: UUID,
    ): Boolean {
        val existing = productAliasRepository.findById(aliasId, accountId) ?: return false
        purchaseItemEntityRepository.clearMappedItemsForAlias(
            accountId = accountId,
            rawDescription = existing.rawDescription,
            productVariantId = existing.productVariantId,
        )
        productAliasRepository.deleteById(aliasId)
        return true
    }

    private fun ensureUniqueProductName(
        accountId: UUID,
        name: String,
        currentProductId: UUID?,
    ) {
        val existing = productRepository.findByName(accountId, name) ?: return
        if (existing.id != currentProductId) {
            throw IllegalArgumentException("A product with this name already exists")
        }
    }

    private fun normalizeQuantity(
        quantityValue: BigDecimal?,
        quantityUnit: String?,
        normalizedQuantityValue: BigDecimal?,
        normalizedQuantityUnit: String?,
    ): Pair<BigDecimal?, String?> {
        if (normalizedQuantityValue != null && !normalizedQuantityUnit.isNullOrBlank()) {
            return normalizedQuantityValue to normalizedQuantityUnit.trim().uppercase()
        }
        if (quantityValue == null || quantityUnit.isNullOrBlank()) return null to null
        return when (quantityUnit.trim().uppercase()) {
            "KG" -> quantityValue to "KG"
            "G" -> quantityValue.divide(BigDecimal("1000"), 4, RoundingMode.HALF_UP) to "KG"
            "MG" -> quantityValue.divide(BigDecimal("1000000"), 4, RoundingMode.HALF_UP) to "KG"
            "L" -> quantityValue to "L"
            "ML" -> quantityValue.divide(BigDecimal("1000"), 4, RoundingMode.HALF_UP) to "L"
            "M" -> quantityValue to "M"
            "CM" -> quantityValue.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP) to "M"
            "MM" -> quantityValue.divide(BigDecimal("1000"), 4, RoundingMode.HALF_UP) to "M"
            "UN" -> quantityValue to "UN"
            else -> null to null
        }
    }

    private fun String?.normalizeCode(current: String? = null): String? =
        when {
            this == null -> current
            this.isBlank() -> null
            else -> this.trim()
        }

    private fun String?.normalizeText(
        current: String? = null,
        uppercase: Boolean = false,
    ): String? =
        when {
            this == null -> current
            this.isBlank() -> null
            uppercase -> this.trim().uppercase()
            else -> this.trim()
        }
}
