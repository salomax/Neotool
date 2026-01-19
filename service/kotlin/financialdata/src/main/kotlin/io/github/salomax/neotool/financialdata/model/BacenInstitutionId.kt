package io.github.salomax.neotool.financialdata.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * Composite primary key for BacenInstitutionEntity.
 * Represents (bacen_cod_inst, ano_mes_ref) as the primary key.
 */
@Embeddable
data class BacenInstitutionId(
    @Column(name = "bacen_cod_inst", nullable = false)
    var bacenCodInst: String,
    @Column(name = "ano_mes_ref", nullable = false, length = 6)
    var anoMesRef: String,
) : Serializable {
    // No-arg constructor required by JPA
    constructor() : this("", "")
}
