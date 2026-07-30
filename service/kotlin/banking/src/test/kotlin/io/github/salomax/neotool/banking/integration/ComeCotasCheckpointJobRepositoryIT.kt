package io.github.salomax.neotool.banking.integration

import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpoint
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJob
import io.github.salomax.neotool.banking.domain.investment.ComeCotasCheckpointJobStatus
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointJobRepository
import io.github.salomax.neotool.banking.repo.investment.ComeCotasCheckpointRepository
import io.github.salomax.neotool.common.test.transaction.runTransaction
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@MicronautTest(startApplication = true)
@DisplayName("Come-cotas checkpoint job repository IT")
class ComeCotasCheckpointJobRepositoryIT : BankingPostgresIntegrationTest() {
    // Native ON CONFLICT behavior and uniqueness constraints require real Postgres coverage,
    // so the backlog's "unit" repository checks intentionally live in this integration-backed test.
    @Inject
    lateinit var repository: ComeCotasCheckpointJobRepository

    @Inject
    lateinit var checkpointRepository: ComeCotasCheckpointRepository

    @Inject
    lateinit var entityManager: EntityManager

    private lateinit var accountId: UUID
    private lateinit var userId: UUID
    private lateinit var financialAccountId: UUID
    private lateinit var assetId: UUID
    private lateinit var holdingId: UUID
    private lateinit var fundCnpj: String

    @BeforeEach
    fun insertFixtures() {
        accountId = UUID.randomUUID()
        userId = UUID.randomUUID()
        financialAccountId = UUID.randomUUID()
        assetId = UUID.randomUUID()
        holdingId = UUID.randomUUID()
        fundCnpj = assetId.toString().filter(Char::isDigit).padEnd(14, '0').take(14)

        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.financial_accounts (
                    id, account_id, created_by_user_id, type, subtype, name, currency_code, created_at, updated_at
                ) VALUES (
                    '$financialAccountId', '$accountId', '$userId', 'INVESTMENT', 'BROKERAGE', 'Come Cotas IT', 'BRL', NOW(), NOW()
                )
                """.trimIndent(),
            )
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id, name, asset_type, currency_code, fund_cnpj, created_at, updated_at
                ) VALUES (
                    '$assetId', 'Fund IT', 'MUTUAL_FUND', 'BRL', '$fundCnpj', NOW(), NOW()
                )
                """.trimIndent(),
            )
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_holdings (
                    id, financial_account_id, account_id, asset_id, quantity, average_cost, source, created_at, updated_at
                ) VALUES (
                    '$holdingId', '$financialAccountId', '$accountId', '$assetId', 100.00000000, 1.00000000, 'MANUAL', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `claim is atomic and pending jobs are ordered by checkpoint date`() {
        val earlierCheckpoint = LocalDate.of(2026, 5, 29)
        val laterCheckpoint = LocalDate.of(2026, 11, 30)
        val laterJob =
            ComeCotasCheckpointJob(
                holdingId = holdingId,
                financialAccountId = financialAccountId,
                accountId = accountId,
                checkpointDate = laterCheckpoint,
                status = ComeCotasCheckpointJobStatus.PENDING,
            )
        val earlierJob =
            laterJob.copy(
                checkpointDate = earlierCheckpoint,
            )

        assertThat(repository.saveIfAbsent(laterJob)).isTrue()
        assertThat(repository.saveIfAbsent(earlierJob)).isTrue()
        assertThat(repository.saveIfAbsent(earlierJob)).isFalse()

        val pendingJobs =
            entityManager.runTransaction {
                entityManager.clear()
                repository.findOldestPending(50)
                    .filter { it.holdingId == holdingId && it.accountId == accountId }
            }
        assertThat(pendingJobs.map { it.checkpointDate }).containsExactly(earlierCheckpoint, laterCheckpoint)

        val persisted = pendingJobs.first()
        val firstClaim =
            entityManager.runTransaction {
                repository.tryClaimJob(requireNotNull(persisted.id), "node-a")
            }
        val secondClaim =
            entityManager.runTransaction {
                repository.tryClaimJob(requireNotNull(persisted.id), "node-b")
            }

        assertThat(firstClaim).isNotNull
        assertThat(secondClaim).isNull()
    }

    @Test
    fun `releaseZombieJobs resets stale processing jobs`() {
        val saved =
            repository.save(
                ComeCotasCheckpointJob(
                    holdingId = holdingId,
                    financialAccountId = financialAccountId,
                    accountId = accountId,
                    checkpointDate = LocalDate.of(2026, 11, 30),
                    status = ComeCotasCheckpointJobStatus.PROCESSING,
                    processingNodeId = "node-a",
                    lastHeartbeatAt = Instant.parse("2026-01-01T00:00:00Z"),
                ),
            )

        val jobId = requireNotNull(saved.id)
        val released =
            entityManager.runTransaction {
                repository.releaseZombieJobs(Instant.parse("2026-01-02T00:00:00Z"))
            }
        val reloaded =
            entityManager.runTransaction {
                entityManager.clear()
                repository.findById(jobId)
            }

        assertThat(released).isEqualTo(1)
        assertThat(reloaded).isNotNull
        assertThat(reloaded!!.status).isEqualTo(ComeCotasCheckpointJobStatus.PENDING)
        assertThat(reloaded.processingNodeId).isNull()
        assertThat(reloaded.lastHeartbeatAt).isNull()
    }

    @Test
    fun `come-cotas checkpoint repository upsert is idempotent for the same holding and checkpoint date`() {
        val original =
            checkpointRepository.upsert(
                checkpoint(
                    checkpointDate = LocalDate.of(2026, 5, 29),
                    computedAmount = "10.2500",
                    withheldAmount = "9.7500",
                ),
            )
        val updated =
            checkpointRepository.upsert(
                checkpoint(
                    checkpointDate = LocalDate.of(2026, 5, 29),
                    computedAmount = "12.0000",
                    withheldAmount = "11.5000",
                ),
            )

        assertThat(updated.id).isEqualTo(original.id)
        assertThat(updated.computedAmount).isEqualByComparingTo("12.0000")
        assertThat(updated.withheldAmount).isEqualByComparingTo("11.5000")

        val checkpoints =
            checkpointRepository.findByHoldingAndDateRange(
                holdingId = holdingId,
                from = LocalDate.of(2026, 5, 1),
                to = LocalDate.of(2026, 5, 31),
            )

        assertThat(checkpoints).hasSize(1)
        assertThat(checkpoints.single().id).isEqualTo(original.id)
        assertThat(checkpoints.single().computedAmount).isEqualByComparingTo("12.0000")
    }

    @Test
    fun `come-cotas checkpoint repository range lookup returns only requested holding and inclusive bounds`() {
        checkpointRepository.upsert(checkpoint(checkpointDate = LocalDate.of(2026, 5, 29), computedAmount = "10.0000"))
        checkpointRepository.upsert(checkpoint(checkpointDate = LocalDate.of(2026, 11, 30), computedAmount = "20.0000"))
        checkpointRepository.upsert(checkpoint(checkpointDate = LocalDate.of(2027, 5, 31), computedAmount = "30.0000"))

        val otherHoldingId = UUID.randomUUID()
        val otherAssetId = UUID.randomUUID()
        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_assets (
                    id, name, asset_type, currency_code, created_at, updated_at
                ) VALUES (
                    '$otherAssetId', 'Other Checkpoint Fund', 'MUTUAL_FUND', 'BRL', NOW(), NOW()
                )
                """.trimIndent(),
            )
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.investment_holdings (
                    id, financial_account_id, account_id, asset_id, quantity, average_cost, source, created_at, updated_at
                ) VALUES (
                    '$otherHoldingId', '$financialAccountId', '$accountId', '$otherAssetId', 50.00000000, 1.00000000, 'MANUAL', NOW(), NOW()
                )
                """.trimIndent(),
            )
        }
        checkpointRepository.upsert(
            checkpoint(
                holdingId = otherHoldingId,
                checkpointDate = LocalDate.of(2026, 11, 30),
                computedAmount = "99.0000",
            ),
        )

        val checkpoints =
            checkpointRepository.findByHoldingAndDateRange(
                holdingId = holdingId,
                from = LocalDate.of(2026, 11, 30),
                to = LocalDate.of(2027, 5, 31),
            )

        assertThat(checkpoints.map { it.checkpointDate })
            .containsExactly(LocalDate.of(2026, 11, 30), LocalDate.of(2027, 5, 31))
        assertThat(checkpoints.map { it.holdingId }.distinct()).containsExactly(holdingId)
    }

    @Test
    @Tag("integration")
    fun `come-cotas checkpoint unique constraint rejects duplicate insert`() {
        openConnection().use { conn ->
            conn.createStatement().executeUpdate(
                """
                INSERT INTO banking.come_cotas_checkpoint (
                    id, holding_id, checkpoint_date, computed_amount, withheld_amount, created_at, updated_at
                ) VALUES (
                    '${UUID.randomUUID()}', '$holdingId', '2026-11-30', 12.3400, 12.3400, NOW(), NOW()
                )
                """.trimIndent(),
            )

            val error =
                assertThrows<SQLException> {
                    conn.createStatement().executeUpdate(
                        """
                        INSERT INTO banking.come_cotas_checkpoint (
                            id, holding_id, checkpoint_date, computed_amount, withheld_amount, created_at, updated_at
                        ) VALUES (
                            '${UUID.randomUUID()}', '$holdingId', '2026-11-30', 13.0000, 13.0000, NOW(), NOW()
                        )
                        """.trimIndent(),
                    )
                }

            assertThat(error.message).contains("uq_come_cotas_checkpoint_holding_date")
        }
    }

    private fun checkpoint(
        holdingId: UUID = this.holdingId,
        checkpointDate: LocalDate,
        computedAmount: String,
        withheldAmount: String = computedAmount,
    ) = ComeCotasCheckpoint(
        holdingId = holdingId,
        checkpointDate = checkpointDate,
        computedAmount = BigDecimal(computedAmount),
        withheldAmount = BigDecimal(withheldAmount),
    )

    private fun openConnection() =
        DriverManager.getConnection(
            getPgContainer().jdbcUrl,
            getPgContainer().username,
            getPgContainer().password,
        )
}
