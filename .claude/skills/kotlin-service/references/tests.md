# Tests - Kotlin/Micronaut

## Stack de Testes

- JUnit 5 como framework base.
- Mockito-Kotlin para mocks (`mock()`, `whenever()`, `verify()`).
- AssertJ para assertions (`assertThat(...)`).
- `@MicronautTest` para testes que precisam do contexto Micronaut.
- Testcontainers/PostgreSQL para integração quando o fluxo passa pelo banco.

## Unit Tests

```kotlin
@MicronautTest
class SnapshotComputationServiceTest {

    @MockBean(MonthlyBalanceSnapshotRepository::class)
    fun snapshotRepository(): MonthlyBalanceSnapshotRepository = mock()

    @Inject
    lateinit var service: SnapshotComputationService

    @Test
    fun `should recompute closing balance correctly`() {
        whenever(snapshotRepository.findByMonth(any())).thenReturn(...)

        val result = service.recomputeSnapshot(...)

        assertThat(result.closingBalance).isEqualByComparingTo(BigDecimal("100.00"))
    }
}
```

## Integration Tests

- Use a base de integração existente do módulo quando o teste precisar de banco.
- Teste o fluxo completo quando a mudança envolve migration, repository native
  query, GraphQL ou side effects transacionais.
- Prefira unit tests quando a regra é pura e não depende de Micronaut/JPA.

## Comandos

```bash
# Rodar a partir de service/kotlin/
./gradlew :banking:test
./gradlew :banking:test --tests "*TransactionServiceTest"
./gradlew :banking:compileKotlin
./gradlew :banking:compileTestKotlin
```

## Gotchas

- `@MicronautTest` inicia contexto; não use para lógica pura se um unit test simples basta.
- Para substituir beans Micronaut, use `@MockBean`.
- Testes de integração com Docker podem ser mais lentos; rode seletivamente durante o loop.
