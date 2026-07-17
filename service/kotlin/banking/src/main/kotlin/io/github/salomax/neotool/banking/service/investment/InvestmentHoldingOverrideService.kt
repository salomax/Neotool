package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.domain.investment.AssetType
import io.github.salomax.neotool.banking.domain.investment.Holding
import io.github.salomax.neotool.banking.repo.investment.AssetRepository
import io.github.salomax.neotool.banking.repo.investment.HoldingRepository
import io.github.salomax.neotool.common.error.CommonErrorCode
import io.github.salomax.neotool.common.error.ValidationException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
open class InvestmentHoldingOverrideService(
    private val holdingRepository: HoldingRepository,
    private val assetRepository: AssetRepository,
) {
    @Transactional
    open fun setComeCotasEnabled(
        holdingId: UUID,
        accountId: UUID,
        enabled: Boolean?,
    ): Holding {
        val holding =
            holdingRepository.findById(holdingId, accountId)
                ?: throw NoSuchElementException("Holding $holdingId not found")
        val asset =
            assetRepository.findById(holding.assetId)
                ?: throw NoSuchElementException("Asset ${holding.assetId} not found")
        if (asset.assetType != AssetType.MUTUAL_FUND) {
            throw ValidationException(
                CommonErrorCode.INVALID_INPUT,
                field = "holdingId",
                parameters = mapOf("reason" to "Come-cotas override is only valid for mutual funds"),
            )
        }
        return holdingRepository.update(
            holding.copy(
                comeCotasEnabled = enabled,
                updatedAt = Instant.now(),
            ),
        )
    }
}
