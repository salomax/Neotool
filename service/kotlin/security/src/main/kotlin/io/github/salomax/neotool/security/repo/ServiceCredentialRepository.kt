package io.github.salomax.neotool.security.repo
import io.github.salomax.neotool.common.tracing.TraceExecution
import io.github.salomax.neotool.security.model.ServiceCredentialEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

@TraceExecution(layer = "repository")
@Repository
interface ServiceCredentialRepository : JpaRepository<ServiceCredentialEntity, UUID>
