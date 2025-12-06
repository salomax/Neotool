#!/usr/bin/env node
/**
 * Postinstall script that runs GraphQL code generation
 * Only runs if prerequisites are met (schema file exists, dependencies installed)
 */

import { existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, resolve } from 'path';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const webDir = resolve(__dirname, '..');
// Use GRAPHQL_SCHEMA_PATH if set, otherwise fall back to default dev schema
const schemaPathEnv = process.env.GRAPHQL_SCHEMA_PATH;
const schemaPath = schemaPathEnv
  ? resolve(webDir, schemaPathEnv)
  : resolve(webDir, '../contracts/graphql/supergraph/supergraph.local.graphql');

// Check if schema file exists
if (!existsSync(schemaPath)) {
  console.warn('⚠️  GraphQL schema file not found:', schemaPath);
  console.warn('⚠️  Skipping code generation. Run "pnpm codegen" manually after ensuring the schema exists.');
  process.exit(0);
}

// Check if graphql-codegen is available
try {
  // Try to resolve the graphql-codegen binary
  const codegenPath = resolve(webDir, 'node_modules/.bin/graphql-codegen');
  if (!existsSync(codegenPath)) {
    console.warn('⚠️  graphql-codegen not found in node_modules');
    console.warn('⚠️  Skipping code generation. Run "pnpm codegen" manually after installation completes.');
    process.exit(0);
  }
} catch (error) {
  console.warn('⚠️  Could not verify graphql-codegen installation');
  console.warn('⚠️  Skipping code generation. Run "pnpm codegen" manually after installation completes.');
  process.exit(0);
}

// Run codegen
try {
  console.log('🔄 Running GraphQL code generation...');
  // Use pnpm exec to ensure we use the locally installed version
  execSync('pnpm exec graphql-codegen --config codegen.ts', {
    cwd: webDir,
    stdio: 'inherit',
    env: { ...process.env, PATH: process.env.PATH },
  });
  console.log('✅ GraphQL code generation completed successfully');
  
  console.log('🔧 Syncing fragment documents...');
  execSync('node scripts/fix-generated-types.mjs', {
    cwd: webDir,
    stdio: 'inherit',
    env: { ...process.env, PATH: process.env.PATH },
  });
} catch (error) {
  console.error('❌ GraphQL code generation failed');
  console.error('💡 You can run "pnpm codegen" manually to retry');
  // Don't fail the install process - codegen can be run manually
  process.exit(0);
}
