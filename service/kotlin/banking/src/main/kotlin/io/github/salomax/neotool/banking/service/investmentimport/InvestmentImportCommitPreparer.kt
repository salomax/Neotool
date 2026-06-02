package io.github.salomax.neotool.banking.service.investmentimport

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.Asset
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investment.Indexer
import io.github.salomax.neotool.banking.domain.investment.InvestmentSource
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransaction
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionDirection
import io.github.salomax.neotool.banking.domain.investment.InvestmentTransactionType
import io.github.salomax.neotool.banking.domain.investmentimport.ConfirmInvestmentImportCommand
import io.github.salomax.neotool.banking.domain.investmentimport.ConflictResolution
import io.github.salomax.neotool.banking.domain.investmentimport.InstitutionAccountMapping
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReview
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReviewRow
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.repo.investment.InvestmentTransactionRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Singleton
open class InvestmentImportCommitPreparer(
    private val financialAccountRepository: FinancialAccountRepository,
    private val assetRepository: AssetRepository,
    private val holdingRepository: HoldingRepository,
    private val investmentTransactionRepository: InvestmentTransactionRepository,
) {
    @Transactional
    open fun commit(
        command: ConfirmInvestmentImportCommand,
        review: InvestmentImportReview,
    ) {
        val mappingById = command.institutionMappings.associateBy { it.reviewInstitutionId }
        validateMappings(mappingById, review)

        val accountByReviewInstitutionId =
            mappingById.mapValues { (_, mapping) ->
                resolveInvestmentAccount(mapping, command.accountId)
            }

        val patchByRowIndex = command.rowPatches.associateBy { it.rowIndex }

        review.institutions.forEach { institution ->
            val financialAccount = accountByReviewInstitutionId[institution.reviewInstitutionId]
                ?: throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "institutionMappings",
                    parameters = mapOf("reason" to "Missing mapping for institution: ${institution.institutionName}"),
                )
            val financialAccountId = requireNotNull(financialAccount.id)

            institution.rows.forEach { row ->
                val patch = patchByRowIndex[row.rowIndex]
                if (patch?.skip == true) return@forEach

                val principal = patch?.principal
                if (row.requiresPrincipal && principal == null) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = "rowPatches[${row.rowIndex}].principal",
                        parameters = mapOf("reason" to "Principal is required for row ${row.rowIndex} (${row.productName})"),
                    )
                }

                if (row.conflictingHoldingId != null && patch?.conflictResolution == null) {
                    throw ValidationException(
                        CommonErrorCode.INVALID_INPUT,
                        field = "rowPatches[${row.rowIndex}].conflictResolution",
                        parameters = mapOf("reason" to "conflictResolution required for conflicting row ${row.rowIndex}"),
                    )
                }

                if (patch?.conflictResolution == ConflictResolution.KEEP_EXISTING) return@forEach

                val asset = resolveOrCreateAsset(row)
                val holding = upsertHolding(
                    financialAccountId = financialAccountId,
                    accountId = command.accountId,
                    assetId = requireNotNull(asset.id),
                    row = row,
                    principal = principal,
                )

                createPurchaseTransactionIfAbsent(
                    command = command,
                    financialAccountId = financialAccountId,
                    holdingId = requireNotNull(holding.id),
                    row = row,
                    principal = principal,
                )
            }
        }
    }

    private fun validateMappings(
        mappingById: Map<String, InstitutionAccountMapping>,
        review: InvestmentImportReview,
    ) {
        val reviewInstitutionIds = review.institutions.map { it.reviewInstitutionId }.toSet()
        mappingById.keys.forEach { id ->
            if (id !in reviewInstitutionIds) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "institutionMappings",
                    parameters = mapOf("reason" to "Unknown reviewInstitutionId: $id"),
                )
            }
        }

        review.institutions.forEach { institution ->
            if (institution.reviewInstitutionId !in mappingById) {
                throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "institutionMappings",
                    parameters = mapOf("reason" to "Missing mapping for institution: ${institution.institutionName}"),
                )
            }
        }
    }

    private fun resolveInvestmentAccount(
        mapping: InstitutionAccountMapping,
        accountId: UUID,
    ): FinancialAccount {
        val account =
            financialAccountRepository.findById(mapping.financialAccountId, accountId)
                ?: throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "institutionMappings",
                    parameters = mapOf("reason" to "Financial account ${mapping.financialAccountId} not found"),
                )

        if (account.type != FinancialAccountType.INVESTMENT) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "institutionMappings",
                parameters = mapOf("reason" to "Account ${mapping.financialAccountId} is not an INVESTMENT account"),
            )
        }

        return account
    }

    private fun resolveOrCreateAsset(row: InvestmentImportReviewRow): Asset {
        if (!row.providerCode.isNullOrBlank()) {
            assetRepository.findByProviderId(row.providerCode)?.let { return it }
        }

        val assetType = mapAssetType(row.assetTypeCode)
        assetRepository.findByNameAndTypeAndIssuer(
            name = row.productName,
            assetType = assetType,
            issuerName = row.issuerName,
        )?.let { return it }

        val now = Instant.now()
        return assetRepository.save(
            Asset(
                name = row.productName,
                assetType = assetType,
                issuerName = row.issuerName,
                providerId = row.providerCode,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun upsertHolding(
        financialAccountId: UUID,
        accountId: UUID,
        assetId: UUID,
        row: InvestmentImportReviewRow,
        principal: BigDecimal?,
    ): Holding {
        val now = Instant.now()
        val existingHolding =
            row.conflictingHoldingId?.let { holdingRepository.findById(it, accountId) }
                ?: holdingRepository.findByFinancialAccountAndAsset(financialAccountId, assetId)

        val nextAverageCost = computeAverageCost(principal, row.quantity, existingHolding?.averageCost)
        val nextGrossValue = row.currentValue ?: existingHolding?.grossValue
        val nextNetValue = row.currentValue ?: existingHolding?.netValue

        return if (existingHolding != null) {
            holdingRepository.update(
                existingHolding.copy(
                    quantity = row.quantity ?: existingHolding.quantity,
                    averageCost = nextAverageCost,
                    issueDate = row.issueDate ?: existingHolding.issueDate,
                    maturityDate = row.maturityDate ?: existingHolding.maturityDate,
                    indexer = mapIndexer(row.indexerLabel) ?: existingHolding.indexer,
                    grossValue = nextGrossValue,
                    netValue = nextNetValue,
                    lastValuedAt = if (row.currentValue != null) now else existingHolding.lastValuedAt,
                    source = InvestmentSource.FILE_IMPORT,
                    providerInvestmentId = row.providerCode ?: existingHolding.providerInvestmentId,
                    updatedAt = now,
                ),
            )
        } else {
            val quantity = row.quantity
                ?: throw ValidationException(
                    CommonErrorCode.INVALID_INPUT,
                    field = "rowPatches[${row.rowIndex}].quantity",
                    parameters = mapOf("reason" to "Quantidade ausente para a linha ${row.rowIndex}"),
                )

            holdingRepository.save(
                Holding(
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    assetId = assetId,
                    quantity = quantity,
                    averageCost = nextAverageCost,
                    issueDate = row.issueDate,
                    maturityDate = row.maturityDate,
                    indexer = mapIndexer(row.indexerLabel),
                    isTaxExempt = row.assetTypeCode in TAX_EXEMPT_TYPES,
                    grossValue = nextGrossValue,
                    netValue = nextNetValue,
                    lastValuedAt = if (row.currentValue != null) now else null,
                    source = InvestmentSource.FILE_IMPORT,
                    providerInvestmentId = row.providerCode,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun createPurchaseTransactionIfAbsent(
        command: ConfirmInvestmentImportCommand,
        financialAccountId: UUID,
        holdingId: UUID,
        row: InvestmentImportReviewRow,
        principal: BigDecimal?,
    ) {
        val providerCode = row.providerCode
        if (!providerCode.isNullOrBlank() && investmentTransactionRepository.existsByProviderTransactionId(financialAccountId, providerCode)) {
            return
        }

        val amount = principal ?: BigDecimal.ZERO
        investmentTransactionRepository.save(
            InvestmentTransaction(
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = command.accountId,
                createdByUserId = command.userId,
                direction = InvestmentTransactionDirection.INFLOW,
                type = InvestmentTransactionType.PURCHASE,
                quantity = row.quantity,
                grossValue = amount,
                netValue = amount,
                tradeDate = row.issueDate ?: LocalDate.now(),
                settlementDate = row.issueDate,
                source = InvestmentSource.FILE_IMPORT,
                providerTransactionId = providerCode,
                importJobId = command.jobId,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun computeAverageCost(
        principal: BigDecimal?,
        quantity: BigDecimal?,
        fallback: BigDecimal?,
    ): BigDecimal {
        if (principal == null || quantity == null || quantity <= BigDecimal.ZERO) {
            return fallback ?: BigDecimal.ZERO
        }
        return principal.divide(quantity, 8, RoundingMode.HALF_UP)
    }

    private fun mapIndexer(indexerLabel: String?): Indexer? {
        val normalized = indexerLabel?.trim()?.uppercase() ?: return null
        return when {
            normalized.contains("IPCA") -> Indexer.IPCA
            normalized.contains("CDI") -> Indexer.CDI
            normalized.contains("PREFIX") || normalized.contains("PRE") -> Indexer.PRE_FIXADO
            normalized.contains("SELIC") -> Indexer.SELIC
            normalized.contains("IGP") -> Indexer.IGP_M
            else -> null
        }
    }

    companion object {
        private val TAX_EXEMPT_TYPES = setOf("LCI", "LCA", "CRI", "CRA")
    }
}
