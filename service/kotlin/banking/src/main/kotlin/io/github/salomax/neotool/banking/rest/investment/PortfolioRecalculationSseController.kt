package io.github.salomax.neotool.banking.rest.investment

import io.github.salomax.neotool.banking.domain.portfolio.PortfolioRecalculationStatus
import io.github.salomax.neotool.banking.model.dto.investment.PortfolioRecalculationStatusPayload
import io.github.salomax.neotool.banking.repo.portfolio.PortfolioRecalculationJobRepository
import io.github.salomax.neotool.banking.service.investment.event.PortfolioRecalculationEventBus
import io.github.salomax.neotool.banking.shared.BankingPermissions
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.sse.Event
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.UUID

private fun extractBearerToken(request: HttpRequest<*>): String? {
    val auth = request.headers.get("Authorization") ?: return null
    if (!auth.startsWith("Bearer ", ignoreCase = true)) return null
    return auth.substring(7).trim().takeIf { it.isNotBlank() }
}

@Controller("/investments/portfolio")
@Singleton
open class PortfolioRecalculationSseController(
    private val portfolioRecalculationEventBus: PortfolioRecalculationEventBus,
    private val portfolioRecalculationJobRepository: PortfolioRecalculationJobRepository,
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationChecker: AuthorizationChecker,
) {
    @Get("/{accountId}/recalculation-status/events", produces = [MediaType.TEXT_EVENT_STREAM])
    open fun recalculationStatusEvents(
        @PathVariable accountId: UUID,
        request: HttpRequest<*>,
    ): Flux<Event<PortfolioRecalculationStatusPayload>> {
        val token = extractBearerToken(request) ?: throw AuthenticationRequiredException("Authentication required")
        val principal: RequestPrincipal = requestPrincipalProvider.fromToken(token)
        authorizationChecker.require(principal, BankingPermissions.BANKING_ACCOUNT_VIEW)
        val currentAccountId = requireNotNull(principal.currentAccountId) { "No active account in token" }
        if (currentAccountId != accountId) {
            throw HttpStatusException(HttpStatus.FORBIDDEN, "Forbidden")
        }

        val current =
            portfolioRecalculationJobRepository.findActiveByAccount(accountId)
                ?: portfolioRecalculationJobRepository.findLatestByAccount(accountId)
                ?: return Flux.empty()

        val currentPayload = PortfolioRecalculationStatusPayload.from(current)
        if (current.status.isTerminal()) {
            return Flux.just(Event.of(currentPayload))
        }

        val busEvents =
            portfolioRecalculationEventBus.asFlux()
                .filter { it.accountId == accountId }

        val terminalPoll =
            Flux.interval(Duration.ofSeconds(2))
                .map { portfolioRecalculationJobRepository.findLatestByAccount(accountId) }
                .filter { it != null }
                .map { PortfolioRecalculationStatusPayload.from(it!!) }
                .filter { it.status.isTerminal() }

        return Flux.merge(busEvents, terminalPoll)
            .startWith(currentPayload)
            .distinctUntilChanged()
            .map { Event.of(it) }
            .takeUntil { it.data.status.isTerminal() }
            .timeout(Duration.ofMinutes(30))
    }

    private fun PortfolioRecalculationStatus.isTerminal(): Boolean =
        this == PortfolioRecalculationStatus.COMPLETED || this == PortfolioRecalculationStatus.FAILED
}
