package io.github.salomax.neotool.banking.rest

import io.github.salomax.neotool.banking.domain.import.ImportJob
import io.github.salomax.neotool.banking.domain.import.ImportJobService
import io.github.salomax.neotool.banking.domain.import.ImportJobStatus
import io.github.salomax.neotool.banking.domain.import_.ImportEventBus
import io.github.salomax.neotool.banking.domain.import_.ImportJobStatusPayload
import io.github.salomax.neotool.common.security.authorization.AuthorizationChecker
import io.github.salomax.neotool.common.security.principal.PrincipalType
import io.github.salomax.neotool.common.security.principal.RequestPrincipal
import io.github.salomax.neotool.common.security.principal.RequestPrincipalProvider
import io.micronaut.http.HttpRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.test.StepVerifier
import reactor.test.scheduler.VirtualTimeScheduler
import java.time.Duration
import java.time.Instant
import java.util.NoSuchElementException
import java.util.UUID

@DisplayName("ImportSseController")
class ImportSseControllerTest {

    private lateinit var importEventBus: ImportEventBus
    private lateinit var importJobService: ImportJobService
    private lateinit var requestPrincipalProvider: RequestPrincipalProvider
    private lateinit var authorizationChecker: AuthorizationChecker
    private lateinit var controller: ImportSseController

    private lateinit var jobId: UUID
    private lateinit var accountId: UUID
    private lateinit var principal: RequestPrincipal
    private val now = Instant.now()

    @BeforeEach
    fun setUp() {
        importEventBus = ImportEventBus()
        importJobService = mock()
        requestPrincipalProvider = mock()
        authorizationChecker = mock()
        controller = ImportSseController(
            importEventBus = importEventBus,
            importJobService = importJobService,
            requestPrincipalProvider = requestPrincipalProvider,
            authorizationChecker = authorizationChecker,
        )
        jobId = UUID.randomUUID()
        accountId = UUID.randomUUID()
        principal = RequestPrincipal(
            principalType = PrincipalType.USER,
            userId = UUID.randomUUID(),
            serviceId = null,
            token = "test-token",
            permissionsFromToken = listOf("banking:account:view"),
            currentAccountId = accountId,
        )
    }

    private fun requestWithBearer(token: String): HttpRequest<*> =
        HttpRequest.GET<Any>("/imports/$jobId/events").header("Authorization", "Bearer $token")

    @Nested
    @DisplayName("importJobEvents")
    inner class ImportJobEventsTests {

        @Test
        fun `returns single event and completes when job is already terminal`() {
            val job = ImportJob(
                id = jobId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                createdByUserId = principal.userId!!,
                source = io.github.salomax.neotool.banking.domain.import.ImportSource.OFX,
                fileHash = "h",
                status = ImportJobStatus.COMPLETED,
                importedCount = 10,
                skippedCount = 0,
                duplicateReviewCount = 0,
                errorCount = 0,
                createdAt = now,
                updatedAt = now,
            )
            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId)).thenReturn(job)

            val flux = controller.importJobEvents(jobId, requestWithBearer("test-token"))

            StepVerifier.create(flux)
                .expectNextMatches { e -> e.data?.jobId == jobId && e.data?.status == ImportJobStatus.COMPLETED }
                .expectComplete()
                .verify()
            verify(importJobService).getJob(jobId, accountId)
        }

        @Test
        fun `streams events from bus until terminal when job not terminal`() {
            val job = ImportJob(
                id = jobId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                createdByUserId = principal.userId!!,
                source = io.github.salomax.neotool.banking.domain.import.ImportSource.OFX,
                fileHash = "h",
                status = ImportJobStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId)).thenReturn(job)

            val flux = controller.importJobEvents(jobId, requestWithBearer("test-token"))

            StepVerifier.create(flux)
                .then {
                    importEventBus.emit(ImportJobStatusPayload(jobId, accountId, ImportJobStatus.PROCESSING, null, null, null, null))
                    importEventBus.emit(ImportJobStatusPayload(jobId, accountId, ImportJobStatus.COMPLETED, 5, 1, 0, 0))
                }
                .expectNextMatches { e -> e.data.status == ImportJobStatus.PENDING }
                .expectNextMatches { e -> e.data.status == ImportJobStatus.PROCESSING }
                .expectNextMatches { e -> e.data.status == ImportJobStatus.COMPLETED }
                .expectComplete()
                .verify()
        }

        @Test
        fun `filters events by jobId and accountId so other account does not receive`() {
            val otherAccountId = UUID.randomUUID()
            val job = ImportJob(
                id = jobId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                createdByUserId = principal.userId!!,
                source = io.github.salomax.neotool.banking.domain.import.ImportSource.OFX,
                fileHash = "h",
                status = ImportJobStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId)).thenReturn(job)

            val flux = controller.importJobEvents(jobId, requestWithBearer("test-token"))

            StepVerifier.create(flux)
                .then {
                    importEventBus.emit(ImportJobStatusPayload(jobId, otherAccountId, ImportJobStatus.COMPLETED, 1, 0, 0, 0))
                    importEventBus.emit(ImportJobStatusPayload(jobId, accountId, ImportJobStatus.COMPLETED, 1, 0, 0, 0))
                }
                .expectNextMatches { e -> e.data.status == ImportJobStatus.PENDING && e.data.accountId == accountId }
                .expectNextMatches { e -> e.data.accountId == accountId }
                .expectComplete()
                .verify()
        }

        @Test
        fun `polls job status and emits terminal when bus event is missed`() {
            val pendingJob = ImportJob(
                id = jobId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                createdByUserId = principal.userId!!,
                source = io.github.salomax.neotool.banking.domain.import.ImportSource.OFX,
                fileHash = "h",
                status = ImportJobStatus.PENDING,
                createdAt = now,
                updatedAt = now,
            )
            val completedJob = pendingJob.copy(
                status = ImportJobStatus.COMPLETED,
                importedCount = 3,
                skippedCount = 0,
                duplicateReviewCount = 0,
                errorCount = 0,
                updatedAt = now.plusSeconds(5),
                completedAt = now.plusSeconds(5),
            )

            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId))
                .thenReturn(pendingJob, completedJob)

            VirtualTimeScheduler.getOrSet()
            StepVerifier.withVirtualTime {
                controller.importJobEvents(jobId, requestWithBearer("test-token"))
            }
                .expectSubscription()
                .expectNextMatches { e -> e.data.status == ImportJobStatus.PENDING }
                .thenAwait(Duration.ofSeconds(2))
                .expectNextMatches { e -> e.data.status == ImportJobStatus.COMPLETED }
                .expectComplete()
                .verify()
        }

        @Test
        fun `throws when job not found`() {
            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId)).thenReturn(null)

            assertThrows<NoSuchElementException> {
                controller.importJobEvents(jobId, requestWithBearer("test-token")).blockFirst()
            }
        }

        @Test
        fun `throws when no Authorization header`() {
            val request = HttpRequest.GET<Any>("/imports/$jobId/events")

            assertThrows<io.github.salomax.neotool.common.security.exception.AuthenticationRequiredException> {
                controller.importJobEvents(jobId, request).blockFirst()
            }
        }

        @Test
        fun `calls authorizationChecker with principal and BANKING_ACCOUNT_VIEW`() {
            val job = ImportJob(
                id = jobId,
                financialAccountId = UUID.randomUUID(),
                accountId = accountId,
                createdByUserId = principal.userId!!,
                source = io.github.salomax.neotool.banking.domain.import.ImportSource.OFX,
                fileHash = "h",
                status = ImportJobStatus.COMPLETED,
                createdAt = now,
                updatedAt = now,
            )
            whenever(requestPrincipalProvider.fromToken("test-token")).thenReturn(principal)
            whenever(importJobService.getJob(jobId, accountId)).thenReturn(job)

            controller.importJobEvents(jobId, requestWithBearer("test-token")).blockFirst()

            verify(authorizationChecker).require(eq(principal), eq(io.github.salomax.neotool.banking.domain.rbac.BankingPermissions.BANKING_ACCOUNT_VIEW))
        }
    }
}
