---
title: Investment Portfolio — Notas de Sessão de Roadmap Retail (Março 2026)
type: session-notes
category: investment-portfolio
status: reference
version: 1.0.0
tags: [roadmap, retail, b2c, investimentos, fontes-de-dados, mvp, ir, imposto-de-renda, open-finance]
last_updated: 2026-03-05
---

# Investment Portfolio — Notas de Sessão de Roadmap Retail

> Sessão de planejamento realizada em 2026-03-05. Cobre benchmark competitivo, repriorização do roadmap, estratégia de fontes de dados e decisões abertas para a feature de Investimentos (Retail/B2C).

---

## 1. Contexto

A equipe está atualmente desenvolvendo o **módulo de controle financeiro** (contas bancárias, cartões de crédito, receitas/despesas). O **módulo de portfólio de investimentos** é a próxima grande feature a ser planejada e desenhada.

Esta sessão focou em:
- Entender o que o mercado está fazendo (benchmark competitivo)
- Definir quais funcionalidades priorizar no MVP
- Decidir como as contas de investimento se relacionam com as contas bancárias regulares na arquitetura
- Avaliar fontes de dados para cotações e dados de ativos com foco em minimizar custos

---

## 2. Resumo do Benchmark Competitivo

### Players Brasileiros

| Player | Foco Principal | Diferencial Central |
|---|---|---|
| **Kinvo** | Consolidação multi-custodiante | Índice Sharpe por ativo; cobertura do FGC visualizada na carteira |
| **Gorila** | Consolidação B2B/B2C | API pessoal para usuário PRO; white-label para corretoras; independente de custodiantes |
| **Grana Capital** | Automação de IR | Único app a automatizar o ciclo completo de IR: cálculo + pagamento de DARF + declaração IRPF |
| **Status Invest** | Análise fundamentalista | 200+ indicadores por ativo; screener avançado de ações e FIIs — em grande parte gratuito |
| **Meus Dividendos** | Dividendos / renda passiva | Agenda prospectiva de dividendos; DY consolidado da carteira |
| **TradeMap** | Análise + Execução de Ordens | Único no BR com execução de ordens integrada multibroker; módulo completo de opções |
| **App B3** (dez/2025) | Consolidação via CPF | Novo entrante pela própria B3; gratuito; automação de IR prevista para 2027 |

### Players Internacionais (Referência)

| Player | Foco Principal | Diferencial Central |
|---|---|---|
| **Empower (EUA)** | Patrimônio líquido + aposentadoria | Fee Analyzer (custos ocultos de fundos); simulador de aposentadoria Monte Carlo (5k cenários) |
| **Monarch Money (EUA)** | Orçamento + carteira | Melhor app para casais/famílias; balanço mais equilibrado entre orçamento e rastreamento de investimentos |
| **Copilot (EUA)** | Finanças no ecossistema Apple | Melhor UX/design; categorização de transações por IA; suporte a hardware wallets |
| **Wealthfront (EUA)** | Robo-advisor | Tax-loss harvesting diário automatizado; Direct Indexing para HNW |
| **YNAB (EUA)** | Orçamento base-zero | Prova que "fazer menos, fazer melhor" é viável; ignora investimentos intencionalmente |

### Principais Lacunas do Mercado Brasileiro (Oportunidade)

1. **Nenhum app integra controle de gastos + investimentos** em um dashboard unificado — o Invistus já tem o módulo de controle financeiro, o que é uma vantagem competitiva significativa.
2. **Nenhum engine de planejamento por objetivos** — "quanto preciso investir por mês para me aposentar com R$ 5k/mês em 20 anos?" — nenhum app brasileiro faz isso com qualidade.
3. **Fee Analyzer** — nenhum app brasileiro calcula automaticamente quanto o usuário paga em taxas de administração dos fundos da carteira (Empower faz; é transformador).
4. **Patrimônio líquido consciente de liquidez** — "tenho R$ 103k de patrimônio, mas só R$ 12.500 disponível amanhã" — essencial para planejamento financeiro real.
5. **Automação fiscal completa** — Grana Capital lidera, mas cobre apenas renda variável. Um engine fiscal integrado cobrindo todas as classes de ativos não existe.

---

## 3. Como Contas de Investimento se Relacionam com Contas Regulares

### Arquitetura em Três Camadas

O consenso do mercado (adotado por Kinvo, Gorila, Empower) separa os dados financeiros em três camadas distintas:

```
Camada 1 — CONTAS TRANSACIONAIS
  Contas bancárias (corrente, poupança), cartões de crédito
  → Rastreia: saldo disponível, fluxo de caixa, receitas/despesas

Camada 2 — CONTAS DE CUSTÓDIA (CORRETORAS)
  A "carteira" na corretora (XP, BTG, NuInvest, etc.)
  → Rastreia: caixa disponível na corretora para investir

Camada 3 — ATIVOS / POSIÇÕES
  Os investimentos em si, custodiados na corretora
  → CDB no BTG: R$ 10.000 @ 115% CDI, vence em 12/2026
  → 100 ações de PETR4 @ R$ 38,50 de preço médio
  → Tesouro IPCA+ 2035: R$ 5.000 investido
  → FII KNRI11: 50 cotas @ R$ 148,00
```

### Dashboard de Patrimônio Líquido

O dashboard do usuário deve agregar as três camadas em uma visão única de **Patrimônio Líquido**, inspirada no modelo do Empower:

```
PATRIMÔNIO LÍQUIDO (Total)
├── ATIVOS
│   ├── Contas Bancárias             R$ 12.500
│   ├── Renda Fixa                   R$ 45.000
│   ├── Renda Variável — Ações       R$ 28.300
│   ├── FIIs                         R$ 15.000
│   └── Outros (Cripto, etc.)         R$ 2.200
│                          Total:   R$ 103.000
└── DÍVIDAS
    ├── Cartão de Crédito             -R$ 3.200
    └── Financiamento                -R$ 45.000
                           Total:   -R$ 48.200

PATRIMÔNIO LÍQUIDO:                  R$ 54.800
```

### Desafios Técnicos de Precificação por Tipo de Ativo

| Classe de Ativo | Método de Precificação | Frequência de Atualização |
|---|---|---|
| Ações (B3) | Cotação de fechamento EOD ou intraday | Diária (ou tempo real) |
| FIIs | Preço de fechamento da cota na B3 | Diária |
| ETFs | Preço de fechamento da cota na B3 | Diária |
| BDRs | Preço de fechamento da cota na B3 | Diária |
| Tesouro Direto | Publicado pelo Tesouro Nacional | 2x ao dia |
| Renda Fixa (CDB, LCI, etc.) | Engine de juros acumulados (CDI/SELIC do BACEN) | Diária |
| Fundos de Investimento | Cota diária (CVM/ANBIMA) | D+1 a D+2 |
| Criptoativos | Feed de preço das exchanges | Tempo real / horário |

---

## 4. Tipos de Ativos Suportados

### Escopo do MVP

```
Renda Fixa:        CDB, LCI, LCA, LC, Debêntures, CRA, CRI, RDB, Tesouro Direto
Renda Variável:    Ações (B3), FIIs, ETFs, BDRs
Fundos de Investimento: Todos os fundos regulados — dados via CVM (gratuito e público)
```

### Diferido para Fase 2+

```
Internacional:     Ações EUA (via BDR ou direto), ADRs
Previdência:       PGBL, VGBL
Criptoativos:      Somente CEX inicialmente (manual ou via API)
Ativos Offshore:   Entrada manual — conformidade com Lei 14.754/2023
```

---

## 5. Estratégia de Fontes de Dados

### Princípio de Design

> Minimizar custos de licenciamento no MVP utilizando fontes gratuitas/públicas. Diferir integrações caras (Open Finance, cotações em tempo real da B3) para uma fase posterior quando a receita justificar o custo.

### Fontes Gratuitas / Baixo Custo

| Fonte | Dados Fornecidos | Custo | Adequação MVP |
|---|---|---|---|
| **Arquivo B3 (export PDF/Excel)** | Posições e transações de RV exportadas pelo usuário no portal Área do Investidor | Gratuito (ação do usuário) | ★★★★☆ — mesmo workflow de importação de faturas |
| **API Tesouro Nacional** | Preços do Tesouro Direto (atualização 2x/dia) | Gratuito | ★★★★★ |
| **BACEN SGS API** | CDI, SELIC, IPCA histórico | Gratuito | ★★★★★ — essencial para precificação de RF e benchmarks |
| **CVM (dados abertos)** | Todos os fundos regulados — cota diária, composição, histórico | Gratuito | ★★★★★ — fonte para o módulo de fundos |
| **B3 COTAHIST** | Boletim Diário de Informações da B3 (ações, FIIs, ETFs, BDRs, opções, contratos a termo) — fechamentos EOD de todos os instrumentos negociados na B3 | Gratuito (arquivo público, D+1) | ★★★★★ — **juridicamente limpo**: FAQ da B3 afirma explicitamente que dados EOD/D-1 podem ser distribuídos gratuitamente sem autorização prévia da B3; CAPTCHA limita automação (requer processo operacional de download); fonte primária para carga histórica e precificação diária |
| **Yahoo Finance** | Ampla cobertura incluindo ações BR (sufixo .SA) | Gratuito | ★★★☆☆ — juridicamente limpo para EOD (mesma isenção B3 se aplica); útil como fallback automatizado; sem SLA, depende de scraping, pode quebrar sem aviso |
| **brapi.dev** | Cotações EOD de ações/FIIs/ETFs, dividendos, eventos corporativos | R$49,90/mês (150k req/mês) | ★★☆☆☆ — juridicamente limpo para EOD, mas o custo não agrega proteção jurídica adicional sobre fontes gratuitas |
| **Alpha Vantage** | Ações BR e EUA, EOD | Free tier + planos pagos | ★★★☆☆ |

### Nota: B3 CEI Descontinuado

> O Canal Eletrônico do Investidor (CEI) foi **descontinuado pela B3 em novembro de 2021**. O substituto é a **Área do Investidor**, que opera em dois modos:
>
> - **Portal web (gratuito ao usuário):** permite exportar posições e transações em PDF ou Excel — viabiliza a abordagem de importação via arquivo.
> - **API Área do Investidor (B2B, paga):** exclusiva para clientes B2B (fintechs, corretoras); requer contrato com a B3 e tem tarifação. Entra na mesma categoria do Open Finance — **diferida para Fase 3**.

### B3 COTAHIST — Dados EOD de Mercado

O **COTAHIST** (Boletim Diário de Informações) é o boletim oficial diário da B3, cobrindo todos os instrumentos com preços de fechamento EOD. Formato de largura fixa, 245 bytes por registro, códigos BDI: 02=ações, 12=FIIs, 14=ETFs, 70=calls, 80=puts, 10=termos.

**Série histórica:** https://www.b3.com.br/pt_br/market-data-e-indices/servicos-de-dados/market-data/historico/mercado-a-vista/series-historicas/

#### Status Jurídico — CONFIRMADO LIMPO (revisado em 2026-03-05)

Duas fontes independentes confirmam que dados EOD não exigem contrato ou pagamento à B3:

1. **FAQ de Distribuidores da B3:** *"Dados de fim do dia e informações históricas D-1 podem ser distribuídos gratuitamente por distribuidores ou redistribuidores sem autorização prévia da B3."*
2. **Política Comercial de Market Data B3 2026 (v1.0, OC 104/2025-PRE):** Tabela de preços cobre apenas Tempo Real, Atraso Contínuo e Atraso Snapshot. **Dados EOD/histórico não possuem linha de preço.**
3. **Política Comercial de Market Data B3 2027 (v1.0, OC 104/2025-PRE):** Mesma estrutura, mesma ausência de precificação para EOD. A isenção é estrutural, não uma anomalia de um ano. Ambas as políticas publicadas sob o mesmo Ofício Circular.

#### Restrição Operacional

A B3 adicionou validação humana (CAPTCHA) na página de download (`FormConsultaValida.asp`). URLs diretas não funcionam mais para ingestão automatizada. **Solução: processo operacional de download** — um membro da equipe baixa o arquivo diário/anual; isso é um processo interno privado e não afeta o direito legal de usar e exibir os dados.

#### Futuro: Cotações com Delay (Fase 2+)

Ao adicionar cotações em tempo real ou com delay de até 15 min, um contrato de distribuição com a B3 é necessário. Para um app B2C, o tier aplicável é **Terminal Não Profissional** (por dataset): R$1,86/usuário/mês em 2026, R$2,10/usuário/mês em 2027 (~13% de reajuste anual). Considerar na precificação da Fase 2.

### Open Finance e API B3 (B2B/Paga)

- **Decisão: Diferido.** Certificação de Open Finance, API B3 Área do Investidor e conformidade com BACEN têm custos significativos de infraestrutura e regulatórios que não se justificam no MVP.
- **Quando reavaliar:** Quando a receita de planos pagos suportar o investimento. O Open Finance Fase 4 (dados de investimentos) atingiu 1,81 bilhão de chamadas de API/mês em dezembro de 2024 — a infraestrutura está maturando rapidamente.

---

## 6. Dados de Renda Fixa — RESOLVIDO

### Descoberta: O PDF da B3 inclui Renda Fixa

O portal **Área do Investidor da B3** permite ao usuário baixar um PDF consolidado com **todos os produtos custodiados**, incluindo renda fixa de qualquer corretora. Exemplo real do formato:

```
CDB - BANCO BMG S/A    XP INVESTIMENTOS CCTVM S/A    30/03/2026    877    R$ 1,65    R$ 1.448,26
```

Isso significa que CDBs, LCIs, LCAs e outros produtos de RF custodiados na B3 estão presentes no mesmo arquivo que ações, FIIs e Tesouro Direto — **uma única importação cobre toda a carteira do usuário**, independente de quantas corretoras ele utilize.

### Decisão RF-01: RESOLVIDA — Opção B (importação via arquivo)

O workflow é idêntico ao já utilizado para faturas de cartão:

```
Upload do PDF B3 → Extractor Service → AI Parser → Posições Normalizadas (RF + RV)
```

- **Cobre:** CDB, LCI, LCA, Debêntures, CRA, CRI, Ações, FIIs, ETFs, BDRs, Tesouro Direto
- **Multi-corretora por natureza:** o PDF agrega todas as corretoras do CPF do usuário
- **Custo:** zero — é uma ação gratuita do usuário no portal da B3
- **Atualização:** o usuário re-importa quando quiser atualizar as posições (fluxo assíncrono, sem pressão de tempo real)

### Ressalva

O PDF reflete posições em **D-1** (dia útil anterior). Não é tempo real, mas é suficiente para o caso de uso de acompanhamento de portfólio.

### Open Finance Fase 4 (diferido — ainda válido para automação futura)

Quando o Open Finance for viável, poderemos automatizar o ciclo sem ação manual do usuário. O arquivo B3 é a solução de MVP; o Open Finance é a evolução natural.

---

## 7. Fundos de Investimento — CVM como Fonte Gratuita

### Decisão: Usar Dados Abertos da CVM

A CVM (Comissão de Valores Mobiliários) publica dados abrangentes, gratuitos e legíveis por máquina sobre todos os fundos de investimento regulados no Brasil:

- Cadastro de fundos (CNPJ, nome, tipo, gestor, administrador)
- Histórico diário de cotas (VPL)
- Divulgação mensal de composição da carteira
- Métricas de desempenho e risco

Isso elimina a necessidade de qualquer fornecedor de dados pago para o módulo de fundos no MVP.

**Abordagem de integração:**
1. Job periódico de ingestão para sincronizar dados da CVM no banco interno
2. Busca de fundos respaldada pelo catálogo interno com fonte CVM
3. Usuário vincula suas posições (entrada manual de cotas detidas) → app calcula o valor usando a cota CVM

---

## 8. Metodologia de Cálculo de Rentabilidade

### Dois Métodos — Ambos Relevantes

| Método | O que mede | Melhor uso |
|---|---|---|
| **TWRR** (Time-Weighted Rate of Return) | Performance do ativo/carteira, isolando o efeito de aportes e retiradas | Comparação com benchmarks (CDI, IBOVESPA) |
| **MWRR / TIR** (Money-Weighted Rate of Return) | Retorno efetivo do investidor, considerando timing dos aportes | Mostrar ao usuário o retorno real obtido |

**Recomendação:** Exibir TWRR para comparação com benchmarks; oferecer MWRR como visão adicional (ou explicação em tooltip).

### Benchmarks Padrão (Mercado Brasileiro)

| Benchmark | Classe de Ativo | Fonte |
|---|---|---|
| CDI | Referência para renda fixa | BACEN SGS (gratuito) |
| SELIC | Referência para títulos públicos | BACEN SGS (gratuito) |
| IPCA | Inflação | BACEN SGS (gratuito) |
| IBOVESPA | Renda variável (mercado amplo) | brapi.dev ou B3 |
| IFIX | Índice de FIIs | brapi.dev ou B3 |
| Poupança | Referência de baixo risco | BACEN SGS (gratuito) |

---

## 9. Funcionalidades de IR / Imposto de Renda

### Por que é Importante

A automação de IR é a funcionalidade de **maior valor percebido e mais negligenciada** no mercado brasileiro de apps de investimento. O Grana Capital lidera, mas cobre apenas renda variável. Um engine fiscal completo é um diferencial significativo.

### Conjunto de Features por Prioridade

**Alta prioridade (MVP ou Fase 2):**
- Relatório auxiliar de IR para renda variável (ações, FIIs)
- Segregação swing trade vs. day trade (alíquotas diferentes: 15% vs. 20%)
- Controle de isenção mensal (isenção de R$ 20k/mês em venda de ações)
- Rastreador de compensação de prejuízos (carry forward para abater ganhos futuros)

**Prioridade média (Fase 2):**
- Geração de DARF (PDF com valor correto e código de recolhimento)
- Relatório de declaração IRPF anual (dados estruturados para o programa da Receita Federal)
- Relatório de rendimentos de renda fixa (a partir dos informes emitidos pelas corretoras)
- Regras de IR para FIIs (isenção quando < 10% de participação e fundo com 50+ cotistas)

**Prioridade menor (Fase 3+):**
- Pagamento automatizado de DARF (requer integração bancária)
- Declaração de criptoativos (DeCripto / Receita Federal, vigência julho de 2026)
- Declaração de ativos offshore (Lei 14.754/2023)

---

## 10. Roadmap Revisado

### Princípio de Design

> **Maximizar valor no MVP. Diferir Open Finance por custo. Antecipar features de diferenciação de alto valor (IR, planejamento) em vez de aguardar a maturação das integrações de dados.**

---

### MVP — Máximo Valor, Custo Mínimo

**Objetivo:** Entregar um módulo de investimentos convincente e utilizável que supera os concorrentes na integração com o módulo de controle financeiro já existente.

**Escopo:**

| Área | Funcionalidades |
|---|---|
| **Cadastro de Ativos** | Entrada manual de todos os tipos; importação de arquivo B3 (PDF/Excel exportado pelo usuário no portal Área do Investidor) |
| **Tipos de Ativos** | Renda fixa (manual), ações, FIIs, ETFs, BDRs, Tesouro Direto, fundos de investimento (CVM) |
| **Precificação** | Cotações EOD via brapi.dev; RF calculada via CDI/SELIC do BACEN; Tesouro via API do Tesouro Nacional; fundos via cota CVM |
| **Dashboard** | Visão de patrimônio líquido agregando contas bancárias + cartões + investimentos; breakdown de alocação por classe de ativo |
| **Performance** | Rentabilidade TWRR vs. benchmarks (CDI, IBOVESPA, IPCA, IFIX) |
| **Proventos** | Calendário de dividendos e rendimentos; recebimentos acumulados no ano e histórico |
| **Cobertura FGC** | Cobertura do FGC por emissor e percentual da carteira (visível nas posições de RF) |
| **Relatório de IR** | Relatório auxiliar de IR para renda variável (regras de swing trade + FIIs) |
| **Fee Analyzer** | Cálculo automático do total de taxas de administração pagas em fundos da carteira |

**Importação unificada via PDF B3:** o PDF do portal Área do Investidor consolida RF + RV de todas as corretoras do usuário em um único arquivo. O mesmo pipeline `Upload → Extractor → AI` já existente para faturas de cartão cobre toda a carteira de investimentos.

---

### Fase 2 — Planejamento e Diferenciação

| Área | Funcionalidades |
|---|---|
| **Automação de IR** | Geração de DARF; relatório de declaração IRPF anual; casos especiais de IR em FIIs |
| **Planejador de Objetivos** | Simulador de aposentadoria; objetivos financeiros personalizados com projeções de aportes |
| **Cotações Intraday** | Atualizações de preço mais frequentes — avaliar custo vs. benefício |
| **Previdência Privada** | Posições de PGBL e VGBL |
| **Criptoativos** | Somente CEX: Binance, Mercado Bitcoin (manual ou via API de exchange) |
| **Importação por Arquivo** | Importação de RF via arquivo totalmente produtizada (se não estiver no MVP) |

---

### Fase 3 — Integrações Premium

| Área | Funcionalidades |
|---|---|
| **Open Finance Fase 4** | Dados de RF automatizados via API padronizada (quando o custo for justificado pela receita) |
| **API B3 Área do Investidor** | Importação automática de posições RV via API B2B paga da B3 (substituto do CEI descontinuado) |
| **APIs de Corretoras** | Integrações diretas com XP, BTG, NuInvest, etc. |
| **Ativos Offshore** | Manual + importação semi-automatizada; conformidade com Lei 14.754/2023 |
| **IR Avançado** | Pagamento automatizado de DARF; imposto de cripto (DeCripto); estruturas HNW |

---

## 11. Decisões Abertas desta Sessão

| # | Decisão | Status | Observações |
|---|---|---|---|
| RF-01 | Método de importação de renda fixa para o MVP | **RESOLVIDO** | Opção B: PDF da B3 (Área do Investidor) inclui RF + RV de todas as corretoras — mesmo pipeline de faturas de cartão |
| RF-02 | Fonte de dados EOD automatizada | **RESOLVIDO** | Yahoo Finance para o MVP (gratuito, mesmo risco jurídico de alternativas pagas sem licença B3 formal); COTAHIST para carga histórica via processo operacional de download; migrar para fonte licenciada quando a receita justificar |
| RF-03 | Tradeoff entre cotações EOD vs. intraday com delay | **TBD** | EOD é a abordagem do MVP; reavaliar na Fase 2 |
| RF-04 | Frequência de ingestão de dados da CVM | **TBD** | Job diário em batch provavelmente suficiente; definir SLA |
| RF-05 | Licença de exibição do COTAHIST | **RESOLVIDO — NENHUMA LICENÇA NECESSÁRIA** | FAQ da B3 afirma explicitamente: "Dados de fim do dia e informações históricas D-1 podem ser distribuídos gratuitamente por distribuidores ou redistribuidores sem autorização prévia da B3." O contrato de distribuição só é obrigatório para dados em tempo real ou com delay de até 15 minutos. COTAHIST (EOD/D+1) está completamente isento. Fonte: FAQ de Distribuidores de Market Data da B3. |

---

## 12. Insights-Chave

- **O Invistus tem uma vantagem estrutural** que nenhum concorrente brasileiro possui: controle de gastos + rastreamento de investimentos em um único produto. Este é o norte para a estratégia de integração.
- **PDF B3 + COTAHIST + BACEN + CVM** cobre toda a carteira do usuário (RF + RV, multi-corretora) com custo zero e total clareza jurídica para o MVP. Dados EOD/D-1 não exigem contrato de distribuição com a B3 (confirmado pelo FAQ oficial da B3). O COTAHIST fornece todos os preços EOD de forma legal e gratuita — a única restrição operacional é o CAPTCHA na página de download, resolvido por um processo interno de download. Dados em tempo real ou com delay de até 15 minutos são o que exige contrato — irrelevante para este caso de uso.
- **Open Finance é um acelerador futuro, não um requisito do MVP.** O ecossistema está crescendo rapidamente (1,81 bilhão de chamadas de API/mês para investimentos em dez/2024); o timing vai melhorar.
- **Features de IR não devem ser diferidas.** Automação tributária é a feature de maior valor percebido pelos investidores brasileiros e a menos coberta pelos concorrentes fora do escopo restrito do Grana Capital.
- **App B3 (lançado dez/2025)** é um potencial commoditizador da consolidação básica — o Invistus deve se diferenciar em planejamento, profundidade de IR e na integração com controle financeiro, não apenas no rastreamento de carteira.
