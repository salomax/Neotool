package io.github.salomax.neotool.banking.model.transaction

import io.github.salomax.neotool.banking.domain.transaction.CreditCardBill
import java.math.BigDecimal

object CreditCardBillMapper {
    fun toDomain(entity: CreditCardBillEntity): CreditCardBill =
        CreditCardBill(
            id = entity.id,
            financialAccountId = entity.financialAccountId,
            accountId = entity.accountId,
            closingDate = entity.closingDate,
            dueDate = entity.dueDate,
            totalAmount = entity.totalAmount ?: BigDecimal.ZERO,
            minimumPayment = entity.minimumPayment,
            status = entity.status,
            providerBillId = entity.providerBillId,
            deletedAt = entity.deletedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
}
