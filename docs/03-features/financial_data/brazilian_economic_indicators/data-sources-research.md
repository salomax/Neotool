# Brazilian Economic Indicators - Data Sources Research

**Type**: Research Note
**Module**: financial_data
**Status**: Draft

## Purpose

Document how to obtain data for the **brazilian-economic-indicators** feature, listing
official sources, access methods, and practical constraints discovered.

---

## 1) IBGE / SIDRA (IPCA, IPCA-15, INPC, PIB, PIB per capita, Desemprego, Renda Media)

**Primary source**: IBGE SIDRA API and IBGE Agregados API.

**Where**:
- SIDRA API base: https://apisidra.ibge.gov.br/
- SIDRA help: https://apisidra.ibge.gov.br/home/ajuda
- Agregados API docs: https://servicodados.ibge.gov.br/api/docs/agregados?versao=3

**How (SIDRA API)**:
- SIDRA API is URL-based. You must know the **table code** for each indicator.
- Query format is composed with parameters like:
  - `/t/{table}` (table code)
  - `/p/{period}` (periods: `all`, `last`, `first N`, `last N`)
  - `/n1`, `/n2`, `/v`, `/c` depending on table dimensions
- Example format (from SIDRA help): `/t/1612/n2/all/v/all/p/last/c81/2702/f/u`

**How (Agregados API)**:
- Use `/api/v3/agregados/{agregado}/variaveis` and `/api/v3/agregados/{agregado}/periodos` to discover
  variable codes and available periods.
- Use `/api/v3/agregados/{agregado}/variaveis/{variavel}` (or `/periodos/{periodos}/variaveis/{variavel}`) to query data.

**Notes**:
- Use SIDRA to **discover the correct table codes** for each indicator series.
- Prefer **monthly/quarterly** aggregation endpoints for performance and stability.

---

## 1.1) SIDRA Table Codes (Initial Mapping)

**Inflation (SIDRA / IPCA):**
- **IPCA**: SIDRA table **1737** (IPCA - Serie Historica). Numero-indice VAR_CODE **2266**.
- **IPCA-15**: SIDRA table **3065** (IPCA-15 - Serie Historica). Numero-indice VAR_CODE **1117**.
- **INPC**: SIDRA table **1736** (INPC - Serie Historica). Numero-indice VAR_CODE **2289**.
  - **Monthly variation**: IPCA VAR_CODE **63**, IPCA-15 VAR_CODE **355**, INPC VAR_CODE **44**.
  - **YTD (acumulado no ano)**: IPCA VAR_CODE **69**, IPCA-15 VAR_CODE **356**, INPC VAR_CODE **68**.
  - **12-month (acumulado 12 meses)**: IPCA VAR_CODE **2265**, IPCA-15 VAR_CODE **1120**, INPC VAR_CODE **2292**.

**PNAD Continua (SIDRA / trabalho):**
- **Taxa de desocupacao (unemployment rate)**: SIDRA table **6381** (PNAD Continua - taxa de desocupacao). (Table code from PNADC tables list)
- **Renda media do trabalho**: SIDRA table **6387** (rendimento medio habitualmente recebido) or **6390** (rendimento medio efetivamente recebido). Choose one standard and keep consistent. (Table codes from PNADC tables list)

**PIB / Contas Nacionais:**
- **PIB trimestral**: SIDRA CNT table **1620** (indice de volume trimestral) is a stable reference for quarterly PIB series. (Table code from CNT tables list)
- **PIB anual + PIB per capita**: SIDRA CNA table **6784** (PIB anual + PIB per capita). (Table code from CNA tables list)

> **Note**: For each SIDRA table, variables (`/v/`) and classifications (`/c/`) must be selected based on the desired measure. Use the SIDRA table interface to confirm the exact variable codes for "indice", "variacao", or "valor".

---

## 2) Banco Central do Brasil (BCB) - SGS API

**Primary source**: BCB SGS (Sistema Gerenciador de Series Temporais).

**Where**:
- SGS public portal: https://www3.bcb.gov.br/sgspub/
- BCB Open Data portal: https://dadosabertos.bcb.gov.br/
- API base pattern (SGS): `https://api.bcb.gov.br/dados/serie/bcdata.sgs.{SERIE_ID}/dados`
  - Metadata page pattern: `https://www3.bcb.gov.br/sgspub/consultarmetadados/consultarMetadadosSeries.do?method=consultarMetadadosSeriesInternet&hdOidSerieSelecionada={SERIE_ID}`

**How**:
- Use the **BCB Open Data portal** or **SGS portal** to find the series IDs (SERIE_ID).
- API supports JSON/CSV; use date filters:
  - `?formato=json&dataInicial=dd/mm/aaaa&dataFinal=dd/mm/aaaa`

**Important constraint (daily series)**:
- Since **26/03/2025**, requests for **daily series** in JSON/CSV require date filters and
  are limited to **10 years per query**. Plan chunked backfills.

**Indicators that can be covered via SGS**:
- Selic Meta (target rate)
- Selic Efetiva (Over)
- DI Over
- CDI (available in SGS, despite being published by B3)
- Divida Publica / PIB
- Resultado Primario
- Conta Corrente
- Reservas Internacionais

---

## 2.1) SGS Series IDs (Initial Mapping)

**Selic (BCB / SGS):**
- **Taxa Selic diaria**: SGS **11** (Taxa de juros - Selic, diaria). Use as the base daily Selic series.
- **Selic anualizada (base 252)**: SGS **1178** (Taxa de juros - Selic anualizada base 252). Useful when annualized effective rate is required.

**Setor externo (Conta Corrente):**
- **Transacoes correntes - saldo**: SGS **22701** (Transacoes correntes, saldo - US$ milhoes). Use as proxy for "Conta Corrente".

**Divida / PIB:**
- **Divida bruta do governo geral (% PIB)**: SGS **13762** (metodologia a partir de 2008). Use as default "Divida Publica / PIB".

**Reservas internacionais:**
- **Reservas internacionais - conceito caixa - total - diaria**: SGS **13621**. Use for daily or monthly aggregation.

**Resultado primario**:
- Multiple SGS series exist for fiscal results. Current mapping uses SGS **4649** (NFSP - Resultado primario - total - setor publico consolidado - R$ milhoes). Confirm the scope if needed.

**Selic Meta**:
- SGS **432** is commonly referenced for Selic target rate (pending confirmation via SGS metadata lookup).

> **Note**: The SGS series IDs above are verified via the BCB open data portal (SGS series pages/resources).
> Remaining series for **Selic Meta**, **CDI**, and **DI Over** must be confirmed via SGS portal search and standardized.

---

## 3) FGV IBRE / FGV Dados (IGP-M, IGP-DI, IGP-10, IPC-S)

**Primary source**: FGV Dados portal.

**Where**:
- Portal: https://portalibre.fgv.br/fgv-dados
- IPC-S overview: https://portalibre.fgv.br/en/estudos-e-pesquisas/indices-de-precos/ipc-s

**How**:
- FGV provides a **portal-based access** to historical series.
- The portal indicates **aggregated indices** are free; **granular detail** may require subscription.

**Notes**:
- IPC-S has **weekly** collection and publication, with release typically one day after close.
- Confirm if FGV offers an **official API** or only portal access.

**Alternative (recommended for redistribution): Ipeadata (OData API)**

Ipeadata provides public OData endpoints for several FGV indices. The job can use the
series codes below and fetch directly from:
`http://www.ipeadata.gov.br/api/odata4/ValoresSerie(SERCODIGO='{SERCODIGO}')`

Mapped series (FGV via Ipeadata):
- **IGP-M (monthly change)**: `IGP12_IGPMG12`
- **IGP-M (index, points)**: `IGP12_IGPM12` (used to compute YTD and rolling 12m)
- **IGP-M (YTD)**: computed from `IGP12_IGPM12` vs Dec of previous year
- **IGP-M (rolling 12m)**: computed from `IGP12_IGPM12` vs 12 months prior
- **IGP-DI (12m change)**: `IGP12_IGPDI12`
- **IGP-10 (index)**: TODO (confirm Ipeadata series code)

**IPC-S**: not found on Ipeadata catalog yet. If required, we can:
- keep IPC-S pending until a valid Ipeadata series is confirmed, or
- use another public source with redistribution rights.

---

## 4) FIPE (IPC-FIPE)

**Primary source**: FIPE IPC portal.

**Where**:
- IPC-FIPE page: https://www.fipe.org.br/pt-br/indices/ipc
- FIPE contact (IPC): https://www.fipe.org.br/en-us/institutional/contact-us/

**How**:
- FIPE provides a **web interface** for IPC series selection (mensal, quadrissemana, acumulados).
- No public API is documented on the IPC page; data extraction may require **scraping** or
  direct data request via FIPE contact.

**Notes**:
- Consider negotiating data access if automated ingestion is required.

---

## 5) B3 (CDI, DI Curve / DI Futuro)

**Primary source**: B3 market data.

**How**:
- **CDI**: prefer SGS series from BCB for daily values.
- **DI Curve (DI Futuro)**: requires **B3 futures quotes** (DI1), typically via B3 market data
  services or authorized vendors.

**Notes**:
- Data access terms may require contracts or licensed feeds.
- Confirm the internal policy for using B3 market data or third-party vendors.

---

## 6) Comex Stat (Balanca Comercial)

**Primary source**: Comex Stat API (MDIC/SECEX).

**Where**:
- API base: https://api-comexstat.mdic.gov.br
- Docs (Swagger-style): https://api-comexstat.mdic.gov.br/docs#/
- Example endpoint (years): `/general/dates/years`

**How**:
- Main data query is **POST** to `/general` with filters and details.
- Supports monthly data from 1997+ and multiple filters (country, state, NCM, etc.).

**Notes**:
- Government announcements confirm an official **API service** for Comex Stat.

---

## 7) Derived Indicator

**Real Interest Rate (Taxa Real de Juros)**:
- Compute as **Selic - Inflation**.
- Use **Selic (SGS)** and **IPCA (SIDRA)**.
- If inflation expectations are required later, consider BCB Focus/Expectativas OData.

---

## Implementation Notes (Cross-Cutting)

- **Series registry**: Create a registry table mapping indicator -> source -> endpoint -> series ID/table code.
- **Chunking**: Respect BCB 10-year window for daily series; backfill in slices.
- **Idempotency**: Always upsert by (indicator, reference_date).
- **Metadata**: Store units and periodicity for consistent API output.

---

## Bruno Request Examples

Example requests were added to the Bruno collection:

`invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators`

Requests included:
- BCB SGS (confirmed from BCB Open Data portal): Selic daily (serie 11), Reserves (serie 13621)
- BCB SGS (templates): Series metadata lookup, Series data lookup, Selic annualized 252 (serie 1178), Debt/GDP (serie 13762), Current Account (serie 22701)
- IBGE Agregados (discovery): Variables per table for IPCA (1737), IPCA-15 (3065), INPC (1736), PNAD unemployment (6381), renda media (6387/6390), PIB quarterly (1620), PIB anual/per capita (6784)
- IBGE SIDRA templates: IPCA (1737), IPCA-15 (3065), INPC (1736), PNAD unemployment (6381), PIB quarterly (1620)
- IBGE SIDRA (10-year history, monthly): IPCA monthly/YTD/12M, IPCA-15 monthly/YTD/12M, INPC monthly/YTD/12M
- Comex Stat template: Trade Balance (POST /general)

SIDRA requests are **templates** and require confirmation of variable codes (`{VAR_CODE}`) via Agregados API.
For 10-year history of monthly series, use `p/last 120`.

---

## Indicator x Variacao x Bruno Request

| Indicator | Variacao | Bruno request |
| --- | --- | --- |
| IPCA | Variação mensal | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA Monthly Variation (Table 1737 VAR 63).bru` |
| IPCA | Acumulado no ano (YTD) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA YTD (Table 1737 VAR 69).bru` |
| IPCA | Acumulado 12 meses | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA 12M (Table 1737 VAR 2265).bru` |
| IPCA-15 | Variação mensal | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA-15 Monthly Variation (Table 3065 VAR 355).bru` |
| IPCA-15 | Acumulado no ano (YTD) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA-15 YTD (Table 3065 VAR 356).bru` |
| IPCA-15 | Acumulado 12 meses | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - IPCA-15 12M (Table 3065 VAR 1120).bru` |
| INPC | Variação mensal | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - INPC Monthly Variation (Table 1736 VAR 44).bru` |
| INPC | Acumulado no ano (YTD) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - INPC YTD (Table 1736 VAR 68).bru` |
| INPC | Acumulado 12 meses | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - INPC 12M (Table 1736 VAR 2292).bru` |
| Selic | Meta (Copom) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - Selic Target (Serie 432).bru` |
| Selic | Efetiva (diária) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - Selic Daily (Serie 11).bru` |
| Conta Corrente | Valor (US$ mi) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - Current Account Balance (Serie 22701).bru` |
| Reservas Internacionais | Valor (US$ mi) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - International Reserves (Serie 13621).bru` |
| Dívida/PIB | % PIB | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - Public Debt GDP (Serie 13762).bru` |
| Resultado Primário | Valor (R$ mi) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/BCB SGS - Primary Result (Serie 4649).bru` |
| PIB | Valor (corrente, anual) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - PIB Current Values (Table 6784 VAR 9808).bru` |
| Desemprego | Taxa (%) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - Unemployment Rate (Table 6381 VAR 4099).bru` |
| Renda média | Habitual (R$) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - Average Income Habitual (Table 6390 VAR 5933).bru` |
| Renda média | Efetiva (R$) | `invistus/contracts/api-collections/bruno/collections/external_api/Financial Data/Indicators/IBGE SIDRA - Average Income Effective (Table 6387 VAR 5935).bru` |

---

**Note**: Selic Meta series code (432) is pending confirmation via BCB SGS metadata. PIB here uses the annual current values series from table 6784.

## Open Items / To Confirm

1. Confirm **SIDRA variables/classifications** per table (e.g., index vs variation).
2. Confirm SGS series IDs for **Selic Meta**, **CDI**, and **DI Over** (standardize and document).
3. Select the official **Resultado Primario** series scope (setor publico consolidado vs other scopes).
4. Validate official **FGV data access method** (API vs portal extraction) for IGP-M, IGP-DI, IGP-10, IPC-S.
5. Validate official **FIPE data access** for IPC-FIPE (API availability or licensing).
6. Confirm **B3 DI futures** access path and licensing for DI curve construction.
