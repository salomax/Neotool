package io.github.salomax.neotool.assets.startup

import io.github.salomax.neotool.assets.domain.AssetStatus
import io.github.salomax.neotool.assets.repository.AssetRepository
import io.github.salomax.neotool.common.startup.AbstractWarmupService
import jakarta.inject.Singleton
import java.util.UUID

@Singleton
class WarmupService(
    private val assetRepository: AssetRepository,
) : AbstractWarmupService() {
    override fun runWarmupQueries() {
        val dummyId = UUID(0, 0)
        assetRepository.findById(dummyId)
        assetRepository.findByStorageKey("__warmup__")
        assetRepository.findByOwnerIdAndStatus("__warmup__", AssetStatus.READY)
        assetRepository.findByIdIn(emptyList())
    }
}
