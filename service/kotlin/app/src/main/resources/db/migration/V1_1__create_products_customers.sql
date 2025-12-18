-- V1__create_products_customers.sql
-- Creates core tables for Product and Customer.
CREATE TABLE IF NOT EXISTS products (
    id              uuid DEFAULT uuidv7() PRIMARY KEY,
    name            TEXT NOT NULL,
    sku             TEXT UNIQUE NOT NULL,
    price_cents     BIGINT NOT NULL DEFAULT 0,
    stock           INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS customers (
    id              uuid DEFAULT uuidv7() PRIMARY KEY,
    name            TEXT NOT NULL,
    email           TEXT UNIQUE NOT NULL,
    status          TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);
