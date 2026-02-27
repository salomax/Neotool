package io.github.salomax.neotool.banking.model.issuer

import io.github.salomax.neotool.banking.domain.issuer.Issuer

/**
 * Maps between [Issuer] domain and [IssuerEntity].
 */
object IssuerMapper {

    fun toDomain(entity: IssuerEntity): Issuer = Issuer(
        id = entity.id,
        name = entity.name,
        domain = entity.domain,
        compeCode = entity.compeCode,
        ispb = entity.ispb,
        logoKey = entity.logoKey,
        countryCode = entity.countryCode,
        isActive = entity.isActive,
    )
}
