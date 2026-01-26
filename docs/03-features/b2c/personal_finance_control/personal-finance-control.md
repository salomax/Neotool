# Feature: personal-finance-control

**Type**: B2C Product Feature  
**Module**: b2c  
**Status**: Draft

## Overview

The **personal-finance-control** feature enables individuals and families to manage **financial flows** across bank accounts, wallets, and credit cards, with itemized expenses and support for Open Finance synchronization (initially mocked).

This module is the **core ledger for personal/family money movements**, designed to support:
- accurate tracking of income, expenses, and transfers
- item-level granularity for unit price analytics
- future AI-assisted categorization, tagging, and document parsing

---

## Goals

- Centralize personal/family financial flows across accounts and cards.
- Enable accurate entry, classification, and aggregation of transactions.
- Support itemized purchases and unit-price calculation for individual inflation indexing (i3).
- Prepare the foundation for Open Finance sync and AI-assisted enrichment.

---

## Functional Requirements

> Any requirement marked with (*) must validate **quota limits** based on the user plan.

### 1) Accounts, Wallets, and Aggregation
- Users can track **income and expenses** for their bank accounts.
- Users can add **multiple bank accounts** to wallets, with an **aggregator/consolidator** (*).
- Users can record **transfers between accounts** (*).

### 2) Categorization & Tags
- Automatic transaction categorization (pending AI feasibility study).
- Users can attach tags to transactions (pending AI feasibility study).

### 3) Sync & Manual Entry
- A synchronization layer with **Open Finance** will exist (initially mocked).
- Users can manually enter transactions regardless of sync availability.

### 4) Credit Card Bills
- Users can manage **credit card statements/bills** (*).

### 5) Transaction Data Model
Each transaction includes:
- Date/time
- Description
- Amount
- Type: **Income**, **Expense**, or **Transfer** (with target account)
- Notes
- Attachments
- Electronic invoice (e-NF)

### 6) Expense Entry Methods
- Users can add an expense via **e-NF QR code**.
- Users can add an expense via **receipt image/print** (*).

### 7) Itemized Transactions & Unit Pricing
- A single transaction can represent **multiple items**, each with its own value.
- If **quantity + total value** are present, the system must derive **unit price** (e.g., R$100 for 50L → R$2/L).
- This applies to any transaction with quantity and total value, including those captured from **e-NF** or **receipt image**.
- Purpose: enable an **individual inflation index (i3)** based on user purchase history.
- This item requires a **cost/benefit study for AI support** (document parsing and item extraction).

---

## Quota Validation Rules

All features marked with (*) must enforce **plan-based quotas**, e.g.:
- Max wallets per plan
- Max linked accounts
- Max transfers per period
- Max credit card statements
- Max receipt image uploads

(Exact thresholds to be defined by the billing/plan policy.)

---

## Non-Goals (for now)

- Automated financial advice or recommendations
- Full accounting or tax reporting
- Real-time Open Finance sync (initially mocked)

---

## Dependencies & Integrations

- **Open Finance sync layer** (mocked initially)
- **Document ingestion pipeline** (for receipt images and e-NF parsing)
- **Plan/quota enforcement service**
- **Storage** for attachments and structured items

---

## Open Questions

- AI approach and ROI for categorization, tags, and item extraction
- Minimum viable data model for itemized transactions
- Quota policy definitions for B2C plans
- Standardization of e-NF schema support

---

## Success Criteria

- Users can register accounts and record income/expenses reliably.
- Transfers and credit card bills are supported with quota enforcement.
- Itemized transactions and unit price calculations are consistent.
- Manual entry works end-to-end even without Open Finance sync.

