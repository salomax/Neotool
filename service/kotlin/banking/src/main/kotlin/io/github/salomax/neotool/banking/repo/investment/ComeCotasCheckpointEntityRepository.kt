package io.github.salomax.neotool.banking.repo.investment

import io.github.salomax.neotool.banking.model.investment.ComeCotasCheckpointEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
interface ComeCotasCheckpointEntityRepository : JpaRepository<ComeCotasCheckpointEntity, UUID> {
    @Query(
        """
        SELECT c FROM ComeCotasCheckpointEntity c
        WHERE c.holdingId = :holdingId
          AND c.checkpointDate = :checkpointDate
        """,
    )
    fun findByHoldingAndCheckpointDate(
        holdingId: UUID,
        checkpointDate: LocalDate,
    ): ComeCotasCheckpointEntity?

    @Query(
        """
        SELECT c FROM ComeCotasCheckpointEntity c
        WHERE c.holdingId = :holdingId
          AND c.checkpointDate >= :from
          AND c.checkpointDate <= :to
        ORDER BY c.checkpointDate ASC
        """,
    )
    fun findByHoldingAndDateRange(
        holdingId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<ComeCotasCheckpointEntity>

    @Query(
        """
        INSERT INTO banking.come_cotas_checkpoint (
            holding_id,
            checkpoint_date,
            computed_amount,
            withheld_amount,
            created_at,
            updated_at
        ) VALUES (
            :holdingId,
            :checkpointDate,
            :computedAmount,
            :withheldAmount,
            NOW(),
            NOW()
        )
        ON CONFLICT (holding_id, checkpoint_date)
        DO UPDATE SET
            computed_amount = EXCLUDED.computed_amount,
            withheld_amount = EXCLUDED.withheld_amount,
            updated_at = NOW()
        """,
        nativeQuery = true,
    )
    fun upsert(
        holdingId: UUID,
        checkpointDate: LocalDate,
        computedAmount: BigDecimal,
        withheldAmount: BigDecimal,
    )
}
