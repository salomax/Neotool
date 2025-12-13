#!/usr/bin/env node
/**
 * GraphQL codegen post-processing script.
 * 
 * This script fixes Apollo Client 4 compatibility issues in the generated GraphQL code:
 * 
 * 1. Apollo Client 4 Compatibility Fixes:
 *    - BaseMutationOptions Removal:
 *      The @graphql-codegen/typescript-react-apollo plugin generates
 *      BaseMutationOptions types that don't exist in Apollo Client 4.
 *      This function removes those type exports to prevent TypeScript errors.
 * 
 *    - useSuspenseQuery Variable Type Fix:
 *      When queries have required variables, the generated SuspenseQueryHookOptions
 *      type allows variables to be optional, but useSuspenseQuery requires them
 *      when the query schema defines required variables. This function adds
 *      type assertions to fix the type mismatch.
 * 
 * These fixes are necessary due to compatibility issues between the codegen
 * plugin (v4.3.3) and Apollo Client 4. Future versions of the plugin may
 * resolve these issues, at which point these workarounds can be removed.
 * 
 * See: https://the-guild.dev/graphql/codegen/plugins/typescript/typescript-react-apollo
 */

import { existsSync, readFileSync, writeFileSync, readdirSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const webDir = join(__dirname, '..');
const operationsRoot = join(webDir, 'src/lib/graphql/operations');

function collectOperationFiles(dir) {
  const entries = readdirSync(dir, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectOperationFiles(fullPath));
    } else if (entry.isFile() && entry.name.endsWith('.generated.ts')) {
      files.push(fullPath);
    }
  }
  return files;
}

function fixApolloImports() {
  if (!existsSync(operationsRoot)) {
    return;
  }
  const files = collectOperationFiles(operationsRoot);
  files.forEach(file => {
    let content = readFileSync(file, 'utf8');
    let updated = false;
    if (content.includes("import * as ApolloReactHooks from '@apollo/client';")) {
      content = content.replace(
        "import * as ApolloReactHooks from '@apollo/client';",
        "import * as ApolloReactHooks from '@apollo/client/react';",
      );
      updated = true;
    }
    if (content.includes("import * as ApolloReactCommon from '@apollo/client';")) {
      content = content.replace(
        "import * as ApolloReactCommon from '@apollo/client';",
        "import * as ApolloReactCommon from '@apollo/client/react';",
      );
      updated = true;
    }
    if (updated) {
      writeFileSync(file, content, 'utf8');
      console.log(`🔧 Fixed Apollo imports in ${file.replace(webDir, '')}`);
    }
  });
}

/**
 * Removes BaseMutationOptions type exports that are incompatible with Apollo Client 4.
 * 
 * Issue: The @graphql-codegen/typescript-react-apollo plugin generates type exports like:
 *   export type XxxMutationOptions = ApolloReactCommon.BaseMutationOptions<...>;
 * 
 * However, BaseMutationOptions was removed in Apollo Client 4, causing TypeScript errors:
 *   TS2694: Namespace has no exported member 'BaseMutationOptions'
 * 
 * Solution: Remove these type exports since they're not needed - the mutation hooks
 * already provide the necessary types (MutationHookOptions, MutationResult, etc.).
 * 
 * Alternative: Could use skipExportingMutationOptions: true in codegen.ts config,
 * but keeping this post-processing allows more control and visibility.
 */
function removeBaseMutationOptions() {
  if (!existsSync(operationsRoot)) {
    return;
  }
  const files = collectOperationFiles(operationsRoot);
  files.forEach(file => {
    let content = readFileSync(file, 'utf8');
    // Match: export type XxxMutationOptions = ApolloReactCommon.BaseMutationOptions<...>;
    const baseMutationOptionsRegex = /export type \w+MutationOptions\s*=\s*ApolloReactCommon\.BaseMutationOptions<[^>]+>;\n?/gm;
    const beforeCount = (content.match(/BaseMutationOptions/g) || []).length;
    if (beforeCount > 0) {
      content = content.replace(baseMutationOptionsRegex, '');
      const afterCount = (content.match(/BaseMutationOptions/g) || []).length;
      if (beforeCount > afterCount) {
        writeFileSync(file, content, 'utf8');
        console.log(`🔧 Removed ${beforeCount - afterCount} BaseMutationOptions from ${file.replace(webDir, '')}`);
      }
    }
  });
}

/**
 * Fixes useSuspenseQuery type errors when queries have required variables.
 * 
 * Issue: When a GraphQL query has required variables (e.g., `query GetUser($id: ID!)`),
 * the generated code creates a function signature like:
 *   useSuspenseQuery(baseOptions?: SuspenseQueryHookOptions<Query, Variables>)
 * 
 * However, SuspenseQueryHookOptions allows variables to be optional, while
 * useSuspenseQuery's Options type requires variables when the query schema
 * defines them as required. This causes TypeScript errors:
 *   TS2769: Type '...' is not assignable to type 'Options<Exact<{ id: string; }>>'
 *   Type 'undefined' is not assignable to type 'Exact<{ id: string; }>'
 * 
 * Solution: Add a type assertion that narrows the options type to match
 * what useSuspenseQuery expects. We use a specific type assertion rather than
 * 'as any' to maintain type safety while fixing the mismatch.
 * 
 * Note: This is a workaround for a codegen plugin limitation. Future versions
 * may generate correct types that don't require this fix.
 */
function fixSuspenseQueryVariables() {
  if (!existsSync(operationsRoot)) {
    return;
  }
  const files = collectOperationFiles(operationsRoot);
  files.forEach(file => {
    let content = readFileSync(file, 'utf8');
    let updated = false;
    
    // Pattern: return ApolloReactHooks.useSuspenseQuery<Query, Variables>(Document, options);
    // Also match existing "as any" assertions that need to be replaced
    const suspenseQueryCallRegex = /return ApolloReactHooks\.useSuspenseQuery<(\w+), (\w+)>\((\w+Document), options(?:\s+as\s+any)?\);/g;
    
    const matches = [...content.matchAll(suspenseQueryCallRegex)];
    if (matches.length > 0) {
      matches.forEach(match => {
        const queryType = match[1];
        const variablesType = match[2];
        const documentName = match[3];
        
        // Check if variables type has required fields by looking at the type definition
        const variablesTypeRegex = new RegExp(`export type ${variablesType}\\s*=\\s*Types\\.Exact<\\{([^}]+)\\}>;`, 's');
        const variablesMatch = content.match(variablesTypeRegex);
        
        if (variablesMatch) {
          const variablesContent = variablesMatch[1];
          // Check if there are required fields (fields without InputMaybe)
          // Required fields look like: fieldName: Types.Scalars['Type']['input']
          // Optional fields look like: fieldName?: Types.InputMaybe<...>
          const hasRequiredFields = /(\w+):\s*Types\.Scalars\[/.test(variablesContent);
          
          if (hasRequiredFields) {
            // Use a specific type assertion to fix the type mismatch
            // This preserves type safety while allowing the code to compile
            const originalCall = match[0];
            const fixedCall = `return ApolloReactHooks.useSuspenseQuery<${queryType}, ${variablesType}>(${documentName}, options as ApolloReactHooks.SkipToken | ApolloReactHooks.useSuspenseQuery.Options<${variablesType}>);`;
            content = content.replace(originalCall, fixedCall);
            updated = true;
          }
        }
      });
    }
    
    if (updated) {
      writeFileSync(file, content, 'utf8');
      console.log(`🔧 Fixed useSuspenseQuery variables in ${file.replace(webDir, '')}`);
    }
  });
}

// Execute all post-processing fixes
fixApolloImports();
removeBaseMutationOptions(); // Remove BaseMutationOptions types (incompatible with Apollo Client 4)
fixSuspenseQueryVariables(); // Fix useSuspenseQuery type errors for queries with required variables
