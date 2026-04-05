package io.github.salomax.neotool.banking.repo.portfolio

import io.github.salomax.neotool.banking.domain.portfolio.InvestmentAssetCorporateEvent

interface InvestmentAssetCorporateEventRepository {
    fun upsert(event: InvestmentAssetCorporateEvent): InvestmentAssetCorporateEvent
}
