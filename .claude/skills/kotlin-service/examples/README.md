# Exemplos de Referência - kotlin-service

Features implementadas que servem como referência canônica para novos
desenvolvimentos. Leia o código dessas features antes de implementar algo
similar.

## Credit Card Bills

Feature de fatura de cartão de crédito. Cobre domínio, persistência, GraphQL e
testes.

Arquivos-chave:

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/CreditCardBill.kt`
- Service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/CreditCardBillService.kt`
- Entity: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/CreditCardBillEntity.kt`
- Mapper: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/CreditCardBillMapper.kt`
- Repository API/impl: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/CreditCardBillRepository.kt`
- JPA repository: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/CreditCardBillEntityRepository.kt`
- GraphQL DTO/resolver: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/`

## Monthly Balance Snapshots

Snapshot mensal. Mais complexo porque inclui recompute em cadeia, read healing e
sweeper job.

Arquivos-chave:

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/MonthlyBalanceSnapshot.kt`
- Read service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt`
- Computation service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/SnapshotComputationService.kt`
- Job: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/transaction/MonthlySnapshotSweeperJob.kt`
- Entity/repository: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/MonthlyBalanceSnapshotEntity.kt`

## Transactions

Fluxo central do Personal Finance.

Arquivos-chave:

- Domain: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/domain/transaction/Transaction.kt`
- Service: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/TransactionService.kt`
- Entity/mapper: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/model/transaction/`
- Repository: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/repo/transaction/DefaultTransactionRepository.kt`
- Resolver: `service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/graphql/transaction/TransactionResolver.kt`
