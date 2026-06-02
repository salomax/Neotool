package io.github.salomax.neotool.banking.service.investmentimport

import io.github.salomax.neotool.banking.domain.account.FinancialAccount
import io.github.salomax.neotool.banking.domain.account.FinancialAccountType
import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportAccountCandidate
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportConflictDiff
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReview
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReviewInstitution
import io.github.salomax.neotool.banking.domain.investmentimport.InvestmentImportReviewRow
import io.github.salomax.neotool.banking.repo.account.FinancialAccountRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.banking.service.investmentimport.b3.B3RFRow
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class InvestmentImportReviewBuilder(
    private val financialAccountRepository: FinancialAccountRepository,
    private val holdingRepository: HoldingRepository,
) {
    fun build(
        rows: List<B3RFRow>,
        accountId: UUID,
    ): InvestmentImportReview {
        val investmentAccounts =
            financialAccountRepository.findByAccountId(accountId)
                .filter { it.type == FinancialAccountType.INVESTMENT }

        val rowsByInstitution =
            rows.groupBy { normalizeInstitutionKey(it.institutionName) }

        val institutions =
            rowsByInstitution.entries.mapIndexed { idx, (normalizedName, institutionRows) ->
                val readableName = institutionRows.firstOrNull()?.institutionName?.takeIf { it.isNotBlank() } ?: normalizedName
                val reviewInstitutionId = "inst-${idx + 1}"
                val accountMatch = resolveAccountMatch(normalizedName, investmentAccounts)
                val resolvedAccountId = if (accountMatch.ambiguous) null else accountMatch.matchedId

                val reviewRows =
                    institutionRows.map { row ->
                        buildReviewRow(row, resolvedAccountId)
                    }

                InvestmentImportReviewInstitution(
                    reviewInstitutionId = reviewInstitutionId,
                    institutionName = readableName,
                    matchedFinancialAccountId = accountMatch.matchedId,
                    matchAmbiguous = accountMatch.ambiguous,
                    matchCandidates = accountMatch.candidates,
                    rows = reviewRows,
                )
            }

        return InvestmentImportReview(
            source = "B3",
            institutions = institutions,
        )
    }

    private fun buildReviewRow(
        row: B3RFRow,
        financialAccountId: UUID?,
    ): InvestmentImportReviewRow {
        val assetTypeCode = extractAssetTypeCode(row.productName)
        val conflictingHolding =
            if (financialAccountId != null) {
                findConflictingHolding(financialAccountId, row, assetTypeCode)
            } else {
                null
            }

        return InvestmentImportReviewRow(
            rowIndex = row.rowIndex,
            productName = row.productName,
            assetTypeCode = assetTypeCode,
            issuerName = row.issuerName,
            providerCode = row.providerCode,
            indexerLabel = row.indexerLabel,
            issueDate = row.issueDate,
            maturityDate = row.maturityDate,
            quantity = row.quantity,
            principal = null,
            currentValue = row.currentValue,
            requiresPrincipal = true,
            conflictingHoldingId = conflictingHolding?.id,
            conflictDiff =
                conflictingHolding?.let {
                    InvestmentImportConflictDiff(
                        existingQuantity = it.quantity,
                        fileQuantity = row.quantity,
                        existingCurrentValue = it.grossValue ?: it.netValue,
                        fileCurrentValue = row.currentValue,
                        existingMaturityDate = it.maturityDate,
                        fileMaturityDate = row.maturityDate,
                    )
                },
        )
    }

    private fun findConflictingHolding(
        financialAccountId: UUID,
        row: B3RFRow,
        assetTypeCode: String,
    ): Holding? {
        if (!row.providerCode.isNullOrBlank()) {
            holdingRepository.findByProviderInvestmentId(financialAccountId, row.providerCode)?.let { return it }
        }

        val issuerName = row.issuerName ?: return null
        val maturityDate = row.maturityDate ?: return null
        return holdingRepository.findByAssetTypeAndIssuerNameAndMaturityDate(
            financialAccountId = financialAccountId,
            assetType = mapAssetType(assetTypeCode),
            issuerName = issuerName,
            maturityDate = maturityDate,
        )
    }

    private fun resolveAccountMatch(
        normalizedInstitutionName: String,
        accounts: List<FinancialAccount>,
    ): AccountMatchResult {
        if (normalizedInstitutionName == UNKNOWN_INSTITUTION) {
            return AccountMatchResult(null, ambiguous = false, candidates = emptyList())
        }

        val matches =
            accounts.filter { account ->
                val accountName = account.name.trim().uppercase()
                accountName.contains(normalizedInstitutionName) || normalizedInstitutionName.contains(accountName)
            }

        return when {
            matches.isEmpty() -> AccountMatchResult(null, ambiguous = false, candidates = emptyList())
            matches.size == 1 -> AccountMatchResult(matches.first().id, ambiguous = false, candidates = emptyList())
            else -> {
                val candidates =
                    matches.mapNotNull { account ->
                        account.id?.let { id ->
                            InvestmentImportAccountCandidate(
                                financialAccountId = id,
                                accountName = account.name,
                            )
                        }
                    }
                AccountMatchResult(null, ambiguous = true, candidates = candidates)
            }
        }
    }

    private data class AccountMatchResult(
        val matchedId: UUID?,
        val ambiguous: Boolean,
        val candidates: List<InvestmentImportAccountCandidate>,
    )

    companion object {
        private const val UNKNOWN_INSTITUTION = "__UNKNOWN__"

        private fun normalizeInstitutionKey(name: String?): String {
            val normalized = name?.trim()?.uppercase()
            return if (normalized.isNullOrBlank()) UNKNOWN_INSTITUTION else normalized
        }
    }
}

internal fun extractAssetTypeCode(productName: String): String {
    val candidate = productName.split(" - ", limit = 2).first().trim()
    return candidate.replace(" / ", "/").uppercase()
}

internal fun mapAssetType(assetTypeCode: String): AssetType =
    when (assetTypeCode) {
        "CDB" -> AssetType.CDB
        "RDB" -> AssetType.RDB
        "LCI" -> AssetType.LCI
        "LCA" -> AssetType.LCA
        "LC" -> AssetType.LC
        "LF" -> AssetType.LF
        "LIG" -> AssetType.LIG
        "DEBENTURE", "DEBENTURES" -> AssetType.DEBENTURES
        "CRI" -> AssetType.CRI
        "CRA" -> AssetType.CRA
        "TESOURO", "TREASURY" -> AssetType.TREASURY
        "COE" -> AssetType.COE
        else -> AssetType.OTHER
    }
