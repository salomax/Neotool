package io.github.salomax.neotool.banking.repo.transaction

import io.github.salomax.neotool.banking.model.transaction.MerchantLogoCacheEntity
import io.micronaut.data.annotation.Repository
import io.micronaut.data.jpa.repository.JpaRepository

@Repository
interface MerchantLogoCacheEntityRepository : JpaRepository<MerchantLogoCacheEntity, String> {
    fun findByDomain(domain: String): MerchantLogoCacheEntity?

    fun findByDomainIn(domains: Collection<String>): List<MerchantLogoCacheEntity>
}
