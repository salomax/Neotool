package io.github.salomax.neotool.security.repo

import io.github.salomax.neotool.security.model.preferences.UserPreferencesEntity
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserPreferencesRepository : JpaRepository<UserPreferencesEntity, UUID>
