# Scripts & Commands - kotlin-service

## Gradle

Rode a partir de `service/kotlin/`.

```bash
# Compilar banking
./gradlew :banking:compileKotlin

# Compilar testes
./gradlew :banking:compileTestKotlin

# Rodar todos os testes do banking
./gradlew :banking:test

# Rodar um teste específico
./gradlew :banking:test --tests "*TransactionServiceTest"

# Check completo do módulo
./gradlew :banking:check
```

## Raiz do Repositório

Na raiz, os scripts Nx agrupam checks de vários projetos. Consulte
`package.json` antes de rodar comandos globais porque eles podem acionar muitos
módulos.

## neotool

Consulte scripts `package.json` dos módulos e documentação em `README.md` para
comandos adicionais disponíveis. Não crie scripts novos para Agent Skills; esta
skill só documenta comandos existentes.
