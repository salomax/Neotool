# Exemplos de Referência — kotlin-service

Features completamente implementadas que servem como referência canônica
para novos desenvolvimentos. Leia o código dessas features antes de implementar
algo similar.

## Credit Card Bills (referência primária)

Feature completa de fatura de cartão de crédito. Cobre todo o ciclo:
domain → infra → GraphQL → testes.

Arquivos-chave:
- Domain: `domain/transaction/CreditCardBill.kt`
- Domain interface: `domain/transaction/CreditCardBillRepository.kt`
- Service: `domain/transaction/CreditCardBillService.kt`
- Entity: `model/transaction/CreditCardBillEntity.kt`
- Mapper: `model/transaction/CreditCardBillMapper.kt`
- Repo (JPA): `repo/CreditCardBillEntityRepository.kt`
- Repo (impl): `repo/DefaultCreditCardBillRepository.kt`
- GraphQL DTO: `graphql/dto/CreditCardBillDTO.kt`
- Resolver: método em `graphql/resolver/TransactionResolver.kt`
- Migration: `V0_5__create_credit_card_bills.sql`

## Monthly Balance Snapshots

Feature de snapshot mensal. Mais complexa — inclui job agendado e healing.

Arquivos-chave:
- Domain: `domain/transaction/MonthlyBalanceSnapshot.kt`
- Service de leitura: `domain/transaction/MonthSummaryQueryService.kt`
- Service de cálculo: `domain/transaction/SnapshotComputationService.kt`
- Job: `infrastructure/snapshot/MonthlySnapshotSweeperJob.kt`
- Migration: `V0_11__create_monthly_balance_snapshots.sql`
