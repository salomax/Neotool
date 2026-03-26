package io.github.salomax.neotool.banking.repo.preferences

import io.github.salomax.neotool.banking.model.preferences.AccountPreferencesEntity
import io.micronaut.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountPreferencesRepository : JpaRepository<AccountPreferencesEntity, UUID>
