package io.github.salomax.neotool.banking.job.transaction

import io.github.salomax.neotool.banking.service.transaction.MerchantLogoHydrationService
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton

@Singleton
@Requires(property = "banking.merchant-logo.backfill.enabled", value = "true")
open class MerchantLogoBackfillJob(
    private val merchantLogoHydrationService: MerchantLogoHydrationService,
    @Property(name = "banking.merchant-logo.backfill.batch-size", defaultValue = "25")
    private val batchSize: Int,
) {
    @Scheduled(fixedDelay = "\${banking.merchant-logo.backfill.fixed-delay:PT24H}")
    open fun run() {
        merchantLogoHydrationService.hydrateMissingLogos(batchSize)
    }
}
