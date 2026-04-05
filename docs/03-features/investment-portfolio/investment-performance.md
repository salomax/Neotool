# Investment Performance

Especificação técnica completa do sistema de cálculo de performance de portfólio de investimentos, cobrindo rendimento por portfólio, por classe de ativo, por ativo individual e por aporte — com suporte a comparação com benchmarks (CDI, IBOV, IPCA, IFIX) e histórico completo via backfill.

---

## Contexto e motivação

O `InvestmentSummary` exibe atualmente `grossGainPercent = grossGain / totalCostBasis` — um **Simple Return** que distorce o percentual quando há múltiplos aportes ao longo do tempo:

> Portfólio com R$10k a 10% de rentabilidade (R$11k). Novo aporte de R$50k → saldo R$61k.
> Simple Return: `(61k − 60k) / 60k = 1,6%` — mas a estratégia rendeu 10%.

Para ser comparável com CDI, IBOV e com o mercado em geral, é necessário o **TWR (Time-Weighted Return)**, que é o padrão da indústria (ANBIMA, fundos de investimento) e o método usado por Gorila, Kinvo, Monarch Money e Empower.

---

## Benchmark de mercado

| App | País | Método % | Comparação Benchmark |
|-----|------|----------|---------------------|
| Gorila | BR | TWR (cotização) + TIR opcional | CDI, IBOV, IPCA |
| Kinvo | BR | TWR (sistema de cotas) | CDI, IBOV, IFIX, IPCA |
| Monarch Money | US | TWR | Sim |
| Empower | US | TWR (Modified Dietz) | Sim |

---

## Conceitos fundamentais

### Simple Return (não adequado para portfólio)

```
% = (valor_atual − total_aportado) / total_aportado
```

Válido apenas para ativo individual com aporte único. Não usar como percentual consolidado do portfólio.
Ainda útil como **ganho absoluto em R$** (sempre correto e intuitivo).

### TWR — Time-Weighted Return (método principal)

Implementado via **sistema de cotas**, modelando o portfólio como um fundo fictício:

```
Cota inicial = R$ 1,00  (no dia do primeiro evento)

A cada dia de mercado:
  nova_cota = cota_anterior × (1 + retorno_diário)
  retorno_diário = (valor_fim − valor_início − fluxos_líquidos_do_dia) / valor_início

A cada aporte:
  novas_cotas = valor_aportado / cota_do_dia  (a cota não muda, só a quantidade)

A cada resgate:
  cotas_resgatadas = valor_resgatado / cota_do_dia

TWR de qualquer período:
  retorno = (cota_fim / cota_início) − 1
```

**Vantagem:** comparável com benchmarks. É o padrão ANBIMA/GIPS.
**Responde:** "qual foi a performance da minha estratégia de investimento?"

### XIRR — Money-Weighted Return (por ativo)

```
Encontra taxa r tal que:
NPV = Σ Ci / (1 + r)^ti = 0
onde Ci = fluxos de caixa (aportes negativos, resgates/dividendos positivos, valor atual positivo)
```

**Vantagem:** reflete o retorno real do dinheiro considerando o timing dos aportes.
**Responde:** "qual foi a rentabilidade anualizada deste ativo específico?"

### Por aporte — naturalmente derivado do sistema de cotas

Cada aporte compra um lote de cotas a um preço. O retorno daquele aporte é a valorização da cota desde então:

```
Aporte em Jan/24: 1.000 cotas @ R$1,00
Cota hoje: R$1,42

Rendimento do aporte Jan/24: (1,42 / 1,00) − 1 = +42%
Valor atual: 1.000 × R$1,42 = R$1.420
Ganho absoluto: R$420
```

Nenhuma tabela adicional — o `quota_value_at_event` no `PortfolioFlowEvent` já contém essa informação.

---

## Níveis de performance suportados

| Nível | Método | Responde |
|-------|--------|----------|
| Por portfólio | TWR (sistema de cotas) | "Minha carteira rendeu X% vs CDI" |
| Por classe de ativo | TWR (sub-portfólio por grupo) | "Renda Variável puxou ou segurou o portfólio?" |
| Por ativo individual | XIRR | "PETR4 rendeu X% a.a. — valeu o risco?" |
| Por aporte | Quota system (derivado) | "O aporte de jan/24 rendeu mais que o de jun/24?" |

---

## Arquitetura de dois layers

```
┌─────────────────────────────────────────────────────────┐
│  LAYER 1: Valor atual (on-read, sempre real-time)       │
│  Σ (holdings × cotação mais recente) + caixa            │
│  → Atualiza instantaneamente com qualquer mudança       │
│  → Já implementado no InvestmentQueryService            │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│  LAYER 2: Performance histórica (job assíncrono)        │
│  Quotas de T-1 para trás — imutáveis após fechamento    │
│  → MTD, YTD, 1 ano, 3 anos, desde o início             │
│  → Calculado por DailyQuotaJob e RecalculationJob       │
└─────────────────────────────────────────────────────────┘
```

O usuário **nunca fica sem o valor atual**. O que fica "calculando" após uma mudança é apenas a performance histórica (TWR vs benchmarks, gráfico de evolução, retorno por período).

---

## Dois jobs, responsabilidades distintas

### DailyQuotaJob

Roda todo dia após o fechamento do mercado (ex: 23h). Avança um dia com os preços de fechamento.

```
Para cada conta com holdings ativos:
  1. Buscar cotações do dia (investment_asset_daily_price)
  2. Calcular valor total da carteira (holdings × preço + caixa)
  3. Buscar PortfolioFlowEvents do dia (aportes, resgates, dividendos)
  4. Calcular retorno diário excluindo fluxos (Modified Dietz intraday)
  5. Atualizar quota_value e total_quotas para PORTFOLIO e cada ASSET_GROUP
  6. Salvar investment_portfolio_quota
  7. Marcar RecalculationJob pendente como não necessário se não houver

Fallback se cotação não disponível:
  - Renda Fixa: calcular pela taxa contratada (determinístico)
  - Ações/FIIs: usar última cotação disponível + marcar has_estimated_values=true
  - Cripto: idem
```

Frequência por tipo de ativo:

| Classe | Horário do job | Observação |
|--------|---------------|------------|
| Renda Fixa | 23h | Acréscimo diário determinístico |
| Fundos de Investimento | D+1 após ANBIMA | Cota publicada com defasagem |
| Ações / FIIs | Após 17h (fechamento B3) | Mercado fecha às 17h |
| Cripto | 23h59 UTC | Mercado 24/7, usar fechamento à meia-noite |

O `DailyQuotaJob` **não roda** para contas que já possuem um `RecalculationJob` com status `PENDING` ou `PROCESSING` — o recalculation engloba o dia atual quando executado.

### RecalculationJob

Disparado sempre que há uma mudança na carteira que afete valores históricos. Reconstrói as quotas desde a data mais antiga afetada.

**Dispara recalculation:**
- Nova posição cadastrada (qualquer data)
- Edição de quantidade, preço médio ou data de compra
- Deleção de posição
- Registro de dividendo, JCP ou provento
- Registro de aporte ou resgate
- Import de extrato de investimentos (B3, XP, etc.)

**NÃO dispara recalculation:**
- Alterar nome, observação ou tag
- Alterar corretora associada
- Alterar categoria customizada
- Mudanças em contas bancárias (sem holdings)

```
Trigger → RecalculationJob criado com status PENDING
  → Publicar PortfolioRecalculationRequestedEvent no Kafka
  → Consumer recebe:
      1. Atualiza status → PROCESSING, emite via EventBus → SSE frontend
      2. Determinar recalculate_from (data mais antiga afetada)
      3. Para cada dia de recalculate_from até hoje:
           a. Calcular valor de cada holding (holdings × preço do dia)
           b. Calcular caixa do dia
           c. Processar PortfolioFlowEvents do dia
           d. Recalcular quota_value e total_quotas (PORTFOLIO + ASSET_GROUPs)
           e. Upsert investment_portfolio_quota
      4. Atualizar status → COMPLETED, emite via EventBus → SSE frontend
      5. Commit offset Kafka
```

---

## Orquestração de dados de mercado

### Princípio: reutilizar o `financialdata`, não duplicar

O serviço `financialdata` já é a fonte de verdade de ativos, fundos e indicadores. Em vez de o `banking` criar jobs próprios que chamam B3, CVM e BACEN diretamente, a arquitetura correta é:

1. **`financialdata`** roda os jobs de ingestão e publica eventos Kafka por job concluído
2. **`banking`** consome esses eventos, sincroniza os dados via queries Relay bulk e mantém um checkpoint local
3. **`banking`** só roda o `DailyQuotaJob` quando `safe_valuation_date` confirma consistência de todas as fontes obrigatórias

Isso evita duplicação de lógica de ingestão, mantém os contratos de domínio centralizados e torna o pipeline auditável por ambos os serviços.

### O que já existe no `financialdata` (reutilizável)

| Contrato | Tipo | Uso |
|----------|------|-----|
| `investmentAssets(first, after)` | Relay connection | Catálogo/bootstrap de mapeamento de ativos |
| `investmentFunds(first, after)` | Relay connection | Catálogo/bootstrap de mapeamento de fundos |
| `investmentAssetsByTickers` | Snapshot query | Snapshot atual por ticker |
| `investmentFundMarketSnapshots` | Snapshot query | Snapshot atual de cotas de fundos |
| `indicatorsLatest` | Snapshot query | CDI, IPCA, etc. mais recentes |
| `ListedAsset.priceHistory(from, to, last)` | Por ativo | Útil para leitura individual, **não para sync bulk** |
| `InvestmentFund.priceHistory(from, to, last)` | Por fundo | Idem |
| `EconomicIndicatorVariation.series(range, limit)` | Por indicador | Idem |

Os contratos de `priceHistory` e `series` **não são adequados para sync de alto volume**: não são Relay, são centrados em um ativo por vez e não suportam partição por data. Continuam válidos como contratos de consulta para o usuário final.

### Gaps: novas queries Relay bulk no `financialdata`

Adicionar ao schema do `financialdata` quatro queries de sync com paginação Relay estável:

```graphql
# ─── Preços diários de ativos listados ───────────────────────────────────────
type Query {
  listedAssetDailyPrices(
    first: Int!
    after: String
    from: Date!
    to: Date!
    updatedSince: DateTime   # filtro opcional para replay/correção
  ): AssetDailyPriceConnection!

  # ─── NAV diário de fundos ─────────────────────────────────────────────────
  fundDailyNavs(
    first: Int!
    after: String
    from: Date!
    to: Date!
    updatedSince: DateTime
  ): FundDailyNavConnection!

  # ─── Pontos de indicadores econômicos ────────────────────────────────────
  economicIndicatorPoints(
    first: Int!
    after: String
    indicatorLabels: [String!]   # ex: ["CDI", "IPCA", "SELIC"] — null = todos
    variationLabels: [String!]   # ex: ["DAILY", "MONTHLY"] — null = todos
    from: Date!
    to: Date!
    updatedSince: DateTime
  ): EconomicIndicatorPointConnection!

  # ─── Eventos corporativos (dividendos, bonificações, splits) ─────────────
  assetCorporateEvents(
    first: Int!
    after: String
    from: Date!
    to: Date!
    updatedSince: DateTime
  ): AssetCorporateEventConnection!
}
```

**Regras de design das queries bulk:**
- Sempre `first` + `after` (cursor Relay estável)
- Cursor baseado em `(data_date ASC, chave_natural ASC)` — ordenação estável e determinística
- `updatedSince` para replay/correção sem reprocessar o período inteiro
- Resposta enxuta: só campos necessários para materialização no `banking`

### Shape dos tipos Connection/Edge/Node

```graphql
# ─── AssetDailyPrice ──────────────────────────────────────────────────────────
type AssetDailyPriceConnection {
  edges: [AssetDailyPriceEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}
type AssetDailyPriceEdge {
  node: AssetDailyPriceNode!
  cursor: String!
}
type AssetDailyPriceNode {
  ticker: String!           # ex: "PETR4"
  isin: String              # ex: "BRPETRACNPR6"
  priceDate: Date!
  closePrice: Decimal!
  currency: String!         # "BRL"
  adjustedForEvents: Boolean! # true se já incorpora splits/bonificações
  updatedAt: DateTime!
}

# ─── FundDailyNav ─────────────────────────────────────────────────────────────
type FundDailyNavConnection {
  edges: [FundDailyNavEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}
type FundDailyNavEdge {
  node: FundDailyNavNode!
  cursor: String!
}
type FundDailyNavNode {
  cnpj: String!             # identificador primário do fundo
  navDate: Date!            # data de referência da cota (D-1 em relação à publicação)
  navPerShare: Decimal!     # valor da cota
  totalNetAssets: Decimal   # PL do fundo — opcional, útil para filtros futuros
  updatedAt: DateTime!
}

# ─── EconomicIndicatorPoint ───────────────────────────────────────────────────
type EconomicIndicatorPointConnection {
  edges: [EconomicIndicatorPointEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}
type EconomicIndicatorPointEdge {
  node: EconomicIndicatorPointNode!
  cursor: String!
}
type EconomicIndicatorPointNode {
  indicatorLabel: String!   # "CDI" | "IPCA" | "SELIC" | "IGPM"
  variationLabel: String!   # "DAILY" | "MONTHLY" | "ANNUAL"
  referenceDate: Date!
  value: Decimal!           # taxa/variação no período
  dailyFactor: Decimal!     # fator diário composto: (1 + taxa_diária) — pronto para Π
  updatedAt: DateTime!
}

# ─── AssetCorporateEvent ──────────────────────────────────────────────────────
type AssetCorporateEventConnection {
  edges: [AssetCorporateEventEdge!]!
  pageInfo: PageInfo!
  totalCount: Int
}
type AssetCorporateEventEdge {
  node: AssetCorporateEventNode!
  cursor: String!
}
type AssetCorporateEventNode {
  ticker: String!
  isin: String
  eventType: CorporateEventType!  # DIVIDEND | INTEREST_ON_EQUITY | SPLIT | BONUS | SUBSCRIPTION
  exDate: Date!                   # data ex (quem tem na carteira nesta data recebe)
  paymentDate: Date               # data de pagamento (pode ser futura)
  amountPerShare: Decimal         # valor por ação/cota (null para splits)
  splitRatio: Decimal             # ex: 3.0 para split 1:3 (null para proventos)
  currency: String!
  updatedAt: DateTime!
}

# ─── PageInfo (padrão Relay — já deve existir no schema) ─────────────────────
type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}
```

### Kafka: eventos publicados pelo `financialdata`

Cada job do `financialdata` publica um evento simples ao concluir. O `banking` é o consumidor.

**Tópico:** `financialdata.market-data.job-completed`

```kotlin
@Serdeable
data class MarketDataJobCompletedEvent(
    val jobName: String,          // "b3_cotahist" | "cvm_funds_nav" | "indicators" | "b3_corporate_events"
    val runId: UUID,              // id único desta execução
    val fromDate: LocalDate,      // início do range de dados desta execução
    val toDate: LocalDate,        // fim do range (normalmente = lastDataDate para runs normais)
    val lastDataDate: LocalDate,  // data mais recente efetivamente disponível na fonte
    val status: String,           // "SUCCESS" | "PARTIAL" | "FAILED"
    val isBackfill: Boolean,      // true se foi execução de backfill histórico
    val lastRunAt: Instant,
    val publishedAt: Instant,
)
```

Jobs e suas características:

| `jobName` | Frequência típica | `lastDataDate` | Dados que publica |
|-----------|------------------|----------------|-------------------|
| `b3_cotahist` | Dias úteis, após 17h30 | D (dia do pregão) | Preços B3: ações, FIIs, opções |
| `cvm_funds_nav` | Dias úteis, manhã | D-1 (atraso CVM) | NAV diário de todos os fundos |
| `indicators` | Dias úteis, ~22h | D (CDI/Selic) ou mensal (IPCA) | Fatores diários de CDI, SELIC, IPCA, IGPM |
| `b3_corporate_events` | Dias úteis | D | Dividendos, JCP, splits, bonificações ex-date |

**Idealmente com outbox** no `financialdata` — o evento só é publicado após commit da transação de dados.

### Tabela `market_data_sync_control` no `banking`

Checkpoint local do `banking` — uma linha por `job_name`. Guarda o watermark do produtor e do consumidor, permitindo calcular o gap real e fazer retry seguro.

```sql
CREATE TABLE banking.market_data_sync_control (
    job_name                VARCHAR(50) PRIMARY KEY,  -- "b3_cotahist" | "cvm_funds_nav" | etc.

    -- Watermark do produtor (financialdata)
    source_last_data_date   DATE,          -- lastDataDate do último evento recebido
    source_last_run_at      TIMESTAMP,     -- lastRunAt do último evento recebido
    last_event_run_id       UUID,          -- runId do último evento processado (idempotência)

    -- Watermark do consumidor (banking)
    last_synced_date        DATE,          -- até que data o banking já materializou os dados
    last_sync_started_at    TIMESTAMP,
    last_sync_completed_at  TIMESTAMP,
    status                  VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                                           -- IDLE | SYNCING | COMPLETED | FAILED

    last_error              TEXT,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seeds obrigatórios (um por job esperado):
INSERT INTO banking.market_data_sync_control (job_name, status) VALUES
    ('b3_cotahist',        'IDLE'),
    ('cvm_funds_nav',      'IDLE'),
    ('indicators',         'IDLE'),
    ('b3_corporate_events','IDLE');
```

### Pipeline completo: do evento ao cálculo de quotas

```
financialdata                          banking
─────────────────                      ──────────────────────────────────────────
b3_cotahist job conclui
  → publica MarketDataJobCompletedEvent
    (jobName=b3_cotahist,
     lastDataDate=2025-04-03,
     status=SUCCESS)
                                       MarketDataSyncConsumer recebe evento:
                                         1. Idempotência: last_event_run_id já visto? → skip
                                         2. Atualiza market_data_sync_control:
                                            source_last_data_date = 2025-04-03
                                            source_last_run_at = now
                                         3. Calcula range a sincronizar:
                                            from = last_synced_date + 1
                                            to   = source_last_data_date
                                         4. Gap = 0? → encerra (nada a fazer)
                                         5. Status → SYNCING
                                         6. Busca dados via GraphQL Relay bulk:
                                            listedAssetDailyPrices(from, to, first=500)
                                            → pagina até hasNextPage = false
                                         7. Upsert batch em investment_asset_daily_price
                                            (ON CONFLICT (asset_id, price_date) DO UPDATE)
                                         8. Avança last_synced_date = to
                                         9. Status → COMPLETED
                                        10. Calcula safe_valuation_date
                                        11. Se safe_valuation_date > última quota processada:
                                            → dispara DailyQuotaJob para a data

cvm_funds_nav job conclui (D+1)
  → publica MarketDataJobCompletedEvent
    (jobName=cvm_funds_nav,
     lastDataDate=2025-04-02,   ← D-1
     status=SUCCESS)
                                       MarketDataSyncConsumer recebe:
                                         → mesmo fluxo: sync fundDailyNavs → upsert
                                         → recalcula safe_valuation_date
                                         → se avançou: dispara DailyQuotaJob
```

### `safe_valuation_date`: quando o banking pode calcular quotas

O `banking` só roda o `DailyQuotaJob` para uma data D quando **todos** os jobs obrigatórios têm `last_synced_date >= D`:

```kotlin
val MANDATORY_JOBS = setOf("b3_cotahist", "cvm_funds_nav", "indicators", "b3_corporate_events")

fun getSafeValuationDate(): LocalDate? {
    val controls = marketDataSyncControlRepository.findAll()
    val mandatoryControls = controls.filter { it.jobName in MANDATORY_JOBS }

    // Se algum job obrigatório nunca sincronizou: sem data segura
    if (mandatoryControls.any { it.lastSyncedDate == null }) return null

    // Data segura = mínimo entre todos os jobs obrigatórios
    return mandatoryControls.minOf { it.lastSyncedDate!! }
}
```

**Consequência natural:**
- Fundos têm `lastDataDate = D-1` → `safe_valuation_date` para D só avança quando `cvm_funds_nav` sincroniza D-1
- Isso é correto: quotas de D dependem das cotas de fundos de D, mas CVM publica D com atraso de 1 dia — então a quota de D do portfólio só pode ser calculada no dia D+1
- B3 e indicadores: disponíveis no mesmo dia D → não atrasam

**Para Renda Fixa pura** (sem ações, fundos ou cripto): `safe_valuation_date` pode ser D mesmo, pois não depende de `b3_cotahist` nem `cvm_funds_nav`. O serviço deve verificar quais jobs são *efetivamente necessários* para as asset classes presentes em cada conta — mas na V1, aplicar o critério conservador (mínimo de todos) é aceitável.

### Backfill: sem fluxo separado

O backfill histórico usa exatamente o mesmo pipeline. Quando o `financialdata` roda um job de backfill para um ano inteiro:

```
MarketDataJobCompletedEvent(
  jobName = "b3_cotahist",
  fromDate = 2024-01-01,
  toDate = 2024-12-31,
  lastDataDate = 2024-12-31,
  isBackfill = true,
)
```

O `MarketDataSyncConsumer` do `banking` recebe, calcula o range real (`last_synced_date + 1` até `lastDataDate`), pagina via Relay e faz upsert. O `RecalculationJob` de portfólio é disparado automaticamente ao final com `recalculate_from = from`.

### Regras de dependência por classe de ativo

```kotlin
enum class MarketDataJob {
    B3_COTAHIST,         // Ações, FIIs, opções
    CVM_FUNDS_NAV,       // Fundos de investimento
    INDICATORS,          // CDI, SELIC, IPCA, IGPM
    B3_CORPORATE_EVENTS, // Dividendos, splits — necessário para ajuste histórico
    INTERNAL,            // Renda Fixa — calculado internamente, sem job externo
}

val ASSET_GROUP_DATA_DEPS = mapOf(
    "fixedIncome"       to setOf(INTERNAL),
    "bankFixedIncome"   to setOf(INTERNAL),
    "treasuryDirect"    to setOf(INTERNAL, INDICATORS),    // para CDI/IPCA indexados
    "variableIncome"    to setOf(B3_COTAHIST, B3_CORPORATE_EVENTS),
    "realEstateFunds"   to setOf(B3_COTAHIST, B3_CORPORATE_EVENTS),
    "investmentFunds"   to setOf(CVM_FUNDS_NAV),
    "crypto"            to setOf(),                        // V1: sem fonte — estimated
    "cash"              to setOf(),
)

val BENCHMARK_DATA_DEPS = mapOf(
    "CDI"  to INDICATORS,
    "IBOV" to B3_COTAHIST,
    "IFIX" to B3_COTAHIST,
    "IPCA" to INDICATORS,
)
```

### Política de estimativas quando dado não está disponível

Quando o `safe_valuation_date` não avança (job em atraso ou com falha no `financialdata`), o `banking` **não roda o DailyQuotaJob** — ele aguarda. Isso é preferível a persistir quotas erradas.

Exceção: se o atraso ultrapassar o SLA configurado (ex: 48h para `cvm_funds_nav`), o `banking` usa o último valor disponível + `has_estimated_values = true`, e registra para reconciliar quando o dado real chegar via `updatedSince` replay.

---

## Kafka: tópicos e eventos

```kotlin
// Publicado pelo financialdata, consumido pelo banking
const val FINANCIALDATA_MARKET_DATA_JOB_COMPLETED = "financialdata.market-data.job-completed"

// Internos do banking
object PortfolioPerformanceTopics {
    const val RECALCULATION_REQUESTED     = "banking.portfolio.recalculation-requested"
    const val RECALCULATION_REQUESTED_DLT = "banking.portfolio.recalculation-requested.DLT"
    const val RECALCULATION_COMPLETED     = "banking.portfolio.recalculation-completed"
}
```

**MarketDataJobCompletedEvent** (publicado pelo `financialdata`):
```kotlin
@Serdeable
data class MarketDataJobCompletedEvent(
    val jobName: String,          // "b3_cotahist" | "cvm_funds_nav" | "indicators" | "b3_corporate_events"
    val runId: UUID,              // id único da execução — chave de idempotência no banking
    val fromDate: LocalDate,      // início do range de dados desta execução
    val toDate: LocalDate,
    val lastDataDate: LocalDate,  // data mais recente efetivamente disponível na fonte
    val status: String,           // "SUCCESS" | "PARTIAL" | "FAILED"
    val isBackfill: Boolean,
    val lastRunAt: Instant,
    val publishedAt: Instant,
)
```

**PortfolioRecalculationRequestedEvent:**
```kotlin
@Serdeable
data class PortfolioRecalculationRequestedEvent(
    val jobId: UUID,
    val financialAccountId: UUID?,   // null = consolidado
    val accountId: UUID,
    val recalculateFrom: LocalDate,  // data mais antiga afetada
    val triggeredBy: String,         // "holding_created", "import", "dividend", etc.
)
```

**PortfolioRecalculationCompletedEvent:**
```kotlin
@Serdeable
data class PortfolioRecalculationCompletedEvent(
    val jobId: UUID,
    val accountId: UUID,
    val status: PortfolioRecalculationStatus,
    val daysProcessed: Int,
    val errorDetails: String?,
)
```

---

## Banco de dados: novas tabelas

### `investment_asset_daily_price`

Cotação histórica diária por ativo. Fundação de todo o sistema de performance.

```sql
CREATE TABLE banking.investment_asset_daily_price (
    asset_id        UUID NOT NULL REFERENCES banking.investment_asset(id),
    price_date      DATE NOT NULL,
    close_price     NUMERIC(18, 8) NOT NULL,
    currency_code   VARCHAR(3) NOT NULL DEFAULT 'BRL',
    source          VARCHAR(50) NOT NULL,  -- B3, ANBIMA, COINGECKO, MANUAL, CALCULATED
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (asset_id, price_date)
);

CREATE INDEX idx_asset_daily_price_date ON banking.investment_asset_daily_price(price_date);
```

### `investment_portfolio_quota`

O coração do TWR — uma linha por conta × dimensão × dia.

```sql
CREATE TABLE banking.investment_portfolio_quota (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_account_id    UUID NOT NULL REFERENCES banking.financial_account(id),
    dimension_type          VARCHAR(20) NOT NULL,   -- PORTFOLIO | ASSET_GROUP
    dimension_key           VARCHAR(50),            -- null para PORTFOLIO; groupKey para ASSET_GROUP
    quota_date              DATE NOT NULL,
    quota_value             NUMERIC(18, 8) NOT NULL,
    total_quotas            NUMERIC(18, 8) NOT NULL,
    total_portfolio_value   NUMERIC(18, 2) NOT NULL,
    has_estimated_values    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (financial_account_id, dimension_type, dimension_key, quota_date)
);

CREATE INDEX idx_portfolio_quota_account_date
    ON banking.investment_portfolio_quota(financial_account_id, quota_date DESC);
CREATE INDEX idx_portfolio_quota_dimension
    ON banking.investment_portfolio_quota(financial_account_id, dimension_type, dimension_key, quota_date DESC);
```

### `investment_portfolio_flow_event`

Registra cada fluxo de caixa com o valor da cota no momento — base para retorno por aporte e para o cálculo correto do TWR.

```sql
CREATE TABLE banking.investment_portfolio_flow_event (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_account_id        UUID NOT NULL REFERENCES banking.financial_account(id),
    account_id                  UUID NOT NULL,
    event_date                  DATE NOT NULL,
    event_type                  VARCHAR(30) NOT NULL, -- DEPOSIT | WITHDRAWAL | DIVIDEND | INCOME_TAX | CORPORATE_ACTION
    gross_amount                NUMERIC(18, 2) NOT NULL,
    portfolio_value_before      NUMERIC(18, 2) NOT NULL,  -- valor da carteira antes do fluxo
    quota_value_at_event        NUMERIC(18, 8) NOT NULL,  -- cota no momento
    quotas_transacted           NUMERIC(18, 8) NOT NULL,  -- cotas compradas (positivo) ou resgatadas (negativo)
    related_holding_id          UUID REFERENCES banking.investment_holding(id),
    source_reference_id         UUID,                     -- id da transação ou import que originou o evento
    source_reference_type       VARCHAR(50),              -- HOLDING_PURCHASE | DIVIDEND | IMPORT | MANUAL
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolio_flow_account_date
    ON banking.investment_portfolio_flow_event(financial_account_id, event_date);
```

### `investment_portfolio_recalculation_job`

Controla o estado do RecalculationJob, análogo a `banking.document_import_jobs`.

```sql
CREATE TABLE banking.investment_portfolio_recalculation_job (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    financial_account_id    UUID REFERENCES banking.financial_account(id),  -- null = consolidado
    account_id              UUID NOT NULL,
    status                  VARCHAR(20) NOT NULL,  -- PENDING | PROCESSING | COMPLETED | FAILED
    recalculate_from        DATE NOT NULL,          -- data mais antiga afetada
    triggered_by            VARCHAR(100) NOT NULL,  -- "holding_created:uuid", "import:uuid", "market_data_ready:CVM_FUNDS", etc.
    days_processed          INT,
    error_details           TEXT,
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recalculation_job_account_status
    ON banking.investment_portfolio_recalculation_job(account_id, status, created_at DESC);
```

### `market_data_sync_control`

Checkpoint local do `banking` — uma linha por `job_name`, persistente. Guarda o watermark do produtor (`financialdata`) e do consumidor (`banking`), permitindo calcular o gap real, garantir idempotência e habilitar replay seguro via `updatedSince`.

```sql
CREATE TABLE banking.market_data_sync_control (
    job_name                VARCHAR(50) PRIMARY KEY,
                                           -- "b3_cotahist" | "cvm_funds_nav" | "indicators" | "b3_corporate_events"

    -- Watermark do produtor (financialdata)
    source_last_data_date   DATE,          -- lastDataDate do último evento recebido
    source_last_run_at      TIMESTAMP,     -- lastRunAt do último evento
    last_event_run_id       UUID,          -- runId do último evento (idempotência)

    -- Watermark do consumidor (banking)
    last_synced_date        DATE,          -- até que data o banking materializou os dados
    last_sync_started_at    TIMESTAMP,
    last_sync_completed_at  TIMESTAMP,
    status                  VARCHAR(20) NOT NULL DEFAULT 'IDLE',
                                           -- IDLE | SYNCING | COMPLETED | FAILED

    last_error              TEXT,
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seeds obrigatórios na migration — um por job esperado:
INSERT INTO banking.market_data_sync_control (job_name, status) VALUES
    ('b3_cotahist',         'IDLE'),
    ('cvm_funds_nav',       'IDLE'),
    ('indicators',          'IDLE'),
    ('b3_corporate_events', 'IDLE');
```

---

## Contrato GraphQL — extensão do InvestmentPortfolioSummary

```graphql
# Extensão do tipo existente
type InvestmentPortfolioSummary {
  # --- campos existentes mantidos ---
  totalHoldingsCount: Int!
  totalCostBasis: Float!
  totalGrossValue: Float!
  grossGain: Float!
  grossGainPercent: Float!          # simple return — mantido para ganho absoluto
  cashBalance: Float!
  totalPortfolioValue: Float!
  hasEstimatedValues: Boolean!
  valuationReferenceDate: Date
  currencyCode: String!
  byAssetGroup: [AssetGroupSummary!]!

  # --- novos campos de performance ---
  performance: PortfolioPerformance!
  recalculationStatus: RecalculationStatus!
}

type PortfolioPerformance {
  twr: Float                        # null se não há histórico suficiente (mín. 2 dias)
  twrSince: Date                    # data de início do cálculo
  twrAbsolute: Float                # ganho/perda em R$ no período selecionado
  period: PerformancePeriod!
  benchmarks: [BenchmarkPerformance!]!
  hasEstimatedValues: Boolean!      # true se alguma cotação do período é estimada
}

type BenchmarkPerformance {
  code: String!                     # CDI | IBOV | IPCA | IFIX
  label: String!                    # "CDI" | "Ibovespa" | "IPCA" | "IFIX"
  twr: Float!                       # retorno do benchmark no mesmo período
}

enum PerformancePeriod {
  MTD                               # mês atual (do dia 1 ao hoje)
  YTD                               # ano atual (do dia 1/1 ao hoje)
  ONE_YEAR                          # últimos 12 meses
  THREE_YEARS                       # últimos 36 meses
  ALL                               # desde o primeiro evento registrado
}

type RecalculationStatus {
  isCalculating: Boolean!
  jobId: UUID
  startedAt: DateTime
  message: String                   # "Recalculando histórico de performance..."
}

# Extensão do AssetGroupSummary existente
type AssetGroupSummary {
  # --- campos existentes mantidos ---
  groupKey: String!
  label: String!
  count: Int!
  totalCostBasis: Float!
  totalGrossValue: Float!
  grossGainPercent: Float
  allocationPercent: Float!
  isCash: Boolean!

  # --- novo campo de performance por grupo ---
  twr: Float                        # TWR do grupo no período selecionado
}

# Novo tipo para performance por ativo individual
type InvestmentHoldingPerformance {
  holdingId: ID!
  xirr: Float                       # taxa anualizada (XIRR); null se < 30 dias
  xirrSince: Date
  totalReturn: Float!               # retorno simples = (valor_atual + proventos) / custo − 1
  totalReturnAbsolute: Float!       # em R$
  dividendsReceived: Float!         # total de proventos recebidos
}

# Novo tipo para performance por aporte
type ContributionPerformance {
  flowEventId: ID!
  eventDate: Date!
  contributedAmount: Float!
  currentValue: Float!
  twr: Float!                       # (cota_atual / cota_na_data_do_aporte) − 1
  absoluteGain: Float!
}

# Extensão da Query
extend type Query {
  investmentPortfolioSummary(
    financialAccountId: ID,
    period: PerformancePeriod
  ): InvestmentPortfolioSummary!

  investmentHoldingPerformance(holdingId: ID!): InvestmentHoldingPerformance!

  investmentContributionPerformance(
    financialAccountId: ID,
    period: PerformancePeriod
  ): [ContributionPerformance!]!
}
```

---

## SSE: atualizações em tempo real

Seguindo exatamente o padrão de `ImportSseController` / `ImportEventBus`:

**Endpoint:**
```
GET /investments/portfolio/{accountId}/recalculation-status/events
Authorization: Bearer {token}
Content-Type: text/event-stream
```

**Payload:**
```kotlin
@Serdeable
data class PortfolioRecalculationStatusPayload(
    val jobId: UUID,
    val accountId: UUID,
    val financialAccountId: UUID?,
    val status: PortfolioRecalculationStatus,
    val daysProcessed: Int?,
    val errorDetails: String?,
)
```

**Fluxo SSE:**
1. Frontend abre conexão SSE imediatamente após gravar/alterar posição
2. SSE controller verifica status atual:
   - Se terminal (COMPLETED/FAILED): retorna imediatamente
   - Se PENDING/PROCESSING: assina `PortfolioRecalculationEventBus` + polling DB a cada 2s (fallback)
3. Stream de eventos até status terminal
4. Frontend atualiza a UI: `isCalculating: true` → skeletons no TWR/performance → `isCalculating: false` → dados exibidos

---

## UX: comunicação ao usuário

### Durante o recalculation

```
┌─────────────────────────────────────────────────────┐
│  Portfólio Total                                    │
│  R$ 87.432,10               ← valor atual (live)   │
│                                                     │
│  ⟳ Recalculando histórico de performance...         │
│    O rendimento e comparativos serão atualizados    │
│    em alguns instantes.                             │
│                                                     │
│  [████████░░░░░░░░]  Rendimento    [skeleton]       │
│  [████░░░░░░░░░░░░]  vs CDI        [skeleton]       │
│  [██████░░░░░░░░░░]  vs IBOV       [skeleton]       │
└─────────────────────────────────────────────────────┘
```

### Após conclusão

```
┌─────────────────────────────────────────────────────┐
│  Portfólio Total                                    │
│  R$ 87.432,10                                       │
│                                                     │
│  Rendimento (YTD)       +R$ 9.832,10    +12,7% ▲   │
│                                                     │
│  vs CDI (YTD)    +8,1%   ████████████              │
│  vs IBOV (YTD)   +7,4%   ██████████                │
│  Portfólio       +12,7%  ████████████████          │
│                                                     │
│  [MTD] [YTD*] [1 ano] [3 anos] [Desde o início]   │
└─────────────────────────────────────────────────────┘
```

### Seletor de período

O período selecionado afeta: TWR exibido, TWR dos grupos, benchmarks no mesmo período. O valor atual (Layer 1) não muda com o período.

---

## Backfill: posições com histórico anterior ao app

Quando uma posição é importada ou cadastrada com data anterior a hoje, o sistema **reconstrói o histórico** retroativamente usando cotações históricas disponíveis.

**Gatilho:** qualquer `PortfolioFlowEvent` com `event_date < today`.

**Comportamento:**
1. `RecalculationJob` criado com `recalculate_from = event_date`
2. Job processa todos os dias de `recalculate_from` até hoje
3. Para dias sem cotação disponível de ativos variáveis: usar última cotação + marcar `has_estimated_values = true`
4. Renda Fixa: sempre calculável retroativamente (taxa contratada × dias = determinístico)
5. Fundos: cotação ANBIMA disponível historicamente

**Resultado:** usuário vê performance desde a data real de compra, comparável com CDI do mesmo período.

---

## Dados de benchmark

### Tabela de cotações de benchmark

```sql
CREATE TABLE banking.market_benchmark_daily (
    benchmark_code  VARCHAR(10) NOT NULL,   -- CDI | IBOV | IPCA | IFIX
    price_date      DATE NOT NULL,
    daily_factor    NUMERIC(18, 10) NOT NULL, -- fator diário (1 + taxa_do_dia)
    cumulative_from NUMERIC(18, 8),           -- acumulado desde o início da série
    source          VARCHAR(50) NOT NULL,     -- BACEN | B3 | IBGE
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (benchmark_code, price_date)
);
```

**Fontes:**
- CDI: API BACEN (série 12)
- IBOV: B3 / Yahoo Finance / provider de dados
- IPCA: API BACEN / IBGE
- IFIX: B3

**TWR do benchmark para um período:**
```kotlin
fun getBenchmarkTwr(code: String, from: LocalDate, to: LocalDate): Double {
    return benchmarkRepository
        .findByCodeAndDateRange(code, from, to)
        .fold(1.0) { acc, row -> acc * row.dailyFactor } - 1.0
}
```

---

## Arquitetura de classes — novos componentes

```
banking/
├── domain/portfolio/
│   ├── PortfolioRecalculationStatus.kt    (enum: PENDING, PROCESSING, COMPLETED, FAILED)
│   ├── PortfolioRecalculationJob.kt       (domain model)
│   ├── PortfolioFlowEventType.kt          (enum: DEPOSIT, WITHDRAWAL, DIVIDEND, ...)
│   ├── MarketDataJob.kt                   (enum: B3_COTAHIST, CVM_FUNDS_NAV, INDICATORS, B3_CORPORATE_EVENTS, INTERNAL)
│   ├── MarketDataSyncControl.kt           (domain model — checkpoint por job_name)
│   └── AssetGroupDataDependencies.kt      (mapeamento AssetGroup → Set<MarketDataJob>)
│
├── model/portfolio/
│   ├── InvestmentPortfolioQuotaEntity.kt
│   ├── InvestmentPortfolioFlowEventEntity.kt
│   ├── InvestmentPortfolioRecalculationJobEntity.kt
│   ├── InvestmentAssetDailyPriceEntity.kt
│   ├── MarketBenchmarkDailyEntity.kt
│   └── MarketDataSyncControlEntity.kt
│
├── repo/portfolio/
│   ├── PortfolioQuotaRepository.kt
│   ├── PortfolioFlowEventRepository.kt
│   ├── PortfolioRecalculationJobRepository.kt
│   ├── AssetDailyPriceRepository.kt
│   ├── MarketBenchmarkDailyRepository.kt
│   └── MarketDataSyncControlRepository.kt
│
├── service/portfolio/
│   ├── PortfolioQuotaCalculationService.kt   (lógica TWR: calcula quota diária)
│   ├── PortfolioPerformanceQueryService.kt   (query TWR por período, benchmarks)
│   ├── PortfolioFlowEventService.kt          (registra flow events, dispara recalculation)
│   ├── PortfolioRecalculationEventBus.kt     (Reactor Sinks — SSE)
│   ├── SafeValuationDateService.kt           (calcula min(last_synced_date) dos jobs obrigatórios)
│   └── XirrCalculationService.kt            (XIRR por holding)
│
├── job/portfolio/
│   ├── DailyQuotaJob.kt                      (@Scheduled — dispara quando safe_valuation_date avança)
│   ├── MarketDataSyncConsumer.kt             (Kafka — consome MarketDataJobCompletedEvent do financialdata)
│   └── PortfolioRecalculationConsumer.kt     (Kafka — executa recalculation por conta)
│
├── client/
│   ├── financialdata/
│   │   ├── FinancialDataGraphQLClient.kt     (cliente GraphQL para o financialdata)
│   │   ├── queries/
│   │   │   ├── ListedAssetDailyPricesQuery.kt    (Relay bulk — listedAssetDailyPrices)
│   │   │   ├── FundDailyNavsQuery.kt             (Relay bulk — fundDailyNavs)
│   │   │   ├── EconomicIndicatorPointsQuery.kt   (Relay bulk — economicIndicatorPoints)
│   │   │   └── AssetCorporateEventsQuery.kt      (Relay bulk — assetCorporateEvents)
│   │   └── FinancialDataTopics.kt                (FINANCIALDATA_MARKET_DATA_JOB_COMPLETED)
│   └── portfolio/messaging/
│       ├── PortfolioPerformanceTopics.kt          (constantes dos tópicos internos)
│       ├── PortfolioRecalculationProducer.kt      (publica recalculation-requested)
│       └── PortfolioRecalculationEvents.kt        (data classes dos eventos internos)
│
├── rest/portfolio/
│   └── PortfolioPerformanceSseController.kt   (SSE endpoint)
│
└── graphql/investment/
    └── InvestmentPerformanceResolver.kt        (resolvers TWR, XIRR, contributions)
```

---

## Backlog de implementação

### Fase 1 — Infraestrutura de dados

**[PERF-BE-01] Migrations de banco de dados**

Criar as 6 novas tabelas na ordem de dependência:
1. `investment_asset_daily_price`
2. `market_benchmark_daily`
3. `market_data_sync_control`  ← inclui INSERT dos 4 seeds obrigatórios
4. `investment_portfolio_flow_event`
5. `investment_portfolio_quota`
6. `investment_portfolio_recalculation_job`

**Critério de aceite:**
- Migrations executam sem erro em ambiente limpo e em banco existente com dados
- Rollback de cada migration funciona independentemente
- Todos os índices e constraints criados conforme spec
- Seeds da `market_data_sync_control` presentes após migration: `b3_cotahist`, `cvm_funds_nav`, `indicators`, `b3_corporate_events` — todos com `status = 'IDLE'`

---

**[PERF-BE-02] Repositórios**

Implementar os 5 repositórios com as operações necessárias:

`PortfolioQuotaRepository`:
- `upsert(entity)` — insert or update por (account, dimension_type, dimension_key, date)
- `findLatestBefore(accountId, dimensionType, dimensionKey, date)` — última quota antes de uma data
- `findByAccountAndDateRange(accountId, dimensionType, dimensionKey, from, to)` — série para TWR
- `deleteFromDate(accountId, from)` — limpa quotas para recalculation

`PortfolioFlowEventRepository`:
- `save(entity)`
- `findByAccountAndDateRange(accountId, from, to)` — fluxos do período
- `findByAccountAndDate(accountId, date)` — fluxos de um dia específico

`PortfolioRecalculationJobRepository`:
- `save(entity)`, `update(entity)`
- `findById(id)`
- `findPendingByAccount(accountId)` — job pendente/processando
- `findLatestByAccount(accountId)` — estado atual para SSE

`AssetDailyPriceRepository`:
- `upsert(entity)`
- `findByAssetAndDate(assetId, date)` — preço exato
- `findLatestBefore(assetId, date)` — preço mais recente disponível
- `findByAssetAndDateRange(assetId, from, to)` — série histórica

`MarketBenchmarkDailyRepository`:
- `upsert(entity)`
- `findByCodeAndDateRange(code, from, to)` — série do benchmark

`MarketDataSyncControlRepository`:
- `findAll()` — todos os checkpoints (uma linha por job_name)
- `findByJobName(jobName)` — checkpoint de um job específico
- `update(entity)` — atualiza watermarks e status (nunca insert — seeds criados na migration)

**Critério de aceite:**
- Todas as operações de upsert são idempotentes
- `findLatestBefore` retorna null quando não há dado anterior (não lança exceção)
- `MarketDataSyncControlRepository.findAll()` sempre retorna exatamente 4 linhas (seeds da migration)
- Testes unitários com Testcontainers (PostgreSQL)

---

**[PERF-BE-03] Novas queries Relay bulk no `financialdata`**

Adicionar as 4 queries de sync ao schema GraphQL do `financialdata`, conforme shapes definidos na seção "Orquestração de dados de mercado".

Regras obrigatórias para cada query:
- Cursor baseado em `(data_date ASC, chave_natural ASC)` — estável e determinístico
- `updatedSince` opcional para replay sem reprocessar o período inteiro
- Reutilizar tipos de nó já existentes onde possível (`ListedAsset`, `InvestmentFund`)
- Resolver deve retornar resultados ordenados de forma idêntica independente de qual réplica de banco serve a query

**Critério de aceite:**
- `listedAssetDailyPrices(first: 500, from: "2025-01-01", to: "2025-01-31")` retorna exatamente todos os preços do período, paginável com cursor estável
- Cursor da última edge de uma página, passado como `after` na próxima, não retorna duplicatas nem pula registros
- `updatedSince` filtra corretamente: só retorna registros com `updated_at >= updatedSince`
- `totalCount` correto para o filtro aplicado (usado pelo banking para estimar progresso)
- `fundDailyNavs` retorna cotas identificadas por CNPJ (não apenas nome do fundo)
- `economicIndicatorPoints` retorna `dailyFactor` pré-calculado — o banking não precisa calcular `(1 + taxa)`
- `assetCorporateEvents` distingue claramente `DIVIDEND`, `INTEREST_ON_EQUITY`, `SPLIT`, `BONUS` no campo `eventType`

---

**[PERF-BE-04] MarketDataSyncConsumer no `banking`**

Kafka consumer que reage a `MarketDataJobCompletedEvent` publicado pelo `financialdata`. É o único ponto de entrada de dados de mercado no `banking`.

```kotlin
@KafkaListener(
    topics = [FINANCIALDATA_MARKET_DATA_JOB_COMPLETED],
    groupId = "banking-market-data-sync",
    properties = [Property(name = "max.poll.records", value = "1")],
)
fun consume(event: MarketDataJobCompletedEvent) {
    val control = marketDataSyncControlRepository.findByJobName(event.jobName)

    // Idempotência: runId já processado?
    if (control.lastEventRunId == event.runId) return

    // Calcular range efetivo a sincronizar
    val from = (control.lastSyncedDate?.plusDays(1)) ?: event.fromDate
    val to = event.lastDataDate
    if (!from.isBefore(to) && from != to) return  // nada novo

    // Atualizar watermark do produtor
    control.sourceLastDataDate = event.lastDataDate
    control.sourceLastRunAt = event.lastRunAt
    control.lastEventRunId = event.runId
    control.status = SYNCING
    marketDataSyncControlRepository.update(control)

    // Sync via GraphQL Relay — paginar até hasNextPage = false
    when (event.jobName) {
        "b3_cotahist"         -> syncListedAssetDailyPrices(from, to)
        "cvm_funds_nav"       -> syncFundDailyNavs(from, to)
        "indicators"          -> syncEconomicIndicatorPoints(from, to)
        "b3_corporate_events" -> syncAssetCorporateEvents(from, to)
    }

    // Avançar watermark do consumidor
    control.lastSyncedDate = to
    control.status = COMPLETED
    marketDataSyncControlRepository.update(control)

    // Recalcular safe_valuation_date e disparar DailyQuotaJob se avançou
    val safeDate = safeValuationDateService.calculate()
    if (safeDate != null && safeDate > lastProcessedQuotaDate()) {
        dailyQuotaJob.triggerFor(safeDate)
    }
}
```

**Upsert batch** em cada sync:
- `investment_asset_daily_price`: `ON CONFLICT (asset_id, price_date) DO UPDATE SET close_price = EXCLUDED.close_price, updated_at = NOW()`
- `market_benchmark_daily`: idem por `(benchmark_code, price_date)`
- Batch size: 500 registros por transação — mesma granularidade da página Relay

**Critério de aceite:**
- Consumer é idempotente: processar o mesmo `runId` duas vezes é no-op
- Processar evento com `lastDataDate` menor que `last_synced_date` é no-op (não regride watermark)
- Paginação: `first=500`, todas as páginas são consumidas antes de avançar `last_synced_date`
- Falha no meio da paginação: `last_synced_date` não avança — na próxima execução (mesmo evento ou replay), reinicia do mesmo ponto
- Upsert batch não falha por registros já existentes com mesmo valor (idempotente)
- `safe_valuation_date` é recalculado após cada sync completo bem-sucedido
- `DailyQuotaJob` só é disparado se `safeDate > lastProcessedQuotaDate` (não dispara desnecessariamente)

---

### Fase 2 — Cálculo de quotas e TWR

**[PERF-BE-05] PortfolioQuotaCalculationService**

Serviço core de cálculo de quota diária. Implementa:

```kotlin
fun calculateDayQuota(
    financialAccountId: UUID,
    accountId: UUID,
    date: LocalDate,
    previousQuota: PortfolioQuota?,           // null no primeiro dia
    holdingsValueByGroup: Map<String, Double>, // valor de mercado por grupo
    cashBalance: Double,
    flowEvents: List<PortfolioFlowEvent>,       // fluxos do dia
): List<PortfolioQuota>                         // PORTFOLIO + cada ASSET_GROUP
```

Lógica:
1. Se `previousQuota == null` (primeiro dia): cota inicial = 1,00; total_quotas = totalValue / 1,00
2. Calcular `portfolio_value_end = Σ holdings + caixa`
3. Calcular `net_flows = Σ flowEvents.grossAmount` (aportes positivo, resgates negativo)
4. `daily_return = (portfolio_value_end − portfolio_value_start − net_flows) / portfolio_value_start`
5. `new_quota_value = previous_quota_value × (1 + daily_return)`
6. Para aportes do dia: `new_quotas += aporte / new_quota_value`
7. Para resgates do dia: `new_quotas -= resgate / new_quota_value`
8. Repetir para cada ASSET_GROUP com seus respectivos valores

**Critério de aceite:**
- Teste: portfólio R$10.000 sem aporte, valoriza 1% → quota vai de R$1,00 para R$1,01
- Teste: portfólio R$10.000 a 10% → aporte de R$50.000 → quota permanece em R$1,10 (não cai)
- Teste: TWR calculado bate com CDI oficial para portfólio composto 100% de Tesouro Selic em período conhecido
- Teste: `Π(1 + daily_return)` para um período = `(quota_fim / quota_início) − 1`

---

**[PERF-BE-06] DailyQuotaJob e SafeValuationDateService**

O `DailyQuotaJob` **não é mais agendado por horário fixo**. Ele é disparado pelo `MarketDataSyncConsumer` sempre que o `safe_valuation_date` avança além da última data já processada em `investment_portfolio_quota`.

```kotlin
// Disparado pelo MarketDataSyncConsumer — não é @Scheduled
fun triggerFor(safeDate: LocalDate) {
    // Para cada conta com holdings ativos:
    //   1. Verificar se há RecalculationJob PENDING/PROCESSING → pular (ele já vai calcular)
    //   2. Buscar última quota processada para a conta
    //   3. Para cada dia entre (última quota + 1) e safeDate:
    //        a. Calcular valor dos holdings (investment_asset_daily_price — já sincronizado)
    //        b. Calcular caixa
    //        c. Buscar FlowEvents do dia
    //        d. Chamar PortfolioQuotaCalculationService.calculateDayQuota
    //        e. Upsert em investment_portfolio_quota
    //   4. Log estruturado ao final
}
```

`SafeValuationDateService`:
```kotlin
fun calculate(): LocalDate? {
    val controls = marketDataSyncControlRepository.findAll()
    val mandatory = controls.filter { it.jobName in MANDATORY_JOBS }
    if (mandatory.any { it.lastSyncedDate == null }) return null
    return mandatory.minOf { it.lastSyncedDate!! }
}
```

Para **Renda Fixa pura** (contas sem ações, FIIs ou fundos): o serviço pode avançar quotas para D mesmo antes de `b3_cotahist` e `cvm_funds_nav` completarem — pois esses jobs não são relevantes para aquela conta. Na V1, aplicar o critério conservador (`min` de todos os jobs) é aceitável.

**Critério de aceite:**
- Job NÃO possui `@Scheduled` — só é chamado por `MarketDataSyncConsumer.triggerFor(date)`
- Job é idempotente: calcular quotas para uma data já processada resulta em upsert sem mudança
- Contas com `RecalculationJob` PENDING ou PROCESSING são puladas (o recalculation cobre aquela data)
- Falha em uma conta não impede as demais (try/catch por conta)
- `safe_valuation_date` é sempre `min(last_synced_date)` dos 4 jobs obrigatórios
- `safe_valuation_date = null` quando qualquer job obrigatório nunca sincronizou
- Log estruturado: data processada, contas calculadas, contas puladas (recalculation pendente), contas com estimativas

---

**[PERF-BE-07] PortfolioPerformanceQueryService**

Serviço de consulta de performance para o GraphQL resolver:

```kotlin
fun getPortfolioPerformance(
    financialAccountId: UUID?,
    accountId: UUID,
    period: PerformancePeriod,
): PortfolioPerformanceDTO

fun getAssetGroupPerformance(
    financialAccountId: UUID?,
    accountId: UUID,
    groupKey: String,
    period: PerformancePeriod,
): Double?  // TWR do grupo; null se sem histórico

fun getContributionPerformance(
    financialAccountId: UUID?,
    accountId: UUID,
): List<ContributionPerformanceDTO>
```

**Critério de aceite:**
- TWR para período `ALL` = `(quota_última / quota_primeira) − 1`
- TWR para período `YTD` = `(quota_hoje / quota_31dez) − 1`
- Quando `twr = null` (sem histórico), campo retornado como `null` no GraphQL (não erro 500)
- Benchmark no mesmo período retorna `null` se dados não disponíveis (não erro)
- `ContributionPerformance` lista apenas aportes (DEPOSIT), não resgates

---

### Fase 3 — RecalculationJob e SSE

**[PERF-BE-08] PortfolioFlowEventService**

Ponto de entrada para registrar eventos de fluxo e disparar recalculation:

```kotlin
fun recordEvent(
    financialAccountId: UUID,
    accountId: UUID,
    eventDate: LocalDate,
    eventType: PortfolioFlowEventType,
    grossAmount: Double,
    relatedHoldingId: UUID?,
    sourceReferenceId: UUID?,
    sourceReferenceType: String?,
)
```

Após salvar o `PortfolioFlowEvent`:
1. Calcular `portfolio_value_before` (on-read no momento do registro)
2. Buscar `quota_value_at_event` da última quota disponível para `eventDate`
3. Calcular `quotas_transacted = grossAmount / quota_value_at_event`
4. Criar `RecalculationJob` com `recalculate_from = eventDate`
5. Publicar `PortfolioRecalculationRequestedEvent` no Kafka

**Este serviço deve ser chamado em:**
- `InvestmentHoldingService` ao criar/editar/deletar holding
- `InvestmentImportProcessor` ao confirmar import
- `DividendRegistrationService` ao registrar dividendo/JCP

**Critério de aceite:**
- Evento salvo antes da publicação Kafka (se Kafka falhar, evento não se perde)
- `RecalculationJob` criado com status `PENDING` sincronamente
- Se já existe um `RecalculationJob` PENDING para a conta, atualizar `recalculate_from` para o menor entre os dois (não criar duplicata)

---

**[PERF-BE-09] PortfolioRecalculationConsumer (Kafka)**

Seguindo o padrão de `ImportFileConsumer`:

```kotlin
@KafkaListener(
    groupId = "banking-portfolio-recalculation-consumer",
    offsetReset = OffsetReset.EARLIEST,
    properties = [
        Property(name = "enable.auto.commit", value = "false"),
        Property(name = "max.poll.records", value = "1"),
    ],
)
fun consume(event: PortfolioRecalculationRequestedEvent) {
    // 1. Buscar job, checar idempotência (já PROCESSING/COMPLETED para este jobId?)
    // 2. Atualizar status → PROCESSING, emitir via EventBus
    // 3. Limpar investment_portfolio_quota desde recalculate_from
    // 4. Para cada dia de recalculate_from até hoje:
    //      a. Buscar cotações do dia
    //      b. Calcular holdings × preço por grupo
    //      c. Buscar FlowEvents do dia
    //      d. Chamar PortfolioQuotaCalculationService.calculateDayQuota
    //      e. Salvar quota
    // 5. Atualizar status → COMPLETED, emitir via EventBus
    // 6. Publicar PortfolioRecalculationCompletedEvent
    // 7. Commit offset
    // Em caso de erro: status → FAILED, emitir via EventBus, commit offset
}
```

**Critério de aceite:**
- Consumer é idempotente: processar o mesmo `jobId` duas vezes não corrompe dados
- Progresso (days_processed) é atualizado a cada 30 dias processados (para históricos longos)
- Falha parcial: status FAILED, `error_details` contém a data que falhou e motivo
- DLT configurado: mensagem vai para `.DLT` após 3 tentativas falhas

---

**[PERF-BE-10] PortfolioRecalculationEventBus e SSE Controller**

Seguindo exatamente o padrão de `ImportEventBus` e `ImportSseController`:

**EventBus:**
```kotlin
@Singleton
class PortfolioRecalculationEventBus {
    private val sink = Sinks.many().multicast()
        .onBackpressureBuffer<PortfolioRecalculationStatusPayload>(256, false)

    fun emit(payload: PortfolioRecalculationStatusPayload) { ... }
    fun asFlux(): Flux<PortfolioRecalculationStatusPayload> = sink.asFlux()
}
```

**SSE Controller:**
```
GET /investments/portfolio/{accountId}/recalculation-status/events
Produces: text/event-stream
Auth: Bearer token + permissão BANKING_INVESTMENT_READ
Timeout: 10 minutos
Polling fallback: a cada 2s (race condition safety net)
```

**Critério de aceite:**
- SSE retorna status atual imediatamente ao conectar (não espera próximo evento)
- Stream encerra automaticamente quando status é terminal (COMPLETED ou FAILED)
- Desconexão do cliente não causa erro no servidor (Reactor cancellation)
- Funciona com múltiplos clientes conectados ao mesmo tempo para a mesma conta

---

### Fase 4 — GraphQL e Frontend

**[PERF-BE-11] InvestmentPerformanceResolver**

Extender `InvestmentResolver` com os novos resolvers:

- `investmentPortfolioSummary(financialAccountId, period)` — adicionar campo `performance` e `recalculationStatus`
- `investmentHoldingPerformance(holdingId)` — XIRR por holding
- `investmentContributionPerformance(financialAccountId, period)` — retorno por aporte

**Critério de aceite:**
- `performance.twr = null` quando não há quotas suficientes (não erro GraphQL)
- `recalculationStatus.isCalculating = true` quando existe job PENDING ou PROCESSING
- `investmentHoldingPerformance` retorna `xirr = null` para holdings com menos de 30 dias (XIRR instável)

---

**[PERF-UI-01] Seletor de período**

Componente `PerformancePeriodSelector` com opções MTD | YTD | 1 ano | 3 anos | Desde o início.
Período selecionado é passado como variável na query `InvestmentPortfolioSummary`.

**Critério de aceite:**
- Período padrão: YTD
- Seleção persiste na sessão (não reseta ao navegar entre contas)
- Período desabilitado quando TWR não disponível para aquele intervalo (ex: conta criada há 2 meses → 3 anos desabilitado)

---

**[PERF-UI-02] PerformanceMetricsSection no InvestmentSummary**

Substituir a linha de Rendimento atual pelos novos campos:

```
Rendimento (YTD)    +R$ 9.832,10    +12,7% ▲

vs CDI    +8,1%   ████████████░░░░░
Portfólio +12,7%  ████████████████
vs IBOV   +7,4%   ██████████░░░░░░
```

**Critério de aceite:**
- Quando `recalculationStatus.isCalculating = true`: mostrar spinner + skeletons no lugar dos valores de performance
- Quando `twr = null`: mostrar "—" com tooltip "Histórico insuficiente para calcular"
- Linha de benchmark só exibe se `benchmarks.length > 0`
- Cor verde/vermelho apenas no TWR do portfólio (não nos benchmarks)
- Valor de `grossGain` em R$ continua exibido (simple return absoluto)

---

**[PERF-UI-03] SSE no InvestmentSummary**

Abrir conexão SSE após qualquer mutação de holdings ou import:

```typescript
// Após mutação bem-sucedida
const { startListening } = usePortfolioRecalculationSSE(accountId)
onMutationSuccess(() => startListening())
```

**Critério de aceite:**
- SSE abre automaticamente após criar/editar/deletar holding
- SSE abre automaticamente após confirmar import de extrato de investimentos
- Quando COMPLETED: fechar SSE e refetch da query `InvestmentPortfolioSummary`
- Quando FAILED: exibir toast de erro com mensagem amigável

---

**[PERF-UI-04] Tela de detalhe do holding — XIRR**

Adicionar `investmentHoldingPerformance` na tela de detalhe de cada holding:

```
PETR4                    100 cotas @ R$47,50
Custo médio: R$38,50     Ganho: +R$900,00 (+23,4%)
TIR anualizada: +18,4% a.a.   ← novo
Desde: 15 mar 2023
```

**Critério de aceite:**
- XIRR exibido apenas se `xirr != null` (>= 30 dias de histórico)
- Tooltip explicando "Taxa Interna de Retorno anualizada considerando data e valor de cada aporte"

---

**[PERF-UI-05] Internacionalização — novos termos**

```
investments.performance.twr                    — "Rendimento da carteira"
investments.performance.twrSince               — "desde {date}"
investments.performance.period.mtd             — "Este mês"
investments.performance.period.ytd             — "Este ano"
investments.performance.period.oneYear         — "1 ano"
investments.performance.period.threeYears      — "3 anos"
investments.performance.period.all             — "Desde o início"
investments.performance.benchmark.vs           — "vs {label}"
investments.performance.recalculating          — "Recalculando histórico de performance..."
investments.performance.recalculatingDetail    — "O rendimento e comparativos serão atualizados em alguns instantes."
investments.performance.noHistory              — "Histórico insuficiente para calcular"
investments.performance.xirr                   — "TIR anualizada"
investments.performance.xirrTooltip            — "Taxa Interna de Retorno anualizada considerando data e valor de cada aporte"
investments.performance.recalculationFailed    — "Não foi possível recalcular o histórico. Tente novamente."
```

---

**[PERF-BE-12] Monitoramento e alertas do pipeline de dados**

Implementar visibilidade operacional sobre o estado do pipeline — essencial para detectar silenciosamente quotas com estimativas sem que o time perceba.

**Endpoints de health (interno):**
```
GET /internal/portfolio/daily-quota-runs?date=2025-04-03
  → status do DailyQuotaRun + fontes completas/pendentes/falhas

GET /internal/portfolio/market-data-ingestion?date=2025-04-03
  → status de cada MarketDataIngestionJob da data
```

**Alertas automáticos:**
- `DailyQuotaRun.status = PARTIAL` após 1h do dia seguinte → alerta operacional
- `market_data_ingestion_job.status = FAILED` após MAX_RETRIES → alerta operacional
- `accounts_with_estimates / accounts_processed > 10%` → alerta (muitas contas com dados estimados)

**Log estruturado obrigatório** em cada job de ingestão e no orchestrator:
```json
{
  "event": "market_data_ingestion_completed",
  "source": "B3_EQUITIES",
  "data_date": "2025-04-03",
  "records_ingested": 412,
  "duration_ms": 3200,
  "assets_not_found": ["XPTO3"]
}
```

**Critério de aceite:**
- Endpoint de health retorna 200 com estado atual sem autenticação de usuário (só IP interno)
- Log estruturado presente em todos os jobs de ingestão, orchestrator e recalculation consumer
- Campo `assets_not_found` lista ativos com holdings ativos mas sem cotação encontrada na fonte

---

### Fase 5 — Observabilidade e operação

**[PERF-OPS-01] Dashboard interno de saúde do pipeline**

Tela administrativa (acesso restrito) mostrando:
- Estado do `DailyQuotaRun` dos últimos 30 dias
- Status de cada fonte de dados por data
- Contas com `has_estimated_values = true` e qual ativo causou a estimativa
- Botão "Forçar recálculo" para um dia específico (dispara `RecalculationJob` para todas as contas)

**Critério de aceite:**
- Exibir claramente quais datas têm quotas estimadas vs. completas
- "Forçar recálculo" requer confirmação e registra quem disparou

---

## Fora do escopo — V1

- Gráfico de linha da evolução da cota ao longo do tempo (V2)
- Performance por período customizado (date picker) (V2)
- Comparação lado-a-lado de múltiplas contas (V2)
- Alertas quando portfólio supera/cai abaixo de benchmark (V2)
- Export CSV de histórico de performance (V2)
- XIRR consolidado do portfólio inteiro (V2 — após validação de uso do XIRR por holding)

---

## Referências

| Arquivo | Relevância |
|---------|-----------|
| [investment-summary.md](investment-summary.md) | Spec do InvestmentSummary — integrar os novos campos |
| [ImportFileConsumer.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/job/import/ImportFileConsumer.kt) | Padrão Kafka consumer a replicar |
| [ImportSseController.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/rest/import/ImportSseController.kt) | Padrão SSE a replicar |
| [ImportEventBus.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/import/ImportEventBus.kt) | Padrão EventBus a replicar |
| [ImportTopics.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/client/import/messaging/ImportTopics.kt) | Padrão de constantes de tópicos |
| [InvestmentQueryService.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/InvestmentQueryService.kt) | Valuation de holdings (Layer 1) |
