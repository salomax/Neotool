package io.github.salomax.neotool.financialdata.repo
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.financialdata.model.RatingIssuerEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface RatingIssuerRepository : JpaRepository<RatingIssuerEntity, UUID> {
    fun findByEnabledTrue(): List<RatingIssuerEntity>

    fun findByIdIn(ids: Collection<UUID>): List<RatingIssuerEntity>
}
