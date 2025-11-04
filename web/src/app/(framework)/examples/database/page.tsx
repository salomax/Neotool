"use client";

import React, { useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Alert from "@mui/material/Alert";
import CodeBlock from "@mui/material/Box";

interface TabPanelProps {
  children?: React.ReactNode;
  index: number;
  value: number;
}

function TabPanel(props: TabPanelProps) {
  const { children, value, index, ...other } = props;

  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`database-tabpanel-${index}`}
      aria-labelledby={`database-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

export default function DatabasePage() {
  const [tabValue, setTabValue] = useState(0);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Database Operations
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        PostgreSQL database schema, migrations, queries, and performance optimization examples.
      </Typography>

      <Paper sx={{ width: '100%' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tabValue} onChange={handleTabChange} aria-label="database operations tabs">
            <Tab label="Schema Design" />
            <Tab label="Migrations" />
            <Tab label="Queries" />
            <Tab label="Indexes" />
            <Tab label="Performance" />
          </Tabs>
        </Box>

        <TabPanel value={tabValue} index={0}>
          <SchemaDesignExample />
        </TabPanel>

        <TabPanel value={tabValue} index={1}>
          <MigrationsExample />
        </TabPanel>

        <TabPanel value={tabValue} index={2}>
          <QueriesExample />
        </TabPanel>

        <TabPanel value={tabValue} index={3}>
          <IndexesExample />
        </TabPanel>

        <TabPanel value={tabValue} index={4}>
          <PerformanceExample />
        </TabPanel>
      </Paper>
    </Container>
  );
}

// Schema Design Example
function SchemaDesignExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Database Schema Design
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Customers Table:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`CREATE TABLE customers (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_status ON customers(status);`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Products Table:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`CREATE TABLE products (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  sku VARCHAR(100) UNIQUE NOT NULL,
  price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
  stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_stock ON products(stock);`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Migrations Example
function MigrationsExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Database Migrations
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Initial Migration:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Migration: 001_initial_schema.sql
-- Description: Create initial tables for customers and products

CREATE TABLE customers (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE products (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  sku VARCHAR(100) UNIQUE NOT NULL,
  price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
  stock INTEGER NOT NULL DEFAULT 0 CHECK (stock >= 0),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Add Indexes Migration:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Migration: 002_add_indexes.sql
-- Description: Add performance indexes

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_status ON customers(status);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_stock ON products(stock);
CREATE INDEX idx_products_price ON products(price_cents);`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Queries Example
function QueriesExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        SQL Queries Examples
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Basic CRUD Operations:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Create
INSERT INTO customers (name, email, status) 
VALUES ('John Doe', 'john@example.com', 'ACTIVE');

-- Read
SELECT * FROM customers WHERE status = 'ACTIVE';
SELECT * FROM products WHERE stock > 0 ORDER BY name;

-- Update
UPDATE customers 
SET status = 'INACTIVE', updated_at = NOW() 
WHERE id = 1;

-- Delete
DELETE FROM products WHERE stock = 0;`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Complex Queries:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Search with pagination
SELECT * FROM customers 
WHERE name ILIKE '%john%' OR email ILIKE '%john%'
ORDER BY created_at DESC
LIMIT 10 OFFSET 0;

-- Aggregation queries
SELECT 
  status,
  COUNT(*) as count,
  AVG(EXTRACT(EPOCH FROM (NOW() - created_at))/86400) as avg_days_old
FROM customers 
GROUP BY status;

-- Low stock products
SELECT name, sku, stock 
FROM products 
WHERE stock <= 10 
ORDER BY stock ASC;`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Indexes Example
function IndexesExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Database Indexes
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Performance Indexes:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Single column indexes
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_status ON customers(status);
CREATE INDEX idx_products_sku ON products(sku);
CREATE INDEX idx_products_stock ON products(stock);

-- Composite indexes
CREATE INDEX idx_customers_status_created ON customers(status, created_at);
CREATE INDEX idx_products_stock_price ON products(stock, price_cents);

-- Partial indexes
CREATE INDEX idx_active_customers ON customers(email) 
WHERE status = 'ACTIVE';

-- Text search indexes
CREATE INDEX idx_customers_name_text ON customers 
USING gin(to_tsvector('english', name));`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Index Analysis:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Check index usage
SELECT 
  schemaname,
  tablename,
  indexname,
  idx_tup_read,
  idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_tup_read DESC;

-- Analyze query performance
EXPLAIN ANALYZE 
SELECT * FROM customers 
WHERE status = 'ACTIVE' 
ORDER BY created_at DESC 
LIMIT 10;`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Performance Example
function PerformanceExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Performance Optimization
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Connection Pooling:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# Database configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/neotool
    username: \${DB_USERNAME:neotool}
    password: \${DB_PASSWORD:password}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Query Optimization:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`-- Use prepared statements
PREPARE get_customer AS 
SELECT * FROM customers WHERE id = $1;

-- Batch operations
INSERT INTO products (name, sku, price_cents, stock) 
VALUES 
  ('Product 1', 'P001', 1999, 100),
  ('Product 2', 'P002', 2999, 50),
  ('Product 3', 'P003', 3999, 25);

-- Use LIMIT for large result sets
SELECT * FROM customers 
ORDER BY created_at DESC 
LIMIT 100;`}
        </CodeBlock>
      </Box>
    </Box>
  );
}
