#!/usr/bin/env node
/**
 * GraphQL codegen post-processing.
 * For now we only need to fix the fragment documents because the stock
 * typescript-operations plugin emits malformed gql strings when a fragment
 * spreads another fragment.
 */

import { existsSync, readFileSync, writeFileSync, readdirSync } from 'fs';
import { dirname, join } from 'path';
import { fileURLToPath } from 'url';
import { Kind, parse, print, visit } from 'graphql';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const webDir = join(__dirname, '..');
const fragmentsSource = join(webDir, 'src/lib/graphql/fragments/common.graphql');
const fragmentsTarget = join(webDir, 'src/lib/graphql/fragments/common.generated.ts');
const operationsRoot = join(webDir, 'src/lib/graphql/operations');

function indentBlock(text, spaces = 4) {
  const pad = ' '.repeat(spaces);
  return text
    .split('\n')
    .map(line => (line ? pad + line : pad))
    .join('\n');
}

function collectFragments(source) {
  const document = parse(source);
  return document.definitions
    .filter(def => def.kind === Kind.FRAGMENT_DEFINITION)
    .map(fragment => {
      const dependencies = new Set();
      visit(fragment, {
        FragmentSpread(node) {
          if (node.name.value !== fragment.name.value) {
            dependencies.add(node.name.value);
          }
        },
      });
      return {
        name: fragment.name.value,
        printed: print(fragment),
        dependencies: Array.from(dependencies),
      };
    });
}

function buildFragmentDoc({ name, printed, dependencies }) {
  const docLines = [
    `export const ${name}FragmentDoc = gql\``,
    indentBlock(printed),
    dependencies.length
      ? dependencies.map(dep => `    \${${dep}FragmentDoc}`).join('\n')
      : '',
    '`;',
  ].filter(Boolean);

  // Add an extra newline between fragments to match codegen's output style
  return docLines.join('\n') + '\n';
}

function rewriteFragmentDocs() {
  if (!existsSync(fragmentsSource) || !existsSync(fragmentsTarget)) {
    return;
  }

  const fragments = collectFragments(readFileSync(fragmentsSource, 'utf8'));
  if (!fragments.length) {
    return;
  }

  const generatedContent = readFileSync(fragmentsTarget, 'utf8');
  const docMarkerIndex = fragments
    .map(fragment => generatedContent.indexOf(`export const ${fragment.name}FragmentDoc`))
    .filter(index => index !== -1)
    .reduce((min, index) => Math.min(min, index), Number.POSITIVE_INFINITY);

  if (!Number.isFinite(docMarkerIndex)) {
    console.warn('⚠️ Could not find fragment markers in', fragmentsTarget);
    return;
  }

  const typeSnippets = fragments
    .map(fragment => {
      const regex = new RegExp(
        `export type ${fragment.name}Fragment\\s*=\\s*[^;]+;`,
        'm',
      );
      const match = generatedContent.match(regex);
      return match ? match[0] : null;
    })
    .filter(Boolean);

  if (!typeSnippets.length) {
    console.warn('⚠️ Could not extract fragment type definitions from', fragmentsTarget);
    return;
  }

  const typeSection = typeSnippets.join('\n\n');
  const fragmentDocs = fragments.map(buildFragmentDoc).join('\n');
  const header = "import { gql } from '@apollo/client';\n\n";
  const nextContent = `${header}${typeSection}\n\n${fragmentDocs}`;

  writeFileSync(fragmentsTarget, nextContent, 'utf8');
  console.log('🔧 Updated fragment documents in common.generated.ts');
}

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

rewriteFragmentDocs();
fixApolloImports();
