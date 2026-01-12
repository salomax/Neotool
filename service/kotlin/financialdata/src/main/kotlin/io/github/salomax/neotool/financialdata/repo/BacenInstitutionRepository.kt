package io.github.salomax.neotool.financialdata.repo

import io.github.salomax.neotool.financialdata.model.BacenInstitutionEntity
import io.github.salomax.neotool.financialdata.model.BacenInstitutionId
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface BacenInstitutionRepository : JpaRepository<BacenInstitutionEntity, BacenInstitutionId>
