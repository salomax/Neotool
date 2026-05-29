---
name: kotlin-service
description: >
  Padrões de implementação para serviços Kotlin/Micronaut no Invistus.
  Use ao implementar backend: domain services, repositories, GraphQL
  resolvers, Flyway migrations, jobs ou testes. Aplica-se ao banking service
  e aos serviços Kotlin/Micronaut do projeto. Trigger: editar service/kotlin/,
  criar endpoints GraphQL, implementar regras de negócio ou escrever testes.
paths: service/kotlin/**
allowed-tools: Bash(./gradlew *)
---

# Kotlin Service Patterns - Invistus

Stack: **Kotlin + Micronaut + JPA + GraphQL + Flyway + PostgreSQL**.

Carregue os arquivos de referência conforme a tarefa:

- Implementando domain service, orchestration ou use case: leia [references/services.md](references/services.md)
- Implementando repository, query SQL, migration ou acesso a banco: leia [references/repo.md](references/repo.md)
- Alterando schema GraphQL, DTOs ou resolvers: leia [references/graphql.md](references/graphql.md)
- Escrevendo testes unitários ou de integração: leia [references/tests.md](references/tests.md)
- Procurando exemplos de features completas: leia [examples/README.md](examples/README.md)
- Procurando comandos Gradle/neotool disponíveis: leia [scripts/README.md](scripts/README.md)

## Regras Não Negociáveis

1. **Domain não depende de infraestrutura**: mantenha regras de negócio longe de JPA, HTTP e GraphQL.
2. **Entities não vazam para GraphQL**: use DTOs próprios em pacotes `graphql/*`.
3. **Micronaut, não Spring**: use `@Singleton`, `@Factory`, `@MicronautTest`; não introduza anotações Spring.
4. **UUIDs como IDs**: aggregates e entidades principais usam UUID como identificador.
5. **Soft delete**: queries de entidades com `deleted_at` sempre filtram registros removidos.
6. **Schema antes do resolver**: toda mudança GraphQL começa pelo schema e só depois vai para DTO/resolver/codegen.

## Estrutura de Pacotes

```text
io.github.salomax.neotool.banking
├── domain/        # modelos e interfaces de domínio
├── service/       # orchestration e casos de uso
├── model/         # entidades JPA, mappers e modelos de persistência
├── repo/          # repositories JPA e implementações
├── graphql/       # resolvers, DTOs e wiring GraphQL
├── client/        # adapters para serviços externos
├── job/           # consumers, scheduled jobs e workers internos
└── rest/          # endpoints REST quando existirem
```
