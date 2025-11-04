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
      id={`graphql-tabpanel-${index}`}
      aria-labelledby={`graphql-tab-${index}`}
      {...other}
    >
      {value === index && <Box sx={{ p: 3 }}>{children}</Box>}
    </div>
  );
}

export default function GraphQLPage() {
  const [tabValue, setTabValue] = useState(0);

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        GraphQL Schema & Federation
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        GraphQL schema design, federation patterns, and subgraph architecture examples.
      </Typography>

      <Paper sx={{ width: '100%' }}>
        <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
          <Tabs value={tabValue} onChange={handleTabChange} aria-label="GraphQL schema tabs">
            <Tab label="Schema Design" />
            <Tab label="Types & Resolvers" />
            <Tab label="Federation" />
            <Tab label="Subgraphs" />
            <Tab label="Best Practices" />
          </Tabs>
        </Box>

        <TabPanel value={tabValue} index={0}>
          <SchemaDesignExample />
        </TabPanel>

        <TabPanel value={tabValue} index={1}>
          <TypesResolversExample />
        </TabPanel>

        <TabPanel value={tabValue} index={2}>
          <FederationExample />
        </TabPanel>

        <TabPanel value={tabValue} index={3}>
          <SubgraphsExample />
        </TabPanel>

        <TabPanel value={tabValue} index={4}>
          <BestPracticesExample />
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
        GraphQL Schema Design
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Core Types:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`type Customer {
  id: ID!
  name: String!
  email: String!
  status: CustomerStatus!
  createdAt: DateTime!
  updatedAt: DateTime!
}

type Product {
  id: ID!
  name: String!
  sku: String!
  priceCents: Int!
  stock: Int!
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum CustomerStatus {
  ACTIVE
  INACTIVE
  PENDING
}

scalar DateTime`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Input Types:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`input CustomerInput {
  name: String!
  email: String!
  status: CustomerStatus!
}

input ProductInput {
  name: String!
  sku: String!
  priceCents: Int!
  stock: Int!
}

input CustomerFilters {
  search: String
  status: CustomerStatus
  limit: Int
  offset: Int
}`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Types & Resolvers Example
function TypesResolversExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Types & Resolvers
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Query Type:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`type Query {
  # Customer queries
  customers(filters: CustomerFilters): [Customer!]!
  customer(id: ID!): Customer
  
  # Product queries
  products(filters: ProductFilters): [Product!]!
  product(id: ID!): Product
  
  # Dashboard queries
  dashboardSummary: DashboardSummary!
  dashboardTimeseries(days: Int!): [DashboardTimeseries!]!
}

type DashboardSummary {
  totalCustomers: Int!
  totalProducts: Int!
  activeCustomers: Int!
  lowStockProducts: Int!
  totalRevenue: Int!
}`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Mutation Type:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`type Mutation {
  # Customer mutations
  createCustomer(input: CustomerInput!): Customer!
  updateCustomer(id: ID!, input: CustomerInput!): Customer!
  deleteCustomer(id: ID!): DeleteResponse!
  
  # Product mutations
  createProduct(input: ProductInput!): Product!
  updateProduct(id: ID!, input: ProductInput!): Product!
  deleteProduct(id: ID!): DeleteResponse!
}

type DeleteResponse {
  success: Boolean!
  message: String!
}`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Federation Example
function FederationExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        GraphQL Federation
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Supergraph Schema:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`extend type Query {
  customers: [Customer!]! @delegate(schema: "customer-service")
  products: [Product!]! @delegate(schema: "product-service")
  dashboardSummary: DashboardSummary! @delegate(schema: "analytics-service")
}

extend type Customer @key(fields: "id") {
  id: ID! @external
  orders: [Order!]! @delegate(schema: "order-service")
}

extend type Product @key(fields: "id") {
  id: ID! @external
  reviews: [Review!]! @delegate(schema: "review-service")
}`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Federation Configuration:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# supergraph.yaml
federation_version: 2
subgraphs:
  customer-service:
    routing_url: http://customer-service:8080/graphql
    schema:
      subgraph_url: http://customer-service:8080/graphql
  
  product-service:
    routing_url: http://product-service:8080/graphql
    schema:
      subgraph_url: http://product-service:8080/graphql
  
  analytics-service:
    routing_url: http://analytics-service:8080/graphql
    schema:
      subgraph_url: http://analytics-service:8080/graphql`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Subgraphs Example
function SubgraphsExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        Subgraph Architecture
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Customer Service Subgraph:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# Customer Service Schema
extend type Query {
  customers: [Customer!]!
  customer(id: ID!): Customer
}

type Customer @key(fields: "id") {
  id: ID!
  name: String!
  email: String!
  status: CustomerStatus!
  createdAt: DateTime!
  updatedAt: DateTime!
}

enum CustomerStatus {
  ACTIVE
  INACTIVE
  PENDING
}

scalar DateTime`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Product Service Subgraph:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# Product Service Schema
extend type Query {
  products: [Product!]!
  product(id: ID!): Product
}

type Product @key(fields: "id") {
  id: ID!
  name: String!
  sku: String!
  priceCents: Int!
  stock: Int!
  createdAt: DateTime!
  updatedAt: DateTime!
}

scalar DateTime`}
        </CodeBlock>
      </Box>
    </Box>
  );
}

// Best Practices Example
function BestPracticesExample() {
  return (
    <Box>
      <Typography variant="h6" gutterBottom>
        GraphQL Best Practices
      </Typography>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Schema Design:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# ✅ Good: Use specific types
type Customer {
  id: ID!
  name: String!
  email: String!
  status: CustomerStatus!
}

# ❌ Bad: Generic types
type Customer {
  id: String
  name: String
  email: String
  status: String
}

# ✅ Good: Use enums for limited values
enum CustomerStatus {
  ACTIVE
  INACTIVE
  PENDING
}

# ✅ Good: Use input types for mutations
input CustomerInput {
  name: String!
  email: String!
  status: CustomerStatus!
}`}
        </CodeBlock>
      </Box>

      <Box sx={{ mb: 3 }}>
        <Typography variant="subtitle1" gutterBottom>
          Performance:
        </Typography>
        <CodeBlock component="pre" sx={{ 
          backgroundColor: 'grey.100', 
          p: 2, 
          borderRadius: 1,
          overflow: 'auto',
          fontSize: '0.875rem'
        }}>
{`# ✅ Good: Use DataLoader for N+1 prevention
const customerLoader = new DataLoader(async (ids) => {
  const customers = await customerRepository.findByIds(ids);
  return ids.map(id => customers.find(c => c.id === id));
});

# ✅ Good: Implement pagination
type CustomerConnection {
  edges: [CustomerEdge!]!
  pageInfo: PageInfo!
}

type CustomerEdge {
  node: Customer!
  cursor: String!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}`}
        </CodeBlock>
      </Box>
    </Box>
  );
}
