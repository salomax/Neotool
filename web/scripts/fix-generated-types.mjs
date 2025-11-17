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
  const baseMutationOptionsRegex = /^export type \w+MutationOptions = ApolloReactCommon\.BaseMutationOptions<[^>]+>;$/gm;
  if (baseMutationOptionsRegex.test(content)) {
    content = content.replace(baseMutationOptionsRegex, '');
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

