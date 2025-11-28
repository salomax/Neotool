package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.rbac.GroupEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

@Repository
interface GroupRepository : JpaRepository<GroupEntity, UUID> {
    fun findByName(name: String): Optional<GroupEntity>
}
