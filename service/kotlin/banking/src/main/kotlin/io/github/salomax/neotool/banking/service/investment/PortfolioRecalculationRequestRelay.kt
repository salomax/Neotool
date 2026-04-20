package io.github.salomax.neotool.banking.service.investment

import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedEvent as KafkaPortfolioRecalculationRequestedEvent
import io.github.salomax.neotool.banking.client.investment.messaging.PortfolioRecalculationRequestedProducer
import io.github.salomax.neotool.banking.model.dto.investment.PortfolioRecalculationRequestedEvent
import io.micronaut.context.annotation.Requires
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Singleton

@Singleton
@Requires(property = "kafka.enabled", value = "true")
open class PortfolioRecalculationRequestRelay(
    private val producer: PortfolioRecalculationRequestedProducer,
) {
    @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
    open fun onRequested(event: PortfolioRecalculationRequestedEvent) {
        producer.publish(
            KafkaPortfolioRecalculationRequestedEvent(
                jobId = event.jobId,
                accountId = event.accountId,
                financialAccountId = event.financialAccountId,
                recalculateFrom = event.recalculateFrom,
                triggeredBy = event.triggeredBy,
                requestedAt = event.requestedAt,
            ),
        )
    }
}
