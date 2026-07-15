# Scripts & Commands — kotlin-service

## Gradle (banking service)

Rodar a partir de `service/kotlin/`:

```bash
# Compilar
./gradlew :banking:build

# Unit tests (sem Docker, rápido)
./gradlew :banking:testUnit

# Integration tests (requer Docker)
./gradlew :banking:testIntegration

# Rodar todos os testes
./gradlew :banking:test

# Check completo (build + test + lint)
./gradlew :banking:check
```

## neotool

Consultar `invistus/neotool` para comandos adicionais disponíveis.
