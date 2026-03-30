package io.github.salomax.neotool.banking.service.pendingaction

import io.github.salomax.neotool.banking.domain.pendingaction.AccountPendingAction
import jakarta.inject.Singleton
import mu.KotlinLogging

/**
 * Publishes a pending action notification to the comms-service (GQL service-to-service).
 * Replace the stub below with the real GQL client call once the comms contract is defined.
 */
@Singleton
class PendingActionCommsService {
    private val logger = KotlinLogging.logger {}

    fun notify(action: AccountPendingAction) {
        // TODO: call comms-service via GQL client
        // Input: accountId, actionType, severity, entityType, entityId, payload
        logger.info { "TODO notify comms: action=${action.id} type=${action.type} account=${action.accountId}" }
    }
}
