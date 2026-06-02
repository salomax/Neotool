# Investment Portfolio — UX Benchmark & Wireframes

> Gerado em Março/2026 · Fase MVP — cadastro manual e gestão de ativos

---

## Benchmark

### Apps analisados

| App | Mercado | Diferencial-chave |
|-----|---------|-------------------|
| **Gorila** | BR | Maior cobertura de ativos BR (40+ produtos, 14k+ ativos), agrupamento por classe, modo advisors |
| **Kinvo** | BR | Aportes vs. Rendimentos (exclusivo), Sharpe ratio, 2M usuários, metas de investimento |
| **Investidor10** | BR | Análise fundamentalista integrada, UI mais leve, tier gratuito generoso |
| **NuInvest** | BR | Integrado ao Nubank (100M usuários), zero corretagem, foco em simplicidade |
| **Sharesight** | INT | Import via e-mail de confirmações, relatório fiscal por país, ETF overlap analysis |
| **Stock Events** | INT | Calendário de dividendos, recap mensal, mobile-first polido (4.58★) |
| **Portfolio Performance** | INT | TWR/IRR precisos, open-source, modelo mais rigoroso de retorno |
| **Delta (eToro)** | INT | Busca ISIN/SEDOL/CUSIP, "Why is it moving?" (AI), multi-asset crypto+stocks |
| **Empower** | INT | Investment Checkup, projeção de taxas vitalícias, integração com aposentadoria |

### O que é table stakes (todos têm)

- Total do patrimônio em destaque com seletor de período (1D / 1M / 3M / 6M / YTD / 1Y / Max)
- Alocação por classe de ativo (pizza/donut)
- Lista de posições com: nome, valor atual, quantidade, preço médio, P&L (R$ e %)
- Agrupamento por classe, ordenação por valor ou rentabilidade
- Formulário de transação: tipo, ativo, data, quantidade, preço unitário, total calculado
- Comparação com benchmark (CDI, IBOV)
- Detalhe de posição com histórico de transações

### Diferenciadores que importam (por tier)

**Tier 1 — alto impacto:**
| Feature | Referência | Razão |
|---------|-----------|-------|
| Aportes vs. Rendimentos separados | Kinvo | Métrica mais emocionalmente relevante — quanto veio de você vs. do mercado |
| IR estimado por posição | Lacuna total no BR | Maior dor do investidor BR, ninguém resolve de forma integrada |
| Preço médio sempre visível | Mercado todo | Pergunta #1 do investidor de ações |
| Sugestão de cotação na data do formulário | Sharesight | Elimina lookup manual |
| Nota de corretagem PDF (futura) | Lacuna total no BR | Equivalente do email-import do Sharesight |

**Tier 2 — diferenciação real:**
| Feature | Referência |
|---------|-----------|
| Calendário de proventos (dividendos, JCP, rendimentos) | Stock Events |
| Sharpe ratio e retorno ajustado a risco | Kinvo |
| Completude de tipos de transação (split, bonificação, subscrição, amortização) | Portfolio Performance |
| Settlement date sugerido (D+2 ações, D+1 RF) | Portfolio Performance |
| Busca por ISIN (para BDRs e ativos internacionais) | Delta |

**Tier 3 — nice to have (V3+):**
- Recap mensal automático por push notification (Stock Events)
- Calculadora de rebalanceamento com target allocation (Portfolio Performance)
- Projeção de taxas ao longo do tempo (Empower)
- Análise de sobreposição de ETFs (Sharesight)
- Dark mode com P&L color-coded (Delta)

### Oportunidades não atendidas pelo mercado BR

1. **IR unificado em tempo real** — nenhum app BR mostra "quanto de IR você deve este mês" consolidando RV + RF + Fundos
2. **Nota de corretagem PDF parsing** — equivalente do email-import do Sharesight; seria um diferencial de aquisição
3. **Calculadora de rebalanceamento** — dado um IPS (70% RV / 30% RF), calcular quais aportes fazer sem vender (para não gerar IR)
4. **Ativos internacionais com Lei 4.173/2023** — nenhum app trata IR mensal de offshore de forma integrada
5. **Mobile-first nativo** — Delta e Stock Events lideram; apps BR são desktop portados para mobile

---

## Decisões de design adoptadas

| Decisão | Justificativa |
|---------|---------------|
| Bottom sheet para formulário de transação | Contexto preservado, menor ruptura de fluxo |
| Progressive disclosure no formulário | Simples para maioria; poder para quem precisa |
| Preço sugerido = cotação do dia | Reduz o principal ponto de fricção no cadastro |
| Tipo de transação com segmented control visual | Compra/Venda/Provento são 90% dos casos; tipos avançados ficam em "mais opções" |
| Agrupamento colapsável por classe | Padrão Gorila — evita sobrecarga visual; subtotais por classe são prioritários |
| Aportes vs. Rendimentos no overview | Diferencial Kinvo — adotar como padrão |
| IR estimado no detalhe da posição | Lacuna de mercado — MVP já entrega |
| Custódia como seletor de conta INVESTMENT | Consistente com o modelo de dados (financial_account do tipo INVESTMENT) |

---

## Wireframes

### Tela 1 — Tab Investimentos (Portfolio Overview)

```
┌─────────────────────────────────────┐
│ ☰  Investimentos          🔔  [👤]  │
├─────────────────────────────────────┤
│                                     │
│  Patrimônio total                   │
│  R$ 142.830,00                      │
│                                     │
│  ┌──────────────┬──────────────┐    │
│  │ Aportes      │ Rendimentos  │    │
│  │ R$ 120.000   │ +R$ 22.830   │    │
│  │              │ +19,0%       │    │
│  └──────────────┴──────────────┘    │
│                                     │
│  [1D] [1M] [3M] [6M] [YTD] [1Y] [Max]
│  ──────────────────────────────     │
│  ╭─────────────────────────────╮    │
│  │  ╱╲    ╱╲         ╱╲       │    │
│  │╱    ╲╱    ╲    ╱╲╱   ╲    │    │
│  │·  ·  ·  ·  ╲╱        ╲   │    │
│  ╰─────────────────────────────╯    │
│  vs CDI ── +2,3pp   vs IBOV +8,1pp │
│                                     │
├─────────────────────────────────────┤
│  Alocação        [Lista ▾]  [+ Ativo]│
│  ╭───────╮                          │
│  │ ██░░░ │  Renda Variável  52%     │
│  │ ░█░░░ │  Renda Fixa      31%     │
│  │ ░░█░░ │  FIIs            12%     │
│  │ ░░░█░ │  Outros           5%     │
│  ╰───────╯                          │
├─────────────────────────────────────┤
│  Minha Carteira      Ordenar ↕  ⚙️  │
│                                     │
│  ▼ Renda Variável          R$ 74.270│
│  ┌─────────────────────────────────┐│
│  │ PETR4   Petrobras ON            ││
│  │ 200 un · PM R$ 28,50            ││
│  │ R$ 37.200           +30,5% ▲    ││
│  └─────────────────────────────────┘│
│  ┌─────────────────────────────────┐│
│  │ BOVA11  iShares IBOV ETF        ││
│  │  50 un · PM R$ 112,40           ││
│  │ R$ 6.250            +11,2% ▲    ││
│  └─────────────────────────────────┘│
│  [ver mais 3 ativos]                │
│                                     │
│  ▶ Renda Fixa              R$ 44.290│
│  ▶ FIIs                    R$ 17.140│
│  ▶ Outros                  R$  7.130│
│                                     │
├─────────────────────────────────────┤
│  📅 Próximos Proventos              │
│  MXRF11  R$ 0,09/cota  15/Abr      │
│  KNRI11  R$ 0,67/cota  20/Abr      │
│  [ver calendário completo]          │
│                                     │
├─────────────────────────────────────┤
│  🏠 Home  │  💳 Banking  │  📈 Invest  │  👤 │
└─────────────────────────────────────┘
```

**Notas de design:**
- `Aportes vs. Rendimentos` — card em destaque logo abaixo do patrimônio (diferencial Kinvo)
- Gráfico mostra carteira + linha de benchmark (CDI por padrão, trocável)
- Holdings agrupados por classe com collapse; subtotal visível mesmo colapsado
- Seção "Próximos Proventos" antecipa o calendário de dividendos (V2 completo)
- FAB `+ Ativo` permite cadastro rápido de nova posição

---

### Tela 2 — Detalhe do Ativo (Position Detail)

```
┌─────────────────────────────────────┐
│ ←   PETR4 · Petrobras ON       ⋮   │
├─────────────────────────────────────┤
│                                     │
│  R$ 37.200,00                       │
│  +R$ 8.700  (+30,5%)  ▲ acima PM   │
│                                     │
│  ╭─────────────────────────────╮    │
│  │         ╱╲                  │    │
│  │        ╱  ╲      ╱╲        │    │
│  │    ╱╲ ╱    ╲    ╱  ╲       │    │
│  │───╱──╲──────╲──╱────╲──    │    │
│  ╰─────────────────────────────╯    │
│  [1M] [3M] [6M] [YTD] [1Y] [Max]   │
│                                     │
├─────────────────────────────────────┤
│  ┌──────────┬──────────┬──────────┐ │
│  │ Qtde.    │ P. Médio │ P. Atual │ │
│  │ 200 un   │ R$ 28,50 │ R$ 37,60 │ │
│  └──────────┴──────────┴──────────┘ │
│                                     │
│  ┌──────────────────────────────┐   │
│  │ Valor investido  R$  5.700   │   │
│  │ Valor atual      R$  7.520   │   │
│  │ Ganho não realiz.+R$  1.820  │   │
│  │ Proventos rec.   +R$    820  │   │
│  │ IR estimado      -R$    273  │   │  ← diferencial: IR visível
│  └──────────────────────────────┘   │
│                                     │
├─────────────────────────────────────┤
│  Histórico de Transações            │
│                                     │
│  ↑ Compra   15/01/2024  100 un      │
│    R$ 31,20/un · Total R$ 3.120     │
│    XP Investimentos                 │
│                                     │
│  ↑ Compra   10/10/2023  100 un      │
│    R$ 25,80/un · Total R$ 2.580     │
│    XP Investimentos                 │
│                                     │
│  💰 Dividendo  15/03/2024           │
│    R$ 0,42/un · Total R$   420      │
│                                     │
│  [carregar mais]                    │
│                                     │
├─────────────────────────────────────┤
│        ┌─────────────────┐          │
│        │ + Nova Transação │          │
│        └─────────────────┘          │
└─────────────────────────────────────┘
```

**Notas de design:**
- Preço médio, preço atual e quantidade em destaque no topo — métricas #1 do investidor
- IR estimado visível na posição — nenhum app BR faz isso nativamente
- Tipo de transação com ícone + cor (▲ verde = compra, ▼ vermelho = venda, 💰 azul = provento)
- FAB contextual `+ Nova Transação` pré-preenche ativo e custódia

---

### Tela 3 — Registrar Transação (bottom sheet, campos principais)

```
┌─────────────────────────────────────┐
│  [fundo escurecido — tela anterior] │
│                                     │
╰─────────────────────────────────────╯
┌─────────────────────────────────────┐  ← bottom sheet
│  ▬▬▬                               │    drag handle
│  Nova Transação                  ✕  │
│                                     │
│  Tipo                               │
│  ╔══════════╦══════════╦══════════╗  │
│  ║ ▲ COMPRA ║ ▼ VENDA  ║ 💰 PROV. ║  │  ← segmented control
│  ╚══════════╩══════════╩══════════╝  │
│  [+ avançado ▾]  Split · Subscr. …  │  ← progressive disclosure
│                                     │
│  Ativo                              │
│  ┌─────────────────────────────────┐│
│  │ 🔍  Buscar por nome ou ticker…  ││
│  └─────────────────────────────────┘│
│  ● PETR4  Petrobras ON         ✓   │  ← pré-selecionado se veio
│            B3 · Ação                │    do detalhe
│                                     │
│  Data da Operação                   │
│  ┌─────────────────────────────────┐│
│  │  15  /  04  /  2025       📅   ││
│  └─────────────────────────────────┘│
│                                     │
│  Quantidade          Preço unitário │
│  ┌──────────────┐  ┌───────────────┐│
│  │   100        │  │  R$ 37,60     ││
│  └──────────────┘  └───────────────┘│
│                        💡 cotação hoje
│                                     │
│  ┌─────────────────────────────────┐│
│  │  Total estimado:   R$ 3.760,00  ││  ← calculado em tempo real
│  └─────────────────────────────────┘│
│                                     │
│  Conta Custodiante                  │
│  ┌─────────────────────────────────┐│
│  │  XP Investimentos           ▾  ││  ← lista de contas INVESTMENT
│  └─────────────────────────────────┘│
│                                     │
│  [+ Campos avançados ▾]             │
│                                     │
│  ┌─────────────────────────────────┐│
│  │         Registrar Compra        ││
│  └─────────────────────────────────┘│
└─────────────────────────────────────┘
```

---

### Tela 4 — Campos Avançados (expandido)

```
│  [+ Campos avançados ▲]             │
│                                     │
│  Taxa de corretagem                 │
│  ┌─────────────────────────────────┐│
│  │  R$ 0,00                        ││
│  └─────────────────────────────────┘│
│                                     │
│  Data de liquidação                 │
│  ┌─────────────────────────────────┐│
│  │  17  /  04  /  2025  (D+2)  📅 ││  ← sugestão D+2 ações / D+1 RF
│  └─────────────────────────────────┘│
│                                     │
│  Fator de preço                     │
│  ┌─────────────────────────────────┐│
│  │  1                              ││  ← relevante para debêntures
│  └─────────────────────────────────┘│
│                                     │
│  Número da nota de corretagem       │
│  ┌─────────────────────────────────┐│
│  │  Ex: 123456                     ││
│  └─────────────────────────────────┘│
│                                     │
│  Observações                        │
│  ┌─────────────────────────────────┐│
│  │  Ex: compra estratégica Q1…     ││
│  └─────────────────────────────────┘│
│                                     │
│  📎 Anexar nota de corretagem       │  ← auditoria / fase futura
│     (PDF ou imagem)                 │
```

---

### Tela 5 — Primeiro cadastro de Renda Fixa (campos condicionais)

> Exibido quando o ativo selecionado é do tipo CDB / LCI / LCA / CRI / CRA / Debentures / Tesouro

```
│  ─── Condições do título ──────────  │
│                                      │
│  Indexador                           │
│  ┌──────────────────────────────────┐│
│  │  CDI                         ▾  ││
│  └──────────────────────────────────┘│
│                                      │
│  % do indexador          Taxa pré    │
│  ┌──────────────┐  ┌────────────────┐│
│  │  110%        │  │  —             ││  ← pré ou pós, não ambos
│  └──────────────┘  └────────────────┘│
│                                      │
│  Vencimento          Carência        │
│  ┌──────────────┐  ┌────────────────┐│
│  │  15/04/2027  │  │  15/10/2025    ││
│  └──────────────┘  └────────────────┘│
│                                      │
│  Data de emissão                     │
│  ┌──────────────────────────────────┐│
│  │  10/04/2024                      ││
│  └──────────────────────────────────┘│
│                                      │
│  ☑  Isento de IR  (LCI / LCA / CRI / CRA)
```

---

### Tela 6 — Estado vazio (primeiro acesso)

```
┌─────────────────────────────────────┐
│ ☰  Investimentos          🔔  [👤]  │
├─────────────────────────────────────┤
│                                     │
│                                     │
│           📈                        │
│                                     │
│   Sua carteira está vazia           │
│   Registre seu primeiro ativo       │
│   para acompanhar seu patrimônio    │
│                                     │
│   ┌─────────────────────────────┐   │
│   │  + Adicionar primeiro ativo │   │
│   └─────────────────────────────┘   │
│                                     │
│   ┌─────────────────────────────┐   │
│   │  Ver carteira demonstração  │   │  ← reduz abandono
│   └─────────────────────────────┘   │
│                                     │
│   💡 Conecte sua corretora pelo     │
│      Open Finance para importar     │
│      automaticamente (em breve)     │
│                                     │
│                                     │
├─────────────────────────────────────┤
│  🏠 Home  │  💳 Banking  │  📈 Invest  │  👤 │
└─────────────────────────────────────┘
```

---

## Priorização MVP vs. fases futuras

### MVP (esta fase)
- [ ] Tab Investimentos com Portfolio Overview
- [ ] Aportes vs. Rendimentos no overview
- [ ] Holdings list agrupado por classe (colapsável)
- [ ] Detalhe da posição com preço médio + IR estimado
- [ ] Formulário de transação manual (Compra / Venda / Provento)
- [ ] Campos condicionais para Renda Fixa
- [ ] Estado vazio com CTA guiado
- [ ] Custódia via seletor de conta INVESTMENT existente

### V2
- [ ] Calendário de proventos completo
- [ ] Sharpe ratio e retorno ajustado a risco
- [ ] Benchmark customizável (além de CDI/IBOV)
- [ ] Busca por ISIN (para BDRs e ativos internacionais)
- [ ] Notificações de proventos e eventos corporativos

### V3+
- [ ] Calculadora de rebalanceamento com target allocation
- [ ] Parsing de nota de corretagem PDF (maior diferencial possível no BR)
- [ ] IR consolidado por mês (RV + RF + Fundos) com geração de DARF
- [ ] Análise de sobreposição de ETFs
- [ ] Relatório para IRPF (copy-paste para programa da Receita)
