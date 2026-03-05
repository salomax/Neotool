package io.github.salomax.neotool.financialdata.repo
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.model.FinancialInstitutionRatingHistoryEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import io.micronaut.data.model.Pageable
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface FinancialInstitutionRatingHistoryRepository : JpaRepository<FinancialInstitutionRatingHistoryEntity, UUID> {
    fun findByFinancialInstitutionIdOrderByChangedAtDesc(
        financialInstitutionId: UUID,
        pageable: Pageable,
    ): List<FinancialInstitutionRatingHistoryEntity>

    @io.micronaut.data.annotation.Query(
        "SELECT h.* FROM financial_data.financial_institution_rating_history h " +
            "WHERE h.id IN (" +
            "  SELECT id FROM (" +
            "    SELECT id, ROW_NUMBER() OVER (" +
            "      PARTITION BY financial_institution_id ORDER BY changed_at DESC" +
            "    ) AS rn " +
            "    FROM financial_data.financial_institution_rating_history " +
            "    WHERE financial_institution_id IN (:financialInstitutionIds)" +
            "  ) ranked " +
            "  WHERE ranked.rn <= :limit" +
            ")",
        nativeQuery = true,
    )
    fun findLatestByFinancialInstitutionIdIn(
        financialInstitutionIds: Collection<UUID>,
        limit: Int,
    ): List<FinancialInstitutionRatingHistoryEntity>
}
