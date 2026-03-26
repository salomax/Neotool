# Product Price Tracking — Requirements Document

**Service:** Invistus Banking Service
**Module:** Personal Finance — Product Price Tracking
**Document date:** 2026-03-25
**Status:** Draft — Design Phase

---

## 1. Overview

Product Price Tracking permite que o usuário acompanhe o preço dos produtos que consome ao longo do tempo, usando como fonte primária a **NF-e / NFC-e** (Nota Fiscal Eletrônica do Consumidor). Hoje, o import de NFC-e já existe e cria uma transação agregada, mas **descarta os itens individuais**. Esta feature persiste cada item, normaliza produtos, e habilita análises de preço e consumo.

### Objetivos

**Fase 1 — Preço & Consumo Pessoal:**
- Quanto estou pagando em média pelo produto X?
- Meu consumo do produto Y aumentou/diminuiu em %?
- Estou pagando X% acima/abaixo da média regional?

**Fase 2 — Inflação & Custo de Vida:**
- Qual minha inflação pessoal (cesta pessoal vs IPCA)?
- Se eu mudasse de cidade, qual seria meu custo de vida estimado?

---

## 2. O Problema da Normalização de Produtos

Este é o maior desafio técnico da feature. Um mesmo produto aparece de formas completamente diferentes entre estabelecimentos:

```
"PIPOCA YOKI 500G"
"PIPOCA P/MICRO YOKI"
"PIPOCA"
"MILHO P/ PIPOCA 500G"
"PIPOCA DOCE 100G"
```

Além disso:
- Unidades variam: UN, KG, G, L, ML, PCT, CX, FD, DZ, M, M2, M3
- Gramatura pode ou não estar na descrição
- Marca pode ou não estar presente
- NCM (8 dígitos) agrupa por tipo fiscal, mas é muito amplo (ex: 19042000 = todos os cereais preparados)
- EAN/GTIN (código de barras) quando presente é o melhor identificador, mas nem sempre consta na NFC-e

### 2.1 Estratégia de Normalização (6 Camadas — mais barata → mais cara)

A normalização segue uma pipeline em camadas. Cada item percorre as camadas em ordem até ser resolvido. O objetivo é que **AI seja o último recurso, não o primeiro**.

| Camada | Método | Custo | Cobertura (início) | Cobertura (maduro*) |
|---|---|---|---|---|
| 1 | EAN/GTIN catálogo interno | Zero | ~0% (catálogo vazio) | ~40-60% |
| 2 | Alias reutilizado (cache) | Zero | ~0% | ~30-40% |
| 3 | Same CNPJ + cProd | Zero | ~0% | ~5-10% |
| 4 | Heurísticas de texto | Zero | ~15-20% | ~15-20% |
| 5 | Embeddings (batch offline) | Baixo | ~10% | ~5% |
| 6 | LLM (sob demanda do usuário) | Alto | ~5% | ~2% |
| — | **Não resolvido / manual** | Zero | **~65-70%** | **~3-5%** |

\* "Maduro" = após ~6 meses de uso ativo com base de usuários crescente. As camadas 1-3 têm efeito de rede: a cobertura cresce organicamente conforme mais usuários mapeiam produtos. No início, a maior parte dos itens dependerá de mapeamento manual ou ficará pendente para o batch D+1 (heurísticas + embeddings).

#### Camada 1 — EAN/GTIN via Catálogo Interno (Automática, custo zero)

Quando o código de barras está presente na NFC-e (`cEAN` / `cEANTrib`), ele identifica unicamente o produto + apresentação. O match é feito contra o **catálogo interno cross-tenant** (`product_variants.ean`): se qualquer usuário já mapeou aquele EAN, o novo item é resolvido automaticamente. O catálogo se auto-alimenta — quanto mais NFC-e importadas na plataforma, maior a cobertura.

> **Fontes externas de EAN — Avaliação:**
>
> Não existe hoje uma base aberta e confiável de EAN/GTIN para produtos brasileiros. Todas as opções avaliadas são inviáveis na v1:
>
> | Fonte | Problema |
> |---|---|
> | Bluesoft Cosmos | ToS proíbe uso comercial sem acordo; free = 25 req/dia; pago = R$ 2.000/mês por 500 req |
> | Open Food Facts | ~33K produtos BR, cobertura muito baixa para o mercado brasileiro |
> | DotCompany | 50 req/dia no free, pricing enterprise não público |
> | GS1 Brasil (CNP) | Acesso restrito a associados, pricing não público |
> | SEFAZ CCG | API exige certificado digital de emissor NF-e; portal manual não é automável |
> | EAN-Search.org | Pago (a partir de EUR 19/mês), sem foco Brasil |
> | RSC Sistemas | Base pequena, qualidade baixa |
>
> **Decisão:** Sem dependência de fonte externa de EAN. O EAN é usado exclusivamente como **chave de dedup interna** no catálogo cross-tenant. Quando um produto com determinado EAN é mapeado (por qualquer camada), o EAN passa a resolver automaticamente para todos os usuários. O catálogo se constrói organicamente com o uso da plataforma. Fontes externas podem ser reavaliadas no futuro se surgir uma opção viável.

#### Camada 2 — Alias Reutilizado (Cache permanente, custo zero)

Se a `raw_description` já foi mapeada antes (por este ou outro usuário), reutilizar direto via tabela `product_alias`. Funciona como cache: quanto mais NFC-e importadas, maior a cobertura. Efeito de rede entre usuários.

#### Camada 3 — Same CNPJ + cProd (Custo zero)

Mesmo estabelecimento (CNPJ) + mesmo código interno (`cProd`) = certamente o mesmo produto. Resolve itens de um estabelecimento que o usuário já comprou antes, mesmo que a descrição tenha mudado levemente.

#### Camada 4 — Heurísticas de Texto (Custo zero, batch D+1)

Regras determinísticas, sem AI, executadas no job noturno:
- Normalizar texto: uppercase, remover acentos, trim espaços extras
- Fuzzy match (Jaro-Winkler) contra aliases existentes, threshold ≥ 0.85
- Regex para extrair padrões recorrentes: `(marca)? (produto) (quantidade)(unidade)`
- Mesmo NCM + similaridade textual alta → sugerir match com `confidence` alta

#### Camada 5 — Embeddings em Batch (Custo baixo, batch D+1)

Para itens que nenhuma camada anterior resolveu:
- Job offline (noturno ou semanal, não em tempo real)
- Gerar embedding dos itens pendentes e comparar via similaridade coseno com catálogo existente
- **~100x mais barato que LLM** por token processado
- Gera sugestões com `confidence` score na tabela `product_alias`
- Usuário confirma ou rejeita na tela de revisão

#### Camada 6 — LLM Sob Demanda (Custo alto, uso controlado)

Último recurso. Invocado **somente quando o usuário abre a tela de revisão de itens pendentes**, nunca automaticamente. O LLM recebe o item + contexto (NCM, itens similares do catálogo) e sugere classificação.

### 2.2 Mitigação de Custos AI

#### Catálogo Compartilhado (Cross-Tenant)

A maior alavanca de redução de custo. Se o usuário A mapeou `"ARROZ CAMIL T1 5KG"` → Arroz Camil 5kg, o usuário B recebe o mesmo mapeamento automaticamente via Camada 2 (alias reutilizado). Efeito de rede: quanto mais usuários, menos AI necessária.

**Privacidade:** Compartilhar apenas `raw_description → product_variant` (mapeamento anônimo). Nunca dados do usuário, valores pagos, ou informações do estabelecimento.

#### Priorização Inteligente

Não normalizar tudo indiscriminadamente. Critérios para priorizar gastos com AI:
- **Recorrência:** Itens comprados 3+ vezes têm mais valor analítico — priorizar
- **Valor:** Itens com `effective_price` < R$ 1,00 provavelmente não justificam o custo
- **Impacto:** Focar nos itens que representam maior % do gasto total do usuário

#### Rate Limiting por Conta

Limitar chamadas AI por período para controle de custo:
- Plano free: até N normalizações AI/mês (ex: 50)
- Plano premium: limite mais alto ou ilimitado
- O usuário sempre pode fazer mapeamento manual sem limite

#### Mapeamento Manual (Sempre disponível, custo zero)

Usuário pode sempre:
- Criar um produto canônico manualmente
- Vincular qualquer item de NFC-e a um produto existente
- Desagrupar itens incorretamente agrupados
- Isso vale tanto como fallback quanto como correção de sugestões erradas

### 2.3 Limitações do NCM

O NCM tem boa cobertura (~90% dos itens de supermercado), mas existem exceções:

| Caso | Exemplo | Impacto |
|---|---|---|
| **NCM genérico (catch-all)** | `21069090` "Outras preparações alimentícias" | NCM existe mas não ajuda — agrupa coisas diferentes |
| **Produtos in natura / a granel** | Hortifruti, padaria, açougue | NCM correto (`07`=hortaliças) mas descrição genérica ("HORTIFRUTI") |
| **Serviços** | Estacionamento, lavagem | Sem NCM (campo vazio ou `00000000`) |
| **NCM errado** | Erro de cadastro do emitente | Mais comum em comércios pequenos |
| **Cancelamentos (CFOP 5.929)** | Itens cancelados dentro da NFC-e | Podem vir sem NCM |

**Tratamento:** Itens sem NCM válido (`NULL`, `00000000`, ou catch-all como `21069090`, `39249000`) são excluídos das analytics por NCM mas continuam elegíveis para normalização por produto canônico via as outras camadas.

### 2.4 Conceito de Produto Canônico

Um **Produto Canônico** (`Product`) representa o conceito abstrato do produto, independente de marca, tamanho ou fornecedor:

- **Exemplo:** "Arroz Branco" é o produto canônico
  - "ARROZ CAMIL T1 5KG" → Arroz Branco, marca Camil, 5kg
  - "ARROZ PRATO FINO 1KG" → Arroz Branco, marca Prato Fino, 1kg

Isso permite responder tanto "quanto pago por arroz?" (canônico) quanto "quanto pago por arroz Camil 5kg?" (variante específica).

---

## 3. Data Model

### 3.1 Tabela `banking.products` — Produto Canônico

Representa o conceito normalizado do produto.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `account_id` | UUID | Yes | Tenant — cada usuário tem seu catálogo |
| `name` | TEXT | Yes | Nome normalizado ("Arroz Branco", "Leite Integral") |
| `ncm_code` | CHAR(8) | No | NCM code mais comum associado |
| `default_unit` | TEXT | No | Unidade padrão para comparação (KG, L, UN) |
| `category` | TEXT | No | Categoria do produto (Alimentos, Limpeza, Higiene...) |
| `created_at` | TIMESTAMPTZ | Yes | |
| `updated_at` | TIMESTAMPTZ | Yes | |

**Unique:** `(account_id, name)` — impede duplicatas no catálogo do usuário.

### 3.2 Tabela `banking.product_variants` — Variante/Apresentação

Representa uma apresentação específica (marca + tamanho + embalagem).

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `product_id` | UUID FK → products | Yes | Produto canônico pai |
| `account_id` | UUID | Yes | Tenant |
| `name` | TEXT | Yes | Nome completo ("Arroz Camil Tipo 1 5kg") |
| `brand` | TEXT | No | Marca extraída |
| `ean` | TEXT | No | Código de barras (EAN/GTIN) quando disponível |
| `quantity_value` | NUMERIC(12,4) | No | Quantidade da embalagem (ex: 500) |
| `quantity_unit` | TEXT | No | Unidade da embalagem (ex: G, ML, UN) |
| `normalized_quantity_value` | NUMERIC(12,4) | No | Quantidade convertida p/ unidade padrão (ex: 0.5 KG) |
| `normalized_quantity_unit` | TEXT | No | Unidade padrão (KG, L, UN) |
| `created_at` | TIMESTAMPTZ | Yes | |
| `updated_at` | TIMESTAMPTZ | Yes | |

**Unique:** `(account_id, ean)` WHERE ean IS NOT NULL — EAN é globalmente único.

### 3.3 Tabela `banking.product_alias` — Mapeamento de Descrição Crua → Variante

Cada descrição distinta de NFC-e (`xProd`) é um alias que aponta para uma variante.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `product_variant_id` | UUID FK → product_variants | Yes | Variante associada |
| `account_id` | UUID | Yes | Tenant |
| `raw_description` | TEXT | Yes | xProd original da NFC-e |
| `source` | TEXT | Yes | Como foi criado: MANUAL, AI_SUGGESTED, EAN_MATCH, HEURISTIC |
| `confidence` | NUMERIC(3,2) | No | Confiança do match (0.00–1.00); null para MANUAL |
| `confirmed` | BOOLEAN | Yes | Se o usuário confirmou o mapeamento |
| `created_at` | TIMESTAMPTZ | Yes | |

**Unique:** `(account_id, raw_description)` — mesma descrição sempre mapeia para a mesma variante.

### 3.4 Tabela `banking.purchase_items` — Itens de Compra

Cada item individual de cada NFC-e importada.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `transaction_id` | UUID FK → transactions | Yes | Transação pai (a compra) |
| `account_id` | UUID | Yes | Tenant |
| `product_variant_id` | UUID FK → product_variants | No | Null se ainda não normalizado |
| `sequence` | INT | Yes | Ordem do item na NFC-e (nItem, 1-based) |
| `raw_description` | TEXT | Yes | xProd original |
| `ncm_code` | CHAR(8) | No | NCM do item |
| `ean` | TEXT | No | cEAN do item ("SEM GTIN" tratado como null) |
| `issuer_product_code` | TEXT | No | cProd — código interno do produto no emissor |
| `quantity` | NUMERIC(12,4) | Yes | qCom |
| `unit` | TEXT | Yes | uCom |
| `unit_price` | NUMERIC(18,4) | Yes | vUnCom (preço unitário bruto, sem desconto) |
| `total_price` | NUMERIC(18,4) | Yes | vProd (valor bruto = qCom × vUnCom, **sem desconto**) |
| `discount` | NUMERIC(18,4) | No | vDesc (desconto aplicado neste item; default 0) |
| `effective_price` | NUMERIC(18,4) | Yes | vProd - vDesc (valor efetivamente pago; computed on insert) |
| `created_at` | TIMESTAMPTZ | Yes | |

**Regra de consistência:** `Σ(effective_price)` de todos os itens ≈ `vNF` (total da NFC-e), podendo divergir por arredondamento ou acréscimos (frete, seguro, ICMS ST).

**Index:** `(product_variant_id, created_at)` — para queries de histórico de preço.

### 3.5 Tabela `banking.nfce_documents` — Metadados do Documento

Persiste os metadados da NFC-e separadamente da transação, permitindo reprocessamento.

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | UUID v7 PK | Yes | |
| `transaction_id` | UUID FK → transactions | Yes | 1:1 com a transação |
| `account_id` | UUID | Yes | Tenant |
| `access_key` | CHAR(44) | Yes | chNFe — chave de acesso |
| `issuer_cnpj` | CHAR(14) | Yes | CNPJ do emitente |
| `issuer_name` | TEXT | Yes | Nome/razão social |
| `emission_date` | TIMESTAMPTZ | Yes | dhEmi |
| `total_value` | NUMERIC(18,4) | Yes | vNF |
| `municipality_code` | TEXT | No | cMunFG (código IBGE) |
| `municipality` | TEXT | No | xMun |
| `state` | CHAR(2) | Yes | UF |
| `xml_url` | TEXT | No | URL original do QR code |
| `created_at` | TIMESTAMPTZ | Yes | |

**Unique:** `(account_id, access_key)` — impede duplicata de NFC-e.

### 3.6 Tabela `banking.ncm_codes` — Referência NCM (lookup estático)

Tabela de referência para traduzir código NCM em nome legível. Dados públicos da Receita Federal (~14.000 linhas). Atualização anual.

| Field | Type | Notes |
|---|---|---|
| `code` | TEXT PK | Código NCM (2, 4, 6 ou 8 dígitos) |
| `description` | TEXT | Descrição oficial |
| `depth` | INT | Nível: 1=capítulo, 2=posição, 3=subposição, 4=item |

Hierarquia do NCM:

```
Capítulo  (2 dígitos)  → "Cereais"                  (10)
Posição   (4 dígitos)  → "Arroz"                     (1006)
Subposição(6 dígitos)  → "Arroz branqueado"           (1006.30)
Item      (8 dígitos)  → "Arroz parboilizado polido"  (1006.30.21)
```

---

## 4. Unidade de Medida — Normalização

Para permitir comparação de preço unitário entre diferentes apresentações, precisamos normalizar unidades para uma **unidade base** por categoria:

| Grupo | Unidades aceitas | Unidade base | Conversão |
|---|---|---|---|
| Massa | G, KG, MG | KG | 1 KG = 1000 G |
| Volume | ML, L | L | 1 L = 1000 ML |
| Unidade | UN, PCT, CX, DZ, FD, SC, PR | UN | Sem conversão automática* |
| Comprimento | M, CM, MM | M | 1 M = 100 CM |

\* Para UN-like: a conversão depende do contexto (1 PCT de 6 = 6 UN). Isso pode ser informado manualmente ou extraído da descrição.

O campo `normalized_quantity_value` + `normalized_quantity_unit` em `product_variants` armazena o resultado da conversão.

**Preço unitário normalizado** = `effective_price / normalized_quantity_value`

> **Importante:** O preço para analytics é sempre o `effective_price` (valor pago), não o `total_price` (valor bruto).
> `vProd` na NFC-e é **sempre o valor bruto (sem desconto)**. O desconto fica separado em `vDesc`.
> A soma que bate com o total da nota é: `vNF = Σ(vProd - vDesc) + frete + seguro + ICMS ST - outros descontos`.

Exemplo:
- "LEITE INTEGRAL 1L" → vProd=5.99, vDesc=0.00 → effective=5.99 → 1.0 L → R$ 5.99/L
- "LEITE PARMALAT 200ML" → vProd=3.50, vDesc=1.00 → effective=2.50 → 0.2 L → R$ 12.50/L

---

## 5. Fluxos de Usuário

### 5.1 Import de NFC-e — Fluxo Completo

O fluxo separa **persistência (síncrona)** de **normalização (assíncrona D+1)**:

```mermaid
flowchart TD
    %% ── IMPORT (SÍNCRONO) ──────────────────────────────────────
    START([Usuário escaneia QR Code]) --> FETCH[Fetch XML via URL da SEFAZ]
    FETCH --> DEDUP_NF{chNFe já existe?}
    DEDUP_NF -- Sim --> SKIP([Retorna: NFC-e já importada])
    DEDUP_NF -- Não --> PERSIST[Persistir no banco]

    PERSIST --> TX[1. Transaction — transação agregada]
    PERSIST --> DOC[2. NfceDocument — metadados da NF-e]
    PERSIST --> ITEMS[3. PurchaseItems — itens crus]

    ITEMS --> LOOP[[Para cada item da NFC-e]]

    %% ── CAMADAS SÍNCRONAS (lookups instantâneos) ───────────────
    LOOP --> C1{Camada 1: EAN existe\nno catálogo interno?}
    C1 -- Sim --> RESOLVED([✓ product_variant_id setado])
    C1 -- Não / SEM GTIN --> C2{Camada 2: raw_description\njá é um alias conhecido?}
    C2 -- Sim --> RESOLVED
    C2 -- Não --> C3{Camada 3: mesmo CNPJ +\ncProd já mapeado?}
    C3 -- Sim --> RESOLVED
    C3 -- Não --> PENDING([⏳ Salva com product_variant_id = null])

    %% ── BATCH D+1 (OFFLINE) ────────────────────────────────────
    PENDING -.-> BATCH[[Job Batch D+1 — diário às 03:00]]

    BATCH --> B_LOAD[Buscar purchase_items\nWHERE product_variant_id IS NULL]
    B_LOAD --> C4{Camada 4: Heurísticas\nde texto — fuzzy match,\nregex, NCM + similaridade}
    C4 -- Match ≥ 0.85 --> ALIAS_H[Criar alias\nsource=HEURISTIC\nconfirmed=false]
    ALIAS_H --> RESOLVED_B([✓ product_variant_id setado])
    C4 -- Sem match --> C5{Camada 5: Embeddings\nsimilaridade coseno\ncontra catálogo}
    C5 -- Match com confiança --> ALIAS_E[Criar alias\nsource=AI_SUGGESTED\nconfirmed=false]
    ALIAS_E --> RESOLVED_B
    C5 -- Sem match --> STILL_PENDING([⏳ Permanece pendente])

    %% ── SOB DEMANDA DO USUÁRIO ─────────────────────────────────
    STILL_PENDING -.-> USER[[Usuário abre tela de revisão]]
    USER --> C6{Camada 6: LLM\nsob demanda}
    C6 --> SUGGEST[Exibe sugestão com\nscore de confiança]
    SUGGEST --> CONFIRM{Usuário aceita?}
    CONFIRM -- Sim --> ALIAS_M[Criar alias\nconfirmed=true]
    ALIAS_M --> RESOLVED_U([✓ Mapeado])
    CONFIRM -- Não --> MANUAL{Usuário mapeia\nmanualmente ou ignora}
    MANUAL -- Mapeia --> ALIAS_MANUAL[Criar alias\nsource=MANUAL\nconfirmed=true]
    ALIAS_MANUAL --> RESOLVED_U
    MANUAL -- Ignora --> IGNORED([Item sem produto\n— entra em analytics por NCM])

    %% ── ESTILOS ────────────────────────────────────────────────
    style START fill:#4CAF50,color:#fff
    style SKIP fill:#FF9800,color:#fff
    style RESOLVED fill:#2196F3,color:#fff
    style RESOLVED_B fill:#2196F3,color:#fff
    style RESOLVED_U fill:#2196F3,color:#fff
    style PENDING fill:#FFC107,color:#000
    style STILL_PENDING fill:#FFC107,color:#000
    style IGNORED fill:#9E9E9E,color:#fff
```

### 5.2 Normalização de Itens Pendentes

Itens com `product_variant_id = null` ou `confirmed = false` ficam acessíveis na tela de revisão:

1. **Itens com sugestão (do batch):** Exibe sugestão com score de confiança, usuário confirma ou rejeita
2. **Itens sem sugestão:** Usuário pode:
   - Pedir sugestão AI (camada 6, sob demanda)
   - Escolher produto existente do catálogo → cria alias
   - Criar novo produto + variante → cria tudo e alias
   - Ignorar → item fica sem produto (não entra em analytics de produto, mas entra em analytics por NCM)

### 5.3 Deduplicação NFC-e ↔ Transação Manual

**Cenário 1: Usuário criou transação manual, depois importa NFC-e**
- Dedup por: mesmo `financial_account_id` + `date` ± 1 dia + `amount` igual
- Se match encontrado → oferecer ao usuário:
  - "Encontramos uma transação de R$ 157,32 em 20/03 que pode ser a mesma compra. Vincular?"
  - Se sim → enriquece a transação existente com dados da NFC-e (itens, CNPJ, etc.)
  - Se não → cria nova transação

**Cenário 2: Usuário importou NFC-e, depois tenta importar de novo**
- Dedup por `access_key` (chNFe) — já implementado hoje via `SameSourceDedupService`

**Cenário 3: Usuário importou NFC-e, depois cria transação manual similar**
- Não bloquear — transação manual é sempre permitida
- Opcionalmente: mostrar aviso "Você já tem uma compra de R$ X no mesmo dia neste estabelecimento"

### 5.4 Criação de Movimentação por Item (import granular)

Para o cenário onde o usuário quer controle fino, oferecer opção de **importar por item**:

- Em vez de 1 transação de R$ 350 no Supermercado, criar N transações (uma por item)
- Cada transação filha vinculada ao `nfce_document`
- Isso facilita categorização (item de limpeza vs alimento) e evita duplicidade granular
- **Configurável por usuário** — nem todos vão querer isso

---

## 6. Campos Adicionais no Parser NFC-e

O parser atual (`NfceItem`) extrai:
- `xProd`, `vProd`, `qCom`, `uCom`, `ncm`

Precisamos extrair adicionalmente:

| Campo | Descrição | Prioridade |
|---|---|---|
| `cEAN` / `cEANTrib` | Código de barras EAN/GTIN — melhor identificador único de produto | Essencial |
| `vUnCom` | Valor unitário comercial — preço sem risco de arredondamento | Importante |
| `vDesc` | Valor do desconto no item — necessário para calcular effective_price | Importante |
| `nItem` | Número sequencial do item no XML — ordenação/referência | Nice to have |
| `cProd` | Código interno do produto no emissor — dedup por estabelecimento (camada 3) | Nice to have |

E no nível do documento:
- `cMunFG` — código IBGE do município (para análise regional)

---

## 7. GraphQL API

### 7.1 Tipos

```graphql
type Product {
  id: ID!
  name: String!
  ncmCode: String
  defaultUnit: String
  category: String
  variants: [ProductVariant!]!
  # Analytics
  averagePrice: BigDecimal          # preço médio unitário normalizado
  priceHistory: [PricePoint!]!      # série temporal
  totalSpent: BigDecimal            # total gasto neste produto
  purchaseCount: Int!               # quantas vezes comprou
}

type ProductVariant {
  id: ID!
  product: Product!
  name: String!
  brand: String
  ean: String
  quantityValue: BigDecimal
  quantityUnit: String
  normalizedQuantityValue: BigDecimal
  normalizedQuantityUnit: String
  averageUnitPrice: BigDecimal      # preço médio normalizado desta variante
}

type PurchaseItem {
  id: ID!
  transaction: Transaction!
  productVariant: ProductVariant
  sequence: Int!
  rawDescription: String!
  ncmCode: String
  ean: String
  quantity: BigDecimal!
  unit: String!
  unitPrice: BigDecimal!
  totalPrice: BigDecimal!           # vProd — valor bruto (sem desconto)
  discount: BigDecimal              # vDesc — desconto no item
  effectivePrice: BigDecimal!       # vProd - vDesc — valor efetivamente pago
  normalizedUnitPrice: BigDecimal   # computed: effectivePrice / normalized qty
}

type PricePoint {
  date: Date!
  unitPrice: BigDecimal!
  storeName: String
  municipality: String
  state: String
}

type NfceDocument {
  id: ID!
  transaction: Transaction!
  accessKey: String!
  issuerCnpj: String!
  issuerName: String!
  emissionDate: DateTime!
  totalValue: BigDecimal!
  municipality: String
  state: String!
  items: [PurchaseItem!]!
}

type UnmappedItemGroup {
  rawDescription: String!
  count: Int!
  suggestedProduct: Product
  suggestedVariant: ProductVariant
  confidence: Float
  sampleItems: [PurchaseItem!]!
}

type NcmSpending {
  ncmCode: String!
  ncmDescription: String            # nome legível da tabela de lookup
  totalSpent: BigDecimal!
  itemCount: Int!
  months: [NcmMonthlySpending!]!
}

type NcmMonthlySpending {
  month: Date!
  totalSpent: BigDecimal!
  itemCount: Int!
}
```

### 7.2 Queries

```graphql
type Query {
  # Catálogo de produtos
  products(accountId: ID!, search: String, category: String): [Product!]!
  product(id: ID!): Product

  # Itens de uma transação
  purchaseItems(transactionId: ID!): [PurchaseItem!]!

  # Documento NFC-e
  nfceDocument(transactionId: ID!): NfceDocument

  # Itens pendentes de normalização
  unmappedItems(accountId: ID!, limit: Int): [UnmappedItemGroup!]!

  # Analytics — Fase 1
  productPriceHistory(
    productId: ID!
    variantId: ID
    startDate: Date
    endDate: Date
  ): [PricePoint!]!

  productConsumptionTrend(
    productId: ID!
    periodMonths: Int = 6
  ): ConsumptionTrend!

  # Analytics por NCM (automático, sem normalização)
  spendingByNcm(
    accountId: ID!
    depth: Int = 4          # 2=capítulo, 4=posição, 6=subposição, 8=item
    startDate: Date
    endDate: Date
  ): [NcmSpending!]!
}
```

### 7.3 Mutations

```graphql
type Mutation {
  # Produto canônico
  createProduct(input: CreateProductInput!): Product!
  updateProduct(id: ID!, input: UpdateProductInput!): Product!
  mergeProducts(sourceId: ID!, targetId: ID!): Product!

  # Variante
  createProductVariant(input: CreateProductVariantInput!): ProductVariant!
  updateProductVariant(id: ID!, input: UpdateProductVariantInput!): ProductVariant!

  # Mapeamento de itens
  mapItemToVariant(rawDescription: String!, variantId: ID!): ProductAlias!
  confirmAiSuggestion(aliasId: ID!, accepted: Boolean!): ProductAlias!
  unmapItem(aliasId: ID!): Boolean!

  # Import NFC-e (atualizado)
  importNfce(financialAccountId: ID!, qrCodeUrl: String!): ImportResult!
}
```

---

## 8. Análises — Fase 1

### 8.1 Preço Médio do Produto

```sql
SELECT
  pv.product_id,
  p.name AS product_name,
  AVG(pi.effective_price / pv.normalized_quantity_value) AS avg_unit_price,
  MIN(pi.effective_price / pv.normalized_quantity_value) AS min_unit_price,
  MAX(pi.effective_price / pv.normalized_quantity_value) AS max_unit_price,
  COUNT(*) AS purchase_count
FROM banking.purchase_items pi
JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
JOIN banking.products p ON p.id = pv.product_id
JOIN banking.transactions t ON t.id = pi.transaction_id
WHERE t.account_id = :accountId
  AND t.deleted_at IS NULL
  AND t.date BETWEEN :startDate AND :endDate
GROUP BY pv.product_id, p.name;
```

### 8.2 Tendência de Consumo

```sql
SELECT
  DATE_TRUNC('month', t.date) AS month,
  SUM(pi.quantity) AS total_quantity,
  SUM(pi.effective_price) AS total_spent,
  COUNT(DISTINCT t.id) AS purchase_count
FROM banking.purchase_items pi
JOIN banking.transactions t ON t.id = pi.transaction_id
WHERE pi.product_variant_id IN (
  SELECT id FROM banking.product_variants WHERE product_id = :productId
)
AND t.account_id = :accountId
AND t.deleted_at IS NULL
GROUP BY DATE_TRUNC('month', t.date)
ORDER BY month;
```

### 8.3 Comparação entre Estabelecimentos

Na Fase 1, sem dados regionais externos, a comparação é **entre estabelecimentos do próprio usuário**:

```sql
SELECT
  nd.issuer_name AS store_name,
  nd.municipality,
  AVG(pi.effective_price / pv.normalized_quantity_value) AS avg_unit_price,
  COUNT(*) AS purchase_count
FROM banking.purchase_items pi
JOIN banking.product_variants pv ON pv.id = pi.product_variant_id
JOIN banking.transactions t ON t.id = pi.transaction_id
JOIN banking.nfce_documents nd ON nd.transaction_id = t.id
WHERE pv.product_id = :productId
  AND t.account_id = :accountId
  AND t.deleted_at IS NULL
  AND t.date BETWEEN :startDate AND :endDate
GROUP BY nd.issuer_name, nd.municipality
ORDER BY avg_unit_price;
```

Resultado: *"Você paga 15% menos por arroz no Atacadão vs Pão de Açúcar"*.

### 8.4 Analytics por NCM (automático, sem normalização)

Funciona **desde o dia zero**, sem que o usuário normalize nenhum produto:

```sql
SELECT
  LEFT(pi.ncm_code, :depth) AS ncm_group,   -- 2, 4, 6 ou 8 dígitos
  nc.description AS ncm_description,
  SUM(pi.effective_price) AS total_spent,
  COUNT(*) AS item_count,
  DATE_TRUNC('month', t.date) AS month
FROM banking.purchase_items pi
JOIN banking.transactions t ON t.id = pi.transaction_id
LEFT JOIN banking.ncm_codes nc ON nc.code = LEFT(pi.ncm_code, :depth)
WHERE t.account_id = :accountId
  AND t.deleted_at IS NULL
  AND pi.ncm_code IS NOT NULL
  AND pi.ncm_code NOT IN ('00000000')
  AND t.date BETWEEN :startDate AND :endDate
GROUP BY LEFT(pi.ncm_code, :depth), nc.description, DATE_TRUNC('month', t.date)
ORDER BY total_spent DESC;
```

#### Complementaridade NCM × Produto canônico

| Dimensão | NCM | Produto canônico |
|---|---|---|
| Esforço do usuário | Zero | Requer mapeamento |
| Granularidade | Grossa (tipo fiscal) | Fina (marca + apresentação) |
| Cobertura | ~90% dos itens NFC-e | Apenas itens mapeados |
| Preço unitário normalizado | Não (mistura apresentações) | Sim (normalizado por kg/L/un) |
| Disponível desde | Dia zero | Após normalização |

**Estratégia:** NCM fornece o panorama geral automático; produto canônico adiciona precisão onde o usuário investir esforço.

---

## 9. Faseamento de Implementação

### Fase 0 — Infraestrutura de Dados (pré-requisito)
1. Migration: criar tabelas `products`, `product_variants`, `product_alias`, `purchase_items`, `nfce_documents`
2. Migration: criar tabela `ncm_codes` (lookup estático) e popular via seed (~14.000 linhas, dados públicos Receita Federal)
3. Expandir parser NFC-e: extrair `cEAN`, `vUnCom`, `vDesc`, `cProd`, `nItem`, `cMunFG`
4. Persistir `nfce_documents` e `purchase_items` no fluxo de import
5. Atualizar `NfceItem` domain model

### Fase 1A — Catálogo de Produtos + Normalização Síncrona
1. CRUD de `Product` e `ProductVariant`
2. Mapeamento manual: tela de itens não mapeados
3. Normalização síncrona no import (camadas 1-3: EAN, alias, CNPJ+cProd)
4. GraphQL queries e mutations básicas

### Fase 1B — Job Batch D+1 + Heurísticas
1. Job scheduled (diário) para itens pendentes
2. Camada 4: heurísticas de texto (fuzzy match, regex, NCM+similaridade)
3. Analytics por NCM (funciona desde aqui, sem normalização)

### Fase 1C — Normalização AI-Assisted
1. Camada 5: serviço de embeddings em batch
2. Camada 6: LLM sob demanda na tela de revisão
3. Fila de revisão com sugestões pendentes
4. Aprendizado: aliases confirmados alimentam camadas 1-2 para futuras importações

### Fase 1D — Analytics de Preço
1. Histórico de preço por produto/variante
2. Tendência de consumo mensal
3. Comparação entre estabelecimentos (mesmo usuário)
4. Dashboard de produtos

### Fase 2A — Deduplicação NFC-e ↔ Manual
1. Matching por data + valor + conta
2. UX de confirmação/vinculação
3. Import granular (1 transação por item) como opção

### Fase 2B — Inflação Pessoal & Custo de Vida
1. Cálculo de índice de inflação pessoal (cesta do usuário vs IPCA)
2. Integração com dados regionais (IBGE/POF)
3. Simulação de custo de vida por município

---

## 10. Questões em Aberto

1. **Granularidade do import:** Default será 1 transação por NFC-e (como hoje) ou 1 por item? Ou configurável?
2. **Produtos compartilhados (cross-tenant):** O catálogo de aliases é compartilhado entre tenants para efeito de rede. Mas o catálogo de produtos canônicos (`products`) é por `account_id`. Confirmar que essa separação é adequada.
3. **Retroatividade:** Ao mapear um alias, atualizar retroativamente todos os `purchase_items` com aquela `raw_description` que estavam com `product_variant_id = null`?
4. **NF-e vs NFC-e:** Vamos suportar NF-e modelo 55 (compras online, B2B) além da NFC-e modelo 65 (varejo presencial)? A estrutura XML é similar mas o fluxo de obtenção é diferente.
5. **Limites de armazenamento:** Uma NFC-e de supermercado pode ter 50+ itens. Em 1 ano de compras semanais → ~2.600 purchase_items/usuário. Gerenciável, mas analytics pesadas devem usar views materializadas?
6. **ICMS ST e acréscimos:** `effective_price = vProd - vDesc` está definido. Porém, `vICMSST` (substituição tributária) e `vFrete`/`vSeg` (frete/seguro rateados) também compõem o valor final. Ignorar na v1 ou incluir?
