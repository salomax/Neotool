# Database Schema Organization

## Overview

All database objects (tables, indexes, sequences, etc.) **must** be organized into PostgreSQL schemas according to their module/service. This practice ensures proper namespace isolation, improves security, and enhances maintainability.

## ⚠️ Rule

**ANY TABLE MUST BE INCLUDED TO A SCHEMA. NEVER IN PUBLIC.**

This is a **MANDATORY** rule that applies to all database migrations and entity definitions:

- ✅ **REQUIRED**: All tables must be created in a named schema (e.g., `security`, `app`)
- ❌ **FORBIDDEN**: Creating tables in the `public` schema
- ✅ **REQUIRED**: All migrations must create the schema if it doesn't exist
- ✅ **REQUIRED**: All table references must use schema-qualified names (e.g., `security.users`)
- ✅ **REQUIRED**: All JPA entities must specify the schema in `@Table` annotations

**The `public` schema must NEVER be used for application tables.**

## Schema Naming Convention

Schemas should be named after the module or service that owns them:

- **Security module**: `security` schema
- **App module**: `app` schema
- **Future modules**: Use the module name (e.g., `billing`, `analytics`, `notifications`)

Schema names should be:
- Lowercase
- Singular noun (e.g., `security`, not `securities`)
- Match the module/service name when possible

## Migration Files

### Schema Creation

Every module's first migration should create its schema:

```sql
-- Create schema
CREATE SCHEMA IF NOT EXISTS security;

-- Set search path for the migration
SET search_path TO security, public;
```

### Table Creation

All tables must be created with explicit schema qualification:

```sql
CREATE TABLE IF NOT EXISTS security.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    -- ... other columns
);
```

### Index Creation

Indexes should be created on schema-qualified tables:

```sql
CREATE INDEX IF NOT EXISTS idx_users_email 
    ON security.users(email);
```

### Foreign Key References

When referencing tables in the same schema, use schema-qualified names:

```sql
CREATE TABLE IF NOT EXISTS security.refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    -- ... other columns
);
```

### Cross-Schema References

When referencing tables in other schemas, always use fully qualified names:

```sql
-- Example: If app.orders references security.users
CREATE TABLE IF NOT EXISTS app.orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES security.users(id) ON DELETE CASCADE,
    -- ... other columns
);
```

## JPA Entity Configuration

### Entity Annotations

All JPA entities must specify the schema in the `@Table` annotation:

```kotlin
@Entity
@Table(name = "users", schema = "security")
data class User(
    @Id val id: UUID = UUID.randomUUID(),
    // ... other fields
)
```

### Benefits

- **Namespace isolation**: Prevents naming conflicts between modules
- **Security**: Allows fine-grained permissions per schema
- **Organization**: Clear separation of concerns
- **Maintainability**: Easier to understand which module owns which tables

## Configuration

### Application Configuration

The default search path can be configured in `application.yml`:

```yaml
jpa:
  default:
    properties:
      hibernate:
        default_schema: public  # Default schema for JPA operations
        hbm2ddl:
          auto: none  # Use Flyway instead
```

**Note**: Even with `default_schema` set, always explicitly specify schema in `@Table` annotations for clarity and to avoid issues when using multiple schemas.

### Connection String

The connection string should include the default schema in the search path:

```yaml
datasources:
  default:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:procureflow_db}?currentSchema=public
```

However, since we use explicit schema qualification in migrations and entities, this is optional.

## Migration Best Practices

### 1. Schema Creation Pattern

Every module's initial migration should follow this pattern:

```sql
-- V0_1__init.sql
-- Create module schema
CREATE SCHEMA IF NOT EXISTS security;

-- Set search path for this migration
SET search_path TO security, public;

-- Create tables with schema qualification
CREATE TABLE IF NOT EXISTS security.users (
    -- table definition
);
```

### 2. Subsequent Migrations

All subsequent migrations should:

1. Set the search path at the beginning
2. Use schema-qualified table names
3. Update schema-qualified tables

```sql
-- V0_2__add_authentication.sql
-- Set search path to security schema
SET search_path TO security, public;

-- Alter tables with schema qualification
ALTER TABLE security.users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);
```

### 3. Cross-Schema Operations

When operations span multiple schemas, explicitly qualify all references:

```sql
-- Example: Create a view that joins data from multiple schemas
CREATE VIEW app.user_orders AS
SELECT 
    u.id as user_id,
    u.email,
    o.id as order_id,
    o.total
FROM security.users u
JOIN app.orders o ON o.user_id = u.id;
```

## Examples

### Security Module

**Migration:**
```sql
-- V0_1__init.sql
CREATE SCHEMA IF NOT EXISTS security;
SET search_path TO security, public;

CREATE TABLE IF NOT EXISTS security.users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Entity:**
```kotlin
@Entity
@Table(name = "users", schema = "security")
data class User(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true) val email: String
)
```

### App Module

**Migration:**
```sql
-- V1_1__create_products_customers.sql
CREATE SCHEMA IF NOT EXISTS app;
SET search_path TO app, public;

CREATE TABLE IF NOT EXISTS app.products (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    -- ... other columns
);
```

**Entity:**
```kotlin
@Entity
@Table(name = "products", schema = "app")
open class ProductEntity(
    @Id override val id: UUID?,
    @Column(nullable = false) open var name: String
)
```

## Enforcement

### Code Reviews

All database migrations and entity definitions must be reviewed to ensure:
1. ✅ Schema is explicitly created in the first migration
2. ✅ All tables use schema-qualified names (e.g., `security.users`, not `users`)
3. ✅ All JPA entities specify the schema in `@Table` annotation (e.g., `@Table(name = "users", schema = "security")`)
4. ❌ **NO tables are created in the `public` schema** - This is a hard requirement, no exceptions for application tables
5. ✅ All foreign key references use schema-qualified names
6. ✅ All indexes are created on schema-qualified tables

### Migration Validation

Before merging:
- Verify schema creation in module's first migration
- Check all table names are schema-qualified
- Ensure foreign key references use schema-qualified names
- Confirm entity annotations include schema specification

## Migration from Existing Code

### Existing Tables in Public Schema

If you have existing tables in the `public` schema, you should:

1. Create a migration to move them to the appropriate schema
2. Update entity annotations to specify the new schema
3. Test thoroughly to ensure all references are updated

**Example migration to move tables:**

```sql
-- V1_2__move_tables_to_app_schema.sql
-- Create app schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS app;

-- Move tables to app schema
ALTER TABLE products SET SCHEMA app;
ALTER TABLE customers SET SCHEMA app;

-- Update indexes (they move automatically with tables)
-- Update sequences (they move automatically with tables)
```

## Benefits

1. **Security**: Granular permissions per schema
2. **Organization**: Clear module boundaries
3. **Maintainability**: Easier to understand codebase structure
4. **Scalability**: Easier to split databases if needed
5. **Performance**: Better query planning with explicit schemas
6. **Best Practice**: Aligns with PostgreSQL and enterprise database practices

## Troubleshooting

### Common Issues

**Issue**: Tables not found after migration
- **Solution**: Ensure schema is created before tables and entity annotations specify the schema

**Issue**: Foreign key constraint violations
- **Solution**: Verify all foreign key references use schema-qualified table names

**Issue**: JPA cannot find tables
- **Solution**: Ensure `@Table` annotation includes `schema = "schema_name"` parameter

## References

- [PostgreSQL Schema Documentation](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [Hibernate Schema Configuration](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#configurations)
- [Flyway Migration Best Practices](https://flywaydb.org/documentation/usage/best-practices/)

