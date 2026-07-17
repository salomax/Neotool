package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.model.dto.investment.ComeCotasInlineBackfillRequestedEvent
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Singleton
import mu.KotlinLogging

@Singleton
open class ComeCotasInlineBackfillRelay(
    private val checkpointService: ComeCotasCheckpointService,
) {
    private val logger = KotlinLogging.logger {}

    @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
    open fun onRequested(event: ComeCotasInlineBackfillRequestedEvent) {
        logger.info {
            "come-cotas inline relay accountId=${event.accountId} jobIds=${event.jobIds} " +
                "requestedAt=${event.requestedAt}"
        }
        event.jobIds.forEach(checkpointService::process)
    }
}
