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

## Fixtures que não provam nada

Um teste só cobre uma propriedade se **falharia com o bug presente**. O erro mais comum: fixtures que tornam dois valores distintos iguais por acaso, mascarando uma comparação no campo errado.

```kotlin
// ❌ Fixture mascara o bug: resource == clientId, então uma checagem
//    que compara o campo errado (aud vs client_id) ainda passa.
val subjectToken = issueToken(resource = "mcp", clientId = "mcp")
// ✅ Valores distintos para o que está sendo distinguido — o bug falha o teste.
val subjectToken = issueToken(resource = "https://mcp.invistus.test", clientId = "mcp")
```

Regra: para qualquer comparação de segurança (audience, tenant, permissão, propriedade), use valores **distintos** no fixture e mentalmente rode o teste contra o código bugado. Se ainda passa, o teste não cobre o que diz cobrir. Ao consertar um bug encontrado em review, primeiro ajuste o fixture para que o teste antigo **falhe**, depois aplique o fix.
