#!/usr/bin/env node

/**
 * Clean Examples Script
 * 
 * This script removes all customer and product example code from the codebase,
 * keeping only the boilerplate infrastructure.
 */

import { readFileSync, writeFileSync, readdirSync, statSync, rmSync, existsSync } from 'fs';
import { join, dirname, relative } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = join(__dirname, '..');

// Color codes for terminal output
const colors = {
  reset: '\x1b[0m',
  bright: '\x1b[1m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  red: '\x1b[31m',
  blue: '\x1b[34m',
  cyan: '\x1b[36m',
};

function log(message, color = 'reset') {
  console.log(`${colors[color]}${message}${colors.reset}`);
}

function logError(message) {
  console.error(`${colors.red}${message}${colors.reset}`);
}

// Files and directories to delete
const filesToDelete = [
  // Backend entities
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/CustomerEntity.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/entity/ProductEntity.kt',
  
  // Backend domain models
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Customer.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/domain/Product.kt',
  
  // Backend DTOs
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/CustomerDto.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/dto/ProductDto.kt',
  
  // Backend resolvers
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/CustomerResolver.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/resolvers/ProductResolver.kt',
  
  // Database migration
  'service/kotlin/app/src/main/resources/db/migration/V1_1__create_products_customers.sql',
];

const directoriesToDelete = [
  // Frontend customer/product pages
  'web/src/app/(neotool)/examples/customers',
  'web/src/app/(neotool)/examples/products',
  
  // Frontend hooks
  'web/src/lib/hooks/customer',
  
  // Frontend GraphQL operations
  'web/src/lib/graphql/operations/customer',
  'web/src/lib/graphql/operations/product',
];

// Files to modify (clean content)
const filesToModify = [
  'contracts/graphql/subgraphs/app/schema.graphqls',
  'service/kotlin/app/src/main/resources/graphql/schema.graphqls',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/AppWiringFactory.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/graphql/dto/Inputs.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/service/Services.kt',
  'service/kotlin/app/src/main/kotlin/io/github/salomax/neotool/example/repo/Repositories.kt',
  'web/src/app/(neotool)/examples/page.tsx',
];

// Delete a file or directory
function deletePath(relativePath) {
  const fullPath = join(projectRoot, relativePath);
  
  if (!existsSync(fullPath)) {
    return { deleted: false, reason: 'not found' };
  }
  
  try {
    const stat = statSync(fullPath);
    if (stat.isDirectory()) {
      rmSync(fullPath, { recursive: true, force: true });
    } else {
      rmSync(fullPath, { force: true });
    }
    return { deleted: true };
  } catch (error) {
    return { deleted: false, reason: error.message };
  }
}

// Clean GraphQL schema file
function cleanGraphQLSchema(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove Product type
    content = content.replace(/type Product @key\(fields: "id"\) \{[^}]+\}\n\n?/gs, '');
    
    // Remove Customer type
    content = content.replace(/type Customer @key\(fields: "id"\) \{[^}]+\}\n\n?/gs, '');
    
    // Remove ProductInput
    content = content.replace(/input ProductInput \{[^}]+\}\n\n?/gs, '');
    
    // Remove CustomerInput
    content = content.replace(/input CustomerInput \{[^}]+\}\n\n?/gs, '');
    
    // Remove CustomerStatus enum
    content = content.replace(/enum CustomerStatus \{[^}]+\}\n\n?/gs, '');
    
    // Clean Query type - remove customer/product queries
    content = content.replace(/  # Entity queries\n  products: \[Product!\]!\n  product\(id: ID!\): Product\n  customers: \[Customer!\]!\n  customer\(id: ID!\): Customer\n/g, '');
    // If Query is empty, make it empty placeholder
    if (content.includes('type Query {') && !content.match(/type Query \{[^}]*\w/)) {
      content = content.replace(/type Query \{[^}]*\}/s, 'type Query {\n}');
    }
    
    // Clean Mutation type - remove customer/product mutations
    content = content.replace(/  # Product mutations\n  createProduct\(input: ProductInput!\): Product!\n  updateProduct\(id: ID!, input: ProductInput!\): Product!\n  deleteProduct\(id: ID!\): Boolean!\n  \n  # Customer mutations\n  createCustomer\(input: CustomerInput!\): Customer!\n  updateCustomer\(id: ID!, input: CustomerInput!\): Customer!\n  deleteCustomer\(id: ID!\): Boolean!\n/g, '');
    // If Mutation is empty, make it empty placeholder
    if (content.includes('type Mutation {') && !content.match(/type Mutation \{[^}]*\w/)) {
      content = content.replace(/type Mutation \{[^}]*\}/s, 'type Mutation {\n}');
    }
    
    // Remove BaseEntityInput if it's only used by products/customers
    // (Keep it for now as it might be a pattern)
    
    // Clean up extra blank lines
    content = content.replace(/\n{3,}/g, '\n\n');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Clean AppWiringFactory.kt
function cleanAppWiringFactory(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove customer/product resolver imports
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.graphql\.resolvers\.CustomerResolver\n/g, '');
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.graphql\.resolvers\.ProductResolver\n/g, '');
    
    // Remove customer/product resolver constructor parameters
    content = content.replace(/,\s*private val customerResolver: CustomerResolver/g, '');
    content = content.replace(/,\s*private val productResolver: ProductResolver/g, '');
    content = content.replace(/private val customerResolver: CustomerResolver,\s*/g, '');
    content = content.replace(/private val productResolver: ProductResolver,\s*/g, '');
    
    // Remove resolver registry registrations
    content = content.replace(/\s*resolverRegistry\.register\("customer", customerResolver\)\n/g, '');
    content = content.replace(/\s*resolverRegistry\.register\("product", productResolver\)\n/g, '');
    
    // Remove customer/product query resolvers
    content = content.replace(/\s*\.dataFetcher\("products",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("product",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("customers",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("customer",[^)]+\)\n/g, '');
    
    // Remove customer/product mutation resolvers
    content = content.replace(/\s*\.dataFetcher\("createProduct",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("updateProduct",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("deleteProduct",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("createCustomer",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("updateCustomer",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("deleteCustomer",[^)]+\)\n/g, '');
    
    // Remove customer/product subscription resolvers
    content = content.replace(/\s*\.dataFetcher\("productUpdated",[^)]+\)\n/g, '');
    content = content.replace(/\s*\.dataFetcher\("customerUpdated",[^)]+\)\n/g, '');
    
    // Remove customer/product type resolver methods
    content = content.replace(/\s*override fun registerCustomerTypeResolvers[^}]+\}\n\n?/gs, '');
    content = content.replace(/\s*override fun registerProductTypeResolvers[^}]+\}\n\n?/gs, '');
    
    // Clean up extra blank lines
    content = content.replace(/\n{3,}/g, '\n\n');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Clean Inputs.kt
function cleanInputs(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove customer/product input classes
    content = content.replace(/data class CustomerInput[^}]+\}[^}]*\}\n\n?/gs, '');
    content = content.replace(/data class ProductInput[^}]+\}[^}]*\}\n\n?/gs, '');
    
    // Clean up extra blank lines
    content = content.replace(/\n{3,}/g, '\n\n');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Clean Services.kt
function cleanServices(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove customer/product imports
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.domain\.Customer\n/g, '');
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.domain\.Product\n/g, '');
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.repo\.CustomerRepository\n/g, '');
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.repo\.ProductRepository\n/g, '');
    
    // Remove ProductService class
    content = content.replace(/@Singleton\s+open class ProductService[^}]+\}[^}]*\}\n\n?/gs, '');
    
    // Remove CustomerService class
    content = content.replace(/@Singleton\s+open class CustomerService[^}]+\}[^}]*\}\n\n?/gs, '');
    
    // Clean up extra blank lines
    content = content.replace(/\n{3,}/g, '\n\n');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Clean Repositories.kt
function cleanRepositories(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove customer/product imports
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.entity\.CustomerEntity\n/g, '');
    content = content.replace(/import io\.github\.salomax\.neotool\.example\.entity\.ProductEntity\n/g, '');
    
    // Remove ProductRepository interface
    content = content.replace(/@Repository\s+interface ProductRepository[^}]+\}\n\n?/gs, '');
    
    // Remove CustomerRepository interface
    content = content.replace(/@Repository\s+interface CustomerRepository[^}]+\}\n\n?/gs, '');
    
    // Clean up extra blank lines
    content = content.replace(/\n{3,}/g, '\n\n');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Clean examples page
function cleanExamplesPage(filePath) {
  const fullPath = join(projectRoot, filePath);
  
  if (!existsSync(fullPath)) {
    return { modified: false, reason: 'not found' };
  }
  
  try {
    let content = readFileSync(fullPath, 'utf-8');
    const originalContent = content;
    
    // Remove customer example object
    content = content.replace(/\s*{\s*title: "Customer Management",[^}]+\}[^}]*\},\n?/gs, '');
    
    // Remove product example object
    content = content.replace(/\s*{\s*title: "Product Catalog",[^}]+\}[^}]*\},\n?/gs, '');
    
    // Remove unused imports if they're only used by customer/product
    // (Keep all icons for now as they might be used elsewhere)
    
    // Clean up trailing commas
    content = content.replace(/,(\s*\]\s*)/g, '$1');
    
    if (content !== originalContent) {
      writeFileSync(fullPath, content, 'utf-8');
      return { modified: true };
    }
    
    return { modified: false };
  } catch (error) {
    return { modified: false, reason: error.message };
  }
}

// Main execution
function main() {
  const args = process.argv.slice(2);
  const dryRun = args.includes('--dry-run');
  const regenerate = args.includes('--regenerate');
  
  log('\n🧹 Starting example cleanup process...\n', 'bright');
  
  if (dryRun) {
    log('🔍 DRY RUN MODE - No files will be modified\n', 'yellow');
  }
  
  let deletedCount = 0;
  let modifiedCount = 0;
  const errors = [];
  
  // Delete files
  log('🗑️  Deleting files...', 'blue');
  for (const file of filesToDelete) {
    const result = dryRun ? { deleted: false, reason: 'dry run' } : deletePath(file);
    if (result.deleted) {
      deletedCount++;
      log(`   ✓ Deleted: ${file}`, 'green');
    } else if (!dryRun && result.reason !== 'not found') {
      errors.push(`Failed to delete ${file}: ${result.reason}`);
      logError(`   ✗ Failed to delete: ${file} - ${result.reason}`);
    }
  }
  
  // Delete directories
  log('\n📁 Deleting directories...', 'blue');
  for (const dir of directoriesToDelete) {
    const result = dryRun ? { deleted: false, reason: 'dry run' } : deletePath(dir);
    if (result.deleted) {
      deletedCount++;
      log(`   ✓ Deleted: ${dir}`, 'green');
    } else if (!dryRun && result.reason !== 'not found') {
      errors.push(`Failed to delete ${dir}: ${result.reason}`);
      logError(`   ✗ Failed to delete: ${dir} - ${result.reason}`);
    }
  }
  
  // Modify files
  log('\n✏️  Modifying files...', 'blue');
  for (const file of filesToModify) {
    let result;
    
    if (dryRun) {
      result = { modified: false, reason: 'dry run' };
    } else {
      if (file.includes('schema.graphqls')) {
        result = cleanGraphQLSchema(file);
      } else if (file.includes('AppWiringFactory.kt')) {
        result = cleanAppWiringFactory(file);
      } else if (file.includes('Inputs.kt')) {
        result = cleanInputs(file);
      } else if (file.includes('Services.kt')) {
        result = cleanServices(file);
      } else if (file.includes('Repositories.kt')) {
        result = cleanRepositories(file);
      } else if (file.includes('examples/page.tsx')) {
        result = cleanExamplesPage(file);
      } else {
        result = { modified: false, reason: 'unknown file type' };
      }
    }
    
    if (result.modified) {
      modifiedCount++;
      log(`   ✓ Modified: ${file}`, 'green');
    } else if (result.reason && result.reason !== 'dry run' && result.reason !== 'not found') {
      errors.push(`Failed to modify ${file}: ${result.reason}`);
      logError(`   ✗ Failed to modify: ${file} - ${result.reason}`);
    }
  }
  
  // Summary
  log('\n✅ Cleanup completed!\n', 'bright');
  log('📊 Summary:', 'blue');
  log(`   • Files/directories deleted: ${deletedCount}`, 'green');
  log(`   • Files modified: ${modifiedCount}`, 'green');
  
  if (errors.length > 0) {
    log(`   • Errors: ${errors.length}`, 'red');
    log('\n⚠️  Errors encountered:', 'yellow');
    errors.forEach(error => logError(`   • ${error}`));
  }
  
  if (dryRun) {
    log('\n💡 This was a dry run. Run without --dry-run to apply changes.', 'yellow');
  } else {
    log('\n⚠️  Next steps:', 'yellow');
    log('   1. Review the changes with: git diff', 'yellow');
    log('   2. Check for any remaining customer/product references', 'yellow');
    log('   3. Regenerate GraphQL types:', 'yellow');
    log('      cd web && npm run codegen', 'yellow');
    log('   4. Regenerate supergraph schema:', 'yellow');
    log('      cd contracts/graphql && ./sync-schemas.sh', 'yellow');
    log('   5. Test the application builds:', 'yellow');
    log('      cd service/kotlin && ./gradlew build', 'yellow');
    log('      cd web && npm run build', 'yellow');
    log('   6. Commit the changes:', 'yellow');
    log('      git add . && git commit -m "Remove customer/product examples"', 'yellow');
    
    if (regenerate) {
      log('\n🔄 Regenerating artifacts...', 'blue');
      // Note: This would require running external commands
      // For now, just remind the user
      log('   Please run the regeneration commands manually:', 'yellow');
      log('   cd web && npm run codegen', 'yellow');
      log('   cd contracts/graphql && ./sync-schemas.sh', 'yellow');
    }
  }
  
  log('');
  
  if (errors.length > 0) {
    process.exit(1);
  }
}

// Run the script
try {
  main();
} catch (error) {
  logError(`\n❌ Fatal error: ${error.message}`);
  if (error.stack) {
    logError(error.stack);
  }
  process.exit(1);
}

