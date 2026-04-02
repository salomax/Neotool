package io.github.salomax.neotool.banking.model.import.nfce

import io.github.salomax.neotool.banking.domain.import.nfce.NfceDocument

object NfceDocumentMapper {
    fun toDomain(entity: NfceDocumentEntity): NfceDocument =
        NfceDocument(
            id = entity.id,
            transactionId = entity.transactionId,
            accountId = entity.accountId,
            accessKey = entity.accessKey,
            issuerCnpj = entity.issuerCnpj,
            issuerName = entity.issuerName,
            issuerTradeName = entity.issuerTradeName,
            issuerStateRegistration = entity.issuerStateRegistration,
            issuerAddress = entity.issuerAddress,
            issuerPostalCode = entity.issuerPostalCode,
            emissionDate = entity.emissionDate,
            totalValue = entity.totalValue,
            totalTax = entity.totalTax,
            buyerCpf = entity.buyerCpf,
            municipalityCode = entity.municipalityCode,
            municipality = entity.municipality,
            state = entity.state,
            countryCode = entity.countryCode,
            sourceData = entity.sourceData,
            createdAt = entity.createdAt,
        )

    fun toEntity(domain: NfceDocument): NfceDocumentEntity =
        NfceDocumentEntity(
            id = domain.id,
            transactionId = domain.transactionId,
            accountId = domain.accountId,
            accessKey = domain.accessKey,
            issuerCnpj = domain.issuerCnpj,
            issuerName = domain.issuerName,
            issuerTradeName = domain.issuerTradeName,
            issuerStateRegistration = domain.issuerStateRegistration,
            issuerAddress = domain.issuerAddress,
            issuerPostalCode = domain.issuerPostalCode,
            emissionDate = domain.emissionDate,
            totalValue = domain.totalValue,
            totalTax = domain.totalTax,
            buyerCpf = domain.buyerCpf,
            municipalityCode = domain.municipalityCode,
            municipality = domain.municipality,
            state = domain.state,
            countryCode = domain.countryCode,
            sourceData = domain.sourceData,
            createdAt = domain.createdAt,
        )
}
