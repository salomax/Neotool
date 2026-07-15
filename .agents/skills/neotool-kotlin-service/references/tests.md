# Tests — Kotlin/Micronaut

## Stack de testes

- **JUnit 5** — framework base
- **Mockito-Kotlin** — mocks (`mock()`, `whenever()`, `verify()`)
- **AssertJ** — assertions (`assertThat(...).isEqualTo(...)`)
- **@MicronautTest** — injeção de dependências nos testes

## Unit tests

```kotlin
@MicronautTest
class SnapshotComputationServiceTest {

    @MockBean(MonthlyBalanceSnapshotRepository::class)
    fun snapshotRepository(): MonthlyBalanceSnapshotRepository = mock()

    @Inject
    lateinit var service: SnapshotComputationService

    @Test
    fun `should recompute closing balance correctly`() {
        // arrange
        whenever(snapshotRepository.findByMonth(any())).thenReturn(...)

        // act
        val result = service.recomputeSnapshot(...)

        // assert
        assertThat(result.closingBalance).isEqualByComparingTo(BigDecimal("100.00"))
    }
}
```

## Integration tests

- Estendem `BankingPostgresIntegrationTest`
- Requerem Docker (PostgreSQL sobe automaticamente via Testcontainers)
- Testam o fluxo completo: HTTP → service → banco

## Comandos

```bash
# Unit tests (sem Docker, rápido)
./gradlew :banking:testUnit

# Integration tests (requer Docker)
./gradlew :banking:testIntegration
```

## Gotchas

- Testes de integração são lentos — não rodar no loop de desenvolvimento
- `@MicronautTest` inicia o contexto completo — preferir unit tests com mocks quando possível
- Para mocks de `@Repository`, usar `@MockBean` (não `@Mock`)
