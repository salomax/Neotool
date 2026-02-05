package io.github.salomax.neotool.financialdata.test.unit.service

import io.github.salomax.neotool.financialdata.domain.DataRestrictionReason
import io.github.salomax.neotool.financialdata.model.InstitutionDataRestrictionEntity
import io.github.salomax.neotool.financialdata.repo.InstitutionDataRestrictionRepository
import io.github.salomax.neotool.financialdata.service.InstitutionDataRestrictionService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InstitutionDataRestrictionServiceTest {
    private val restrictionRepository: InstitutionDataRestrictionRepository = mock()
    private val service = InstitutionDataRestrictionService(restrictionRepository)

    @Test
    fun `isRestricted returns NOT_RESTRICTED when no entity found`() {
        whenever(restrictionRepository.findByBacenCodInstAndIsActiveTrue(eq("001")))
            .thenReturn(null)

        val result = service.isRestricted("001")

        assertThat(result.isRestricted).isFalse()
        assertThat(result.reason).isNull()
    }

    @Test
    fun `isRestricted returns restricted with reason when entity found`() {
        whenever(restrictionRepository.findByBacenCodInstAndIsActiveTrue(eq("001")))
            .thenReturn(
                InstitutionDataRestrictionEntity(
                    bacenCodInst = "001",
                    reason = "INSTITUTION_REQUEST",
                    notes = null,
                    isActive = true,
                ),
            )

        val result = service.isRestricted("001")

        assertThat(result.isRestricted).isTrue()
        assertThat(result.reason).isEqualTo(DataRestrictionReason.INSTITUTION_REQUEST)
    }

    @Test
    fun `getRestrictionsForInstitutions returns empty map when no ids`() {
        val result = service.getRestrictionsForInstitutions(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `getRestrictionsForInstitutions returns only restricted institutions in map`() {
        whenever(restrictionRepository.findByBacenCodInstInAndIsActiveTrue(any()))
            .thenReturn(
                listOf(
                    InstitutionDataRestrictionEntity(
                        bacenCodInst = "001",
                        reason = "INSTITUTION_REQUEST",
                        notes = null,
                        isActive = true,
                    ),
                ),
            )

        val result = service.getRestrictionsForInstitutions(listOf("001", "002"))

        assertThat(result).hasSize(1)
        assertThat(result["001"]!!.isRestricted).isTrue()
        assertThat(result["001"]!!.reason).isEqualTo(DataRestrictionReason.INSTITUTION_REQUEST)
        assertThat(result["002"]).isNull()
    }
}
