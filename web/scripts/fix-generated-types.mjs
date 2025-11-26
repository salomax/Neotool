#!/usr/bin/env node
/**
 * Post-processing script to fix generated GraphQL types for Apollo Client v4
 * Removes BaseMutationOptions type exports that don't exist in v4
 */

import { readFileSync, writeFileSync, readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const rootDir = join(__dirname, '..');
const operationsDir = join(rootDir, 'src/lib/graphql/operations');

function findGeneratedFiles(dir) {
  const files = [];
  const entries = readdirSync(dir, { withFileTypes: true });
  
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...findGeneratedFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith('.generated.ts')) {
      files.push(fullPath);
    }
  }
  
  return files;
}

function fixGeneratedFile(filePath) {
  let content = readFileSync(filePath, 'utf8');
  let modified = false;
  
  // Remove BaseMutationOptions type exports (doesn't exist in Apollo Client v4)
  // Match both single-line and multi-line patterns, with flexible whitespace
  const baseMutationOptionsRegex = /export type \w+MutationOptions\s*=\s*ApolloReactCommon\.BaseMutationOptions<[^>]+>;\s*\n?/gm;
  if (baseMutationOptionsRegex.test(content)) {
    content = content.replace(baseMutationOptionsRegex, '');
    modified = true;
  }
  
  // Also handle cases where MutationOptions might be incorrectly imported from ApolloReactCommon
  // Replace ApolloReactCommon.BaseMutationOptions with MutationOptions from @apollo/client
  const baseMutationOptionsUsageRegex = /ApolloReactCommon\.BaseMutationOptions/g;
  if (baseMutationOptionsUsageRegex.test(content)) {
    // Check if MutationOptions is already imported from @apollo/client
    if (!content.includes("import { gql, MutationOptions } from '@apollo/client'")) {
      // Add MutationOptions to existing import if gql is already imported
      content = content.replace(
        /import { gql(?:, [^}]+)? } from '@apollo\/client';/,
        (match) => {
          if (match.includes('MutationOptions')) {
            return match;
          }
          return match.replace('{ gql', '{ gql, MutationOptions');
        }
      );
    }
    // Replace BaseMutationOptions usage with MutationOptions
    content = content.replace(baseMutationOptionsUsageRegex, 'MutationOptions');
    modified = true;
  }
  
  // Fix useSuspenseQuery type signature - variables must be required when not using skipToken
  // Change from optional to required variables in the function signature
  const suspenseQueryRegex = /export function (\w+SuspenseQuery)\(baseOptions\?: ApolloReactHooks\.SkipToken \| ApolloReactHooks\.SuspenseQueryHookOptions<([^,]+), ([^>]+)>\) \{/g;
  if (suspenseQueryRegex.test(content)) {
    content = content.replace(suspenseQueryRegex, (match, funcName, queryType, varsType) => {
      return `export function ${funcName}(baseOptions: ApolloReactHooks.SkipToken | (ApolloReactHooks.SuspenseQueryHookOptions<${queryType}, ${varsType}> & { variables: ${varsType} })) {`;
    });
    modified = true;
  }
  
  if (modified) {
    writeFileSync(filePath, content, 'utf8');
    console.log(`Fixed: ${filePath.replace(rootDir, '')}`);
  }
}

// Find and fix all generated files
const generatedFiles = findGeneratedFiles(operationsDir);
generatedFiles.forEach(fixGeneratedFile);

console.log(`Processed ${generatedFiles.length} generated files`);

