---
title: Tax Calculation Engine — Redesign Handoff
type: handoff
category: investment-portfolio
status: in-progress
version: 1.0.0
tags: [handoff, come-cotas, tax-engine, investment, portfolio, design]
last_updated: 2026-07-30
---

# Tax Calculation Engine — Redesign Handoff

> Contexto de uma investigação/discussão de design feita em Cowork (2026-07-29), migrando
> para Claude Code CLI para continuar (criação de GitHub Project/issues e implementação
> exigem `gh` CLI com token local, que Cowork não executa por política de segurança).
> Este arquivo é o ponto de partida para retomar sem perder o raciocínio já feito.

---

## 1. Origem do problema

Reconciliação de um fundo de investimento (renda fixa/multimercado) entre o extrato do banco
e o Invistus não batia. Aportes conferiam 100% (R$ 75.392,98), mas cotas, IR e valor
líquido/bruto divergiam.

## 2. Causa raiz encontrada (classificação tributária)

`FundTaxProfileClassifier.kt` (financialdata) decidia COME_COTAS_LONG (15%) vs
COME_COTAS_SHORT (20%) só checando se o nome do fundo continha a substring "CURTO PRAZO",
default LONG. Isso é regulatório (prazo médio da carteira ≤365 dias = curto/20%, >365 =
longo/15%), não uma questão de nomenclatura.

**Descoberta importante:** a CVM publica esse dado oficialmente no dataset
`registro_fundo_classe` (pós Resolução CVM 175), campo `Tributacao_Longo_Prazo` — o job de
ingestão atual (`cvm_funds_job`) só lia o `cad_fi.csv` legado, que não tem esse campo.

### Issues já criadas e mergeadas (confirmado via `git log`, branches `feat/GH-519-...` e `feat/GH-520-...`)

- **#519** — `feat(financialdata): persist raw rcvm175 fund fields` — ingestão expandida da
  CVM (dataset `registro_fundo_classe`, campos raw incluindo `tributacao_longo_prazo`,
  `patrimonio_liquido`, `custodiante`, `auditor`, `publico_alvo`). PR #525, mergeado.
- **#520** — `feat(financialdata): interpret raw CVM fund tax maturity` —
  `FundTaxProfileClassifier` passou a preferir o campo raw da CVM
  (`normalizedRawTaxMaturity == "S" -> COME_COTAS_LONG`, `"N" -> COME_COTAS_SHORT`) sobre o
  heurístico de nome. PR #527, mergeado.

**Pendência não resolvida:** nunca validamos o mapeamento S/N contra uma amostra real de
fundos conhecidos (um sabidamente curto prazo, outro longo prazo). Marcos decidiu adiar isso
("vamos corrigir os erros conhecidos depois faremos mais testes").

## 3. Bug descoberto ao validar o fix (idempotência)

Depois do #520 subir, os números pioraram em vez de melhorar: 2 dos 4 eventos históricos de
"Imposto retido" (Nov/24, Mai/25) desapareceram, e os 2 restantes (Nov/25, Mai/26) cresceram
4x-10x — muito além do que uma mudança de alíquota 15%→20% explicaria sozinha.

**Causa raiz:** `ComeCotasCheckpointService.compute()` chama
`ComeCotasSupport.estimateProjectedComeCotas()`, que reconstrói o ganho acumulado **desde a
data de compra original do lote** interpolando linearmente até o checkpoint atual (usando o
NAV do checkpoint atual, não o NAV histórico de cada checkpoint passado), e depois subtrai
`lot.withholdingPaid` (crédito mutável derivado de quais transações `WITHHOLDING_TAX`
**atualmente existem** no banco). Se um checkpoint intermediário perde seu registro de
retenção (por qualquer motivo — job falho, delete, reprocessamento fora de ordem), todo o
imposto daquele período "vaza" para o próximo checkpoint que rodar, com a alíquota **vigente
agora**, não a histórica.

Isso também explica o **IR = R$ 0,00** que apareceu na posição: `FundValuationService.estimate()`
usa a mesma função `estimateProjectedComeCotas()`. Para fundos SHORT, a alíquota final da
tabela regressiva acima de 180 dias (20%) é numericamente igual à alíquota de come-cotas
SHORT (20% flat) — então o residual colapsa para zero por construção. **Isso não é um bug
novo, é sintoma da mesma causa raiz.** Depois do fix, é esperado (e correto) que essa holding
continue mostrando IR ≈ 0, porque o come-cotas já recolheu tudo que seria devido.

### Issue de refactor (redigida, ainda não criada no GitHub)

Título: **[refactor] Make come-cotas checkpoint calculation idempotent and order-independent**

Conteúdo completo já escrito (ver transcript da conversa em Cowork de 2026-07-29 — pedir para
o usuário colar se necessário, ou recriar a partir da seção 2 acima). Pontos-chave do escopo:
- Cada checkpoint deve ser calculado com o NAV histórico daquele dia específico (já disponível
  via `FundDailyNav`/`getNavSeries`), não por interpolação linear a partir de uma data de
  referência posterior.
- Persistência do checkpoint deve ser upsert idempotente por `(holding_id, checkpoint_date)`.
- Reprocessar do zero (apagar todos os `WITHHOLDING_TAX` e re-rodar o sweep) deve sempre
  produzir o mesmo resultado, independente de ordem/retries/falhas parciais.
- Inclui também corrigir `FundValuationService.estimate()` (mesma causa raiz).
- **Não inclui** backfill dos dados já corrompidos dessa holding específica de teste —
  decisão explícita do Marcos: estamos em MVP, sem backfill agora.

### Issue de baixa prioridade (redigida, ainda não criada)

Título: **[feat] Configurable NAV reference offset for come-cotas gain calculation (D-x)**

Contexto: divergência conhecida de data-base do NAV (D0 vs D-1) entre Invistus e alguns
bancos/administradoras (residual pequeno, ~0,05-0,1 cota por checkpoint, já documentado
anteriormente no issue #408). Decisão do Marcos: manter D0 como padrão, adicionar campo
configurável de offset em dias úteis por holding/fundo para reconciliar com administradoras
que usam D-1. **Prioridade P3, bloqueada pelo refactor de idempotência** (deve nascer já no
design novo, não ser colado no mecanismo antigo).

## 4. Decisão de arquitetura: Motor de Cálculo de Tributos

Ao investigar, ficou claro que a duplicação de lógica tributária é o padrão real do
problema, não um bug isolado. Hoje existem pelo menos 3 implementações independentes da
mesma ideia de "tabela regressiva por regime":

- `common-finance/.../BrazilianFixedIncomeTaxRules.kt` — tabela de 4 faixas (22,5/20/17,5/15%)
  + IOF de 30 dias. **Esta parte está correta e bem testada — deve ser a semente do motor
  novo, não descartada.**
- `FundValuationService.computeFinalIr()` — reimplementa uma tabela de 2 faixas na mão para
  SHORT, delega para `BrazilianFixedIncomeTaxRules` só para LONG.
- `ComeCotasSupport.estimateProjectedComeCotas()` — mais uma tabela própria (15%/20% fixos),
  sem relação de código com as outras duas.

**Decisão (Marcos, 2026-07-29):** parar e redesenhar agora, enquanto estamos em MVP (momento
mais barato — sem dados de produção para migrar, sem backfill sendo feito de qualquer forma).
Não é uma issue única, é um projeto com múltiplas issues.

### Requisitos de design acordados

1. **Extensibilidade para qualquer tipo de asset é objetivo explícito e estratégico**
   ("divisor de águas", vantagem competitiva) — mas **sem implementar** classes de ativo que
   não existem hoje no produto (cripto, REITs). O motor deve ser desenhado para que adicionar
   um novo regime seja extensão (nova implementação), não modificação do core.
2. **Design pattern recomendado: Strategy** para o regime tributário — interface comum
   (ex.: `TaxRegime`) com algo como `periodicWithholdingRate(elapsedDays): BigDecimal?`
   (nullable — nem todo asset tem antecipação estilo come-cotas) e
   `finalRate(elapsedDays): BigDecimal`. Um registry/factory resolve qual regime se aplica a
   um asset/holding, substituindo os `when` espalhados hoje.
3. **O core do motor deve ser função pura**: `compute(lots, regime, dataReferência,
   provedorDeNav) -> resultado`, sem estado mutável equivalente ao `lot.withholdingPaid`
   atual. Isso resolve idempotência e extensibilidade ao mesmo tempo — testar "o motor está
   correto" (idempotente, sem duplicar) fica independente de testar "a regra brasileira X está
   correta" (pode-se testar o motor com uma strategy fake).
4. **Evitar over-engineering**: não introduzir padrões adicionais (ex. Chain of
   Responsibility) só por serem "consagrados" — a composição ganho → IOF → base tributável →
   IR já é uma função simples; Strategy + orquestração pura já resolve.

### Decomposição proposta (ainda não formalizada em issues individuais)

1. Definir abstrações centrais (`TaxRegime`, lote/evento tributável, motor puro).
2. Portar os regimes existentes (LONG, SHORT, VARIABLE_INCOME_15, renda fixa taxable/exempt)
   para as novas abstrações, reaproveitando `BrazilianFixedIncomeTaxRules`.
3. Reconstruir `ComeCotasCheckpointService`, `FundValuationService` e `TaxPolicyService` como
   consumidores finos do motor (idempotência nasce aqui, estruturalmente).
4. Suíte de testes por regime + testes de propriedade do motor (idempotência, ordem, replay)
   usando uma strategy fake, desacoplados das regras brasileiras específicas.
5. Só então reprocessar a holding de teste e validar contra o banco novamente.

Próximo passo sugerido (ainda não executado): rodar `/neotool-solution-design` para
formalizar isso em uma proposta técnica única, depois `/neotool-backlog-gen` para quebrar em
issues.

## 5. GitHub Project

Proposto (ainda não criado — bloqueado por falta de `gh`/token nesta sessão de Cowork):

- **Título:** Tax Calculation Engine
- **Descrição:** Redesign of tax computation (come-cotas + multi-asset tax regimes) as an
  extensible, idempotent engine. Supersedes ad hoc tax logic scattered across
  ComeCotasSupport, FundValuationService, and TaxPolicyService.
- Comando: `gh project create --owner invistus --title "Tax Calculation Engine" --format json`

O projeto de backlog geral do módulo de investimentos é o **#23**
(https://github.com/orgs/invistus/projects/23) — usar o Tax Calculation Engine como projeto
dedicado para este redesenho, não substituindo o #23.

## 6. Restrições/decisões explícitas do Marcos (não reabrir sem necessidade)

- MVP: sem backfill de dados já corrompidos na holding de teste.
- Sem override manual de `taxProfile` por holding por enquanto (rejeitado — "sem correção
  rápida, estamos em MVP").
- Validar o mapeamento S/N da CVM contra amostra real **depois** de corrigir os erros
  conhecidos, não antes.
- Todo conteúdo postado no GitHub (issues, PRs, branches) deve estar em inglês; a conversa
  com Marcos pode continuar em português.

## 7. Gaps ainda em aberto, fora do escopo imediato (registrados, sem issue ainda)

- Gate de elegibilidade de come-cotas por origem da transação (manual vs. OpenFinance) — só
  relevante quando a integração OpenFinance for implementada.
- Heurístico de nome para fundos isentos (`looksTaxExempt` em `FundTaxProfileClassifier`)
  continua sendo um heurístico de nome, não validado contra um campo oficial de isenção.

## 8. Sequenciamento e riscos de interferência — sub-issues #539-#547 (2026-07-30)

Status atual do épico #534: 12 sub-issues, 3 fechadas (**#536** TaxRegime + core,
**#537** TaxRegimeRegistry, **#538** FixedIncomeValuationService). 9 abertas: **#539, #540**
(fora do range pedido, mas bloqueiam #542) e **#541-#547**.

### Grafo de dependências (a partir do campo "Blocked by" de cada issue)

```
#539 (Fund regimes: Long/Short/VariableIncome/NoTax) ──┐
#540 (Absorve ComeCotasEligibility no registry) ───────┼──▶ #542 ──┬──▶ #543 ──┐
#541 (Checkpoint persistence table, sem deps) ─────────┘          │          │
                                                         #544 ◀────┘          │
                                                           │                  │
                                                           ▼                  │
                                                         #545                 │
                                                           │                  │
                                                           └──────▶ #546 ◀────┘
                                                                      │
                                                                      ▼
                                                                    #547
```

### Ordem de execução (sequencial — 1 workspace, sem trilhas paralelas)

Um único dev/workspace não ganha nada propondo paralelismo (isso só teria valor com mais de
uma pessoa/agente executando ao mesmo tempo); a sequência abaixo é estritamente linear,
ordenada para minimizar retrabalho quando a issue seguinte depende de uma decisão de design
da anterior:

1. **#540** (Absorve `ComeCotasEligibility` no registry) — menor, sem dependências, define
   elegibilidade que #543 vai assumir como resolvida.
2. **#539** (Fund regimes Long/Short/VariableIncome/NoTax) — define a interface de regime que
   #544 vai usar no gate de enfileiramento.
3. **#541** (Checkpoint persistence table) — implementar **depois** de #539/#540 para desenhar
   o schema já sabendo o que #542/#545 vão precisar gravar (ver risco 1 abaixo).
4. **#542** (idempotência do checkpoint — fix central) — só começa com 539/540/541 fechados.
5. **#543** (FundValuationService) — fechar todo o "thread" de migração de regime
   (checkpoint→valuation) antes de trocar de contexto para o job de recálculo, reduzindo custo
   de troca de contexto de um único dev.
6. **#544** (TaxRecalculationJob) — só depende de #542, mas feito depois de #543 por ser um
   contexto novo (infra de job, não mais regime/valuation).
7. **#545** (cascade para PortfolioRecalculationJob) — depende de #544.
8. **#546** (validação de integração contra o extrato) — depende de #542, #543 e #545 juntos;
   é o ponto de convergência do redesign.
9. **#547** (cleanup) — só depois de #546 confirmar que a nova pipeline bate com o extrato.

### Pontos de interferência entre issues (onde uma descoberta pode invalidar outra)

1. **Schema de #541 acoplado ao que #545 precisa comparar.** #545 exige "compara valor
   recalculado vs. persistido do checkpoint" — se a tabela criada em #541 não guardar o valor
   de retenção computado (não só a data/holding), #545 vai forçar uma migração adicional na
   mesma tabela. **Ação:** revisar o schema de #541 junto com o desenho de #545 antes de
   implementar #541, não depois.
2. **#543 assume que #540 já resolveu elegibilidade "estruturalmente".** O texto de #543 diz
   que migrar para o registry fecha o bug de #540 "structurally, not just via that issue's
   regression test" — ou seja, #543 está apostando no design de #540 sem re-testar
   explicitamente. Se #540 mudar de abordagem depois de #543 já implementado, #543 quebra
   silenciosamente (sem teste dedicado apontando).
3. **#544 depende do formato do regime definido em #539.** O gate de enfileiramento ("resolved
   regime has non-null `periodicWithholdingRate`") só funciona se a interface de regime de #539
   já estiver estável — como #539 vem antes de #544 na sequência (passo 2 vs. 6), a interface já
   estará validada em uso real (#542, #543) antes de #544 depender dela.
4. **#546 é o único ponto que testa os três fluxos juntos** (checkpoint, valuation, cascade).
   Uma divergência contra o extrato aqui pode indicar bug em #542 **ou** #543 **ou** #545
   isoladamente — vale já prever no ticket de #546 um checklist de "qual sub-fluxo isolar
   primeiro" (sugestão: reprocessar só checkpoint sem cascade, comparar; depois habilitar
   cascade).
5. **#547 é seguro** (só remove código morto) mas só depois de #546 confirmar estabilidade.
