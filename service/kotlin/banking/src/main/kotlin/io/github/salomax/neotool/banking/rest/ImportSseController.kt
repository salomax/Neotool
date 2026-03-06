package io.github.salomax.neotool.banking.rest

import io.github.salomax.neotool.banking.domain.import.ImportJobService
import io.github.salomax.neotool.banking.domain.importevent.ImportEventBus
import io.github.salomax.neotool.banking.domain.importevent.ImportJobStatusPayload
import io.github.salomax.neotool.banking.domain.rbac.BankingPermissions
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.sse.Event
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import java.time.Duration
import java.util.NoSuchElementException
import java.util.UUID

private fun extractBearerToken(request: HttpRequest<*>): String? {
    val auth = request.headers.get("Authorization") ?: return null
    if (!auth.startsWith("Bearer ", ignoreCase = true)) return null
    return auth.substring(7).trim().takeIf { it.isNotBlank() }
}

@Controller("/imports")
@Singleton
open class ImportSseController(
    private val importEventBus: ImportEventBus,
    private val importJobService: ImportJobService,
    private val requestPrincipalProvider: RequestPrincipalProvider,
    private val authorizationChecker: AuthorizationChecker,
) {
    @Get("/{jobId}/events", produces = [MediaType.TEXT_EVENT_STREAM])
    open fun importJobEvents(
        @PathVariable jobId: UUID,
        request: HttpRequest<*>,
    ): Flux<Event<ImportJobStatusPayload>> {
        val token =
            extractBearerToken(request)
                ?: throw io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException(
                    "Authentication required",
                )
        val principal: RequestPrincipal = requestPrincipalProvider.fromToken(token)
        authorizationChecker.require(principal, BankingPermissions.BANKING_ACCOUNT_VIEW)
        val accountId = requireNotNull(principal.currentAccountId) { "No active account in token" }

        val current =
            importJobService.getJob(jobId, accountId)
                ?: throw NoSuchElementException("Import job not found: $jobId")

        if (current.status.isTerminal()) {
            return Flux.just(Event.of(ImportJobStatusPayload.from(current)))
        }

        val busEvents =
            importEventBus.asFlux()
                .filter { it.jobId == jobId && it.accountId == accountId }

        // Poll terminal status as a safety net for race conditions where the terminal bus event
        // may be emitted before this subscriber is fully attached.
        val terminalPoll =
            Flux.interval(Duration.ofSeconds(2))
                .map { importJobService.getJob(jobId, accountId) }
                .filter { it != null }
                .map { ImportJobStatusPayload.from(it!!) }
                .filter { it.status.isTerminal() }

        return Flux.merge(busEvents, terminalPoll)
            .startWith(ImportJobStatusPayload.from(current))
            .distinctUntilChanged()
            .map { Event.of(it) }
            .takeUntil { it.data.status.isTerminal() }
            .timeout(Duration.ofMinutes(10))
    }
}
