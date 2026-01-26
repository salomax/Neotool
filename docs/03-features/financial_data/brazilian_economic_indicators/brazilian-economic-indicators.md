# Feature: brazilian-economic-indicators

**Type**: Financial Data Feature  
**Module**: financial_data  
**Status**: Draft

## Overview

The **brazilian-economic-indicators** feature ingests and maintains the most searched **Brazilian national market indicators**, providing a consistent time-series catalog for macro, inflation, and interest rate analytics.

This feature is focused on **data acquisition, normalization, and storage** of official and widely used public indicators.

---

## Goals

- Provide reliable ingestion for the main Brazilian inflation, interest rate, and macroeconomic indicators.
- Normalize sources and periodicities for consistent downstream consumption.
- Enable derived analytics such as real interest rate.

---

## Functional Requirements

> Each requirement must store indicator metadata (name, description, periodicity, publisher, source, dataset/series code when applicable) and persist time-series values with reference date and publication date.

### 1) Inflation Indexes (Most Searched)

- The system must ingest **IPCA (Indice Nacional de Precos ao Consumidor Amplo)**.
  - Periodicity: monthly
  - Publisher: IBGE
  - Source: SIDRA / IBGE API
  - Dataset: IPCA - Indice Geral
  - Note: base for Copom targets and Tesouro IPCA+

- The system must ingest **IPCA-15**.
  - Periodicity: monthly
  - Publisher: IBGE
  - Source: SIDRA / IBGE API
  - Note: IPCA preview; high search volume for "IPCA-15 hoje"

- The system must ingest **INPC (Indice Nacional de Precos ao Consumidor)**.
  - Periodicity: monthly
  - Publisher: IBGE
  - Source: SIDRA / IBGE API
  - Note: used for wage and benefit adjustments

- The system must ingest **IGP-M (Indice Geral de Precos - Mercado)**.
  - Periodicity: monthly
  - Publisher: FGV
  - Source: FGV Dados (public access)
  - Note: widely used for rent adjustments

- The system must ingest **IGP-DI (Indice Geral de Precos - Disponibilidade Interna)**.
  - Periodicity: monthly
  - Publisher: FGV
  - Source: FGV Dados

- The system must ingest **IGP-10**.
  - Periodicity: monthly
  - Publisher: FGV
  - Source: FGV Dados

- The system must ingest **IPC-FIPE**.
  - Periodicity: monthly
  - Publisher: FIPE
  - Source: Portal FIPE
  - Note: city of Sao Paulo inflation index (regional)

- The system must ingest **IPC-S**.
  - Periodicity: weekly
  - Publisher: FGV
  - Source: FGV Dados

### 2) Interest Rates (Core of the Financial System)

- The system must ingest **Selic Target Rate (Taxa Selic Meta)**.
  - Periodicity: event-driven (Copom decisions)
  - Publisher: Banco Central do Brasil
  - Source: SGS API (series code to be defined)

- The system must ingest **Selic Effective (Over)**.
  - Periodicity: daily
  - Publisher: Banco Central do Brasil
  - Source: SGS API (specific series code)

- The system must ingest **CDI (Certificado de Deposito Interbancario)**.
  - Periodicity: daily
  - Publisher: B3
  - Source: B3 public data (may be mirrored in SGS)

- The system must ingest **DI Over**.
  - Periodicity: daily
  - Publisher: BCB / B3
  - Source: SGS API

- The system must ingest **DI Curve (DI Futuro)**.
  - Periodicity: daily
  - Publisher: B3
  - Source: B3 futures market public data
  - Note: used for valuation, risk, and macro scenarios

- The system must calculate **Real Interest Rate (Taxa Real de Juros)** as a derived indicator.
  - Periodicity: derived (aligned to Selic periodicity)
  - Publisher: internal calculation
  - Source inputs: Selic (BCB) and IPCA (IBGE)
  - Formula: Real Rate = Selic - Expected Inflation (use latest available IPCA as proxy unless expectation series is configured)

### 3) Macro Economic Indicators

- The system must ingest **PIB (Produto Interno Bruto)**.
  - Periodicity: quarterly / annual
  - Publisher: IBGE
  - Source: SIDRA / IBGE API

- The system must ingest **PIB per capita**.
  - Periodicity: annual
  - Publisher: IBGE
  - Source: SIDRA / IBGE API

- The system must ingest **Unemployment Rate (PNAD Continua)**.
  - Periodicity: monthly
  - Publisher: IBGE
  - Source: SIDRA / IBGE API

- The system must ingest **Average Labor Income (Renda Media do Trabalho)**.
  - Periodicity: monthly
  - Publisher: IBGE
  - Source: PNAD Continua (SIDRA)

- The system must ingest **Public Debt / GDP (Divida Publica / PIB)**.
  - Periodicity: monthly
  - Publisher: Banco Central do Brasil
  - Source: SGS API

- The system must ingest **Primary Result (Resultado Primario)**.
  - Periodicity: monthly
  - Publisher: Banco Central do Brasil / Tesouro Nacional
  - Source: SGS API

- The system must ingest **Trade Balance (Balanca Comercial)**.
  - Periodicity: monthly
  - Publisher: Ministerio do Desenvolvimento, Industria, Comercio e Servicos
  - Source: Comex Stat portal

- The system must ingest **Current Account (Conta Corrente)**.
  - Periodicity: monthly
  - Publisher: Banco Central do Brasil
  - Source: SGS API

- The system must ingest **International Reserves (Reservas Internacionais)**.
  - Periodicity: daily / monthly
  - Publisher: Banco Central do Brasil
  - Source: SGS API

---

## Scheduling and Normalization Rules

- The system must schedule ingestion by indicator periodicity (daily, weekly, monthly, quarterly, annual, or event-driven).
- The system must store each series with a **reference date** (period end) and **publication date** (as provided by source or ingestion timestamp).
- The system must normalize units and scale (e.g., percentage vs index points) and persist metadata for downstream formatting.
- The system must support backfill for historical data ranges per indicator source.

---

## Non-Goals (for now)

- Forecasting or predictive analytics of indicators
- Proprietary paid data sources
- Real-time streaming for market quotes
