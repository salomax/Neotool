#!/usr/bin/env node

/**
 * Project Renaming Script
 * 
 * This script reads project.config.json and replaces all occurrences of
 * "neotool" references with the custom project name across all files and folders.
 */

import { readFileSync, writeFileSync, readdirSync, statSync, renameSync, existsSync } from 'fs';
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

// Load configuration
function loadConfig() {
  const configPath = join(projectRoot, 'project.config.json');
  
  if (!existsSync(configPath)) {
    logError(`Configuration file not found: ${configPath}`);
    logError('Please create project.config.json based on project.config.example.json');
    process.exit(1);
  }

  try {
    const configContent = readFileSync(configPath, 'utf-8');
    const config = JSON.parse(configContent);
    
    // Validate required fields
    const required = [
      'displayName', 'packageName', 'packageNamespace', 'databaseName',
      'databaseUser', 'serviceName', 'webPackageName', 'dockerImagePrefix',
      'routeGroup', 'githubOrg', 'githubRepo'
    ];
    
    const missing = required.filter(field => !config[field]);
    if (missing.length > 0) {
      logError(`Missing required configuration fields: ${missing.join(', ')}`);
      process.exit(1);
    }
    
    // Set defaults for optional fields
    config.apiDomain = config.apiDomain || `api.${config.packageName.replace(/-/g, '.')}`;
    config.logoName = config.logoName || `${config.packageName}-logo`;
    
    return config;
  } catch (error) {
    logError(`Error reading configuration: ${error.message}`);
    process.exit(1);
  }
}

// Mapping of old values to new values
function createReplacements(config) {
  // Extract base package name from namespace (e.g., "com.company.myproject" -> "myproject")
  const namespaceParts = config.packageNamespace.split('.');
  const basePackageName = namespaceParts[namespaceParts.length - 1];
  
  // Note: Order matters! More specific replacements should come first
  // to avoid partial matches replacing parts of longer strings
  return {
    // Package namespaces (longest first)
    'io.github.salomax.neotool': config.packageNamespace,
    'salomax.neotool': namespaceParts.slice(-2).join('.'),
    
    // Docker/Container names (specific container names first)
    'neotool-postgres-exporter': `${config.dockerImagePrefix}-postgres-exporter`,
    'neotool-redis-exporter': `${config.dockerImagePrefix}-redis-exporter`,
    'neotool-kafka-exporter': `${config.dockerImagePrefix}-kafka-exporter`,
    'neotool-graphql-router': `${config.dockerImagePrefix}-graphql-router`,
    'neotool-postgres': `${config.dockerImagePrefix}-postgres`,
    'neotool-prometheus': `${config.dockerImagePrefix}-prometheus`,
    'neotool-promtail': `${config.dockerImagePrefix}-promtail`,
    'neotool-grafana': `${config.dockerImagePrefix}-grafana`,
    'neotool-loki': `${config.dockerImagePrefix}-loki`,
    'neotool-redis': `${config.dockerImagePrefix}-redis`,
    'neotool-kafka': `${config.dockerImagePrefix}-kafka`,
    'neotool-api': `${config.dockerImagePrefix}-api`,
    
    // Docker images with tags (specific versions)
    'neotool-web:latest': `${config.webPackageName}:latest`,
    'neotool-backend:latest': `${config.dockerImagePrefix}-backend:latest`,
    'neotool-backend': `${config.dockerImagePrefix}-backend`,
    
    // Package names (before generic 'neotool' replacement)
    'neotool-web': config.webPackageName,
    'neotool-mobile': `${config.packageName}-mobile`,
    'neotool-service': config.serviceName,
    
    // Database names (specific database references)
    'neotool_db': config.databaseName,
    
    // Database user in environment variable patterns (before generic 'neotool' replacement)
    ':-neotool}': `:-${config.databaseUser}}`,
    ':neotool}': `:${config.databaseUser}}`,
    '-neotool}': `-${config.databaseUser}}`,
    '=neotool}': `=${config.databaseUser}}`,
    
    // GitHub URLs (full URLs before paths)
    'https://github.com/salomax/neotool': `https://github.com/${config.githubOrg}/${config.githubRepo}`,
    'github.com/salomax/neotool': `github.com/${config.githubOrg}/${config.githubRepo}`,
    'salomax/neotool': `${config.githubOrg}/${config.githubRepo}`,
    
    // Route groups (specific paths)
    '/(neotool)/': `/(${config.routeGroup})/`,
    '(neotool)': `(${config.routeGroup})`,
    
    // Logo files
    'neotool-logo': config.logoName,
    
    // API domains (full domains)
    'api.neotool.com': config.apiDomain,
    'neotool.com': config.apiDomain.replace(/^api\./, ''),
    
    // Grafana dashboard (specific references)
    'neotool-dashboards': `${config.packageName}-dashboards`,
    'neotool-overview': `${config.packageName}-overview`,
    'NeoTool - System Overview': `${config.displayName} - System Overview`,
    
    // ArgoCD
    'neotool-apps': `${config.packageName}-apps`,
    
    // Display names (case variations)
    'NeoTool': config.displayName,
    'Neotool': config.displayName,
    
    // Generic 'neotool' replacement (must be last to avoid replacing parts of longer strings)
    // This handles database user and other generic references
    'neotool': config.packageName,
  };
}

// Get all files recursively, excluding certain directories
function getAllFiles(dir, fileList = [], excludeDirs = []) {
  const files = readdirSync(dir);
  
  files.forEach(file => {
    const filePath = join(dir, file);
    const stat = statSync(filePath);
    const relativePath = relative(projectRoot, filePath);
    
    // Skip excluded directories
    if (stat.isDirectory()) {
      const shouldExclude = excludeDirs.some(exclude => 
        relativePath.startsWith(exclude) || file === exclude
      );
      if (!shouldExclude) {
        getAllFiles(filePath, fileList, excludeDirs);
      }
    } else {
      // Skip binary files and build artifacts
      const ext = file.split('.').pop().toLowerCase();
      const binaryExts = ['jar', 'class', 'png', 'jpg', 'jpeg', 'gif', 'ico', 'svg', 'woff', 'woff2', 'ttf', 'eot'];
      const buildDirs = ['node_modules', 'build', '.gradle', 'dist', '.next', 'coverage', 'storybook-static'];
      
      const inBuildDir = buildDirs.some(buildDir => relativePath.includes(buildDir));
      const isBinary = binaryExts.includes(ext) && !file.endsWith('.svg'); // Keep SVG as text
      
      if (!inBuildDir && !isBinary && !file.startsWith('.') && file !== 'package-lock.json') {
        fileList.push(filePath);
      }
    }
  });
  
  return fileList;
}

// Replace content in a file
function replaceInFile(filePath, replacements) {
  try {
    let content = readFileSync(filePath, 'utf-8');
    let modified = false;
    
    // Apply replacements
    for (const [oldValue, newValue] of Object.entries(replacements)) {
      if (content.includes(oldValue)) {
        // Use regex for case-sensitive replacement
        const regex = new RegExp(escapeRegex(oldValue), 'g');
        content = content.replace(regex, newValue);
        modified = true;
      }
    }
    
    if (modified) {
      writeFileSync(filePath, content, 'utf-8');
      return true;
    }
    
    return false;
  } catch (error) {
    logError(`Error processing file ${filePath}: ${error.message}`);
    return false;
  }
}

function escapeRegex(str) {
  return str.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

// Rename files and directories
function renameFilesAndDirs(config, replacements) {
  const filesToRename = [];
  const dirsToRename = [];
  
  // Find files to rename
  function findItemsToRename(dir, depth = 0) {
    const items = readdirSync(dir);
    
    items.forEach(item => {
      const itemPath = join(dir, item);
      const stat = statSync(itemPath);
      const relativePath = relative(projectRoot, itemPath);
      
      // Skip build directories
      if (relativePath.includes('node_modules') || 
          relativePath.includes('build') || 
          relativePath.includes('.gradle') ||
          relativePath.includes('.next') ||
          relativePath.includes('coverage')) {
        return;
      }
      
      if (stat.isDirectory()) {
        // Check if directory name needs renaming
        if (item === '(neotool)') {
          dirsToRename.push({
            oldPath: itemPath,
            newName: `(${config.routeGroup})`,
            parent: dir
          });
        } else if (item.includes('neotool')) {
          // Rename directories containing neotool
          const newName = item.replace(/neotool/g, config.packageName);
          dirsToRename.push({
            oldPath: itemPath,
            newName: newName,
            parent: dir
          });
        } else {
          // Recurse into directory
          findItemsToRename(itemPath, depth + 1);
        }
      } else {
        // Check if file name needs renaming
        if (item.includes('neotool')) {
          const newName = item.replace(/neotool/g, config.packageName);
          // Special handling for logo files
          if (item.includes('neotool-logo')) {
            const logoNewName = item.replace(/neotool-logo/g, config.logoName);
            filesToRename.push({
              oldPath: itemPath,
              newName: logoNewName,
              parent: dir
            });
          } else {
            filesToRename.push({
              oldPath: itemPath,
              newName: newName,
              parent: dir
            });
          }
        }
      }
    });
  }
  
  findItemsToRename(projectRoot);
  
  // Rename directories first (deepest first)
  dirsToRename.sort((a, b) => b.oldPath.split('/').length - a.oldPath.split('/').length);
  for (const dir of dirsToRename) {
    const newPath = join(dir.parent, dir.newName);
    try {
      renameSync(dir.oldPath, newPath);
      log(`Renamed directory: ${relative(projectRoot, dir.oldPath)} -> ${relative(projectRoot, newPath)}`, 'cyan');
    } catch (error) {
      logError(`Error renaming directory ${dir.oldPath}: ${error.message}`);
    }
  }
  
  // Rename files
  for (const file of filesToRename) {
    const newPath = join(file.parent, file.newName);
    try {
      renameSync(file.oldPath, newPath);
      log(`Renamed file: ${relative(projectRoot, file.oldPath)} -> ${relative(projectRoot, newPath)}`, 'cyan');
    } catch (error) {
      logError(`Error renaming file ${file.oldPath}: ${error.message}`);
    }
  }
  
  return { filesRenamed: filesToRename.length, dirsRenamed: dirsToRename.length };
}

// Main execution
function main() {
  log('\n🚀 Starting project renaming process...\n', 'bright');
  
  // Load configuration
  log('📋 Loading configuration...', 'blue');
  const config = loadConfig();
  log(`   ✓ Display Name: ${config.displayName}`, 'green');
  log(`   ✓ Package Name: ${config.packageName}`, 'green');
  log(`   ✓ Package Namespace: ${config.packageNamespace}`, 'green');
  log(`   ✓ Route Group: ${config.routeGroup}\n`, 'green');
  
  // Warn if still using default "neotool" values
  if (config.packageName === 'neotool' && config.githubOrg === 'salomax') {
    log('⚠️  Warning: Configuration still contains default "neotool" values.', 'yellow');
    log('   If you want to rename the project, please edit project.config.json first.\n', 'yellow');
    log('   Proceeding with current configuration...\n', 'yellow');
  }
  
  // Create replacements map
  const replacements = createReplacements(config);
  
  // Get all files
  log('📁 Scanning files...', 'blue');
  const excludeDirs = [
    'node_modules', '.git', 'build', '.gradle', '.next', 
    'coverage', 'storybook-static', 'bin', 'dist'
  ];
  const allFiles = getAllFiles(projectRoot, [], excludeDirs);
  log(`   ✓ Found ${allFiles.length} files to process\n`, 'green');
  
  // Process files
  log('✏️  Processing files...', 'blue');
  let filesModified = 0;
  let errors = 0;
  
  for (const file of allFiles) {
    if (replaceInFile(file, replacements)) {
      filesModified++;
      if (filesModified % 50 === 0) {
        process.stdout.write('.');
      }
    }
  }
  
  log(`\n   ✓ Modified ${filesModified} files\n`, 'green');
  
  // Rename files and directories
  log('📝 Renaming files and directories...', 'blue');
  const renameResults = renameFilesAndDirs(config, replacements);
  log(`   ✓ Renamed ${renameResults.filesRenamed} files and ${renameResults.dirsRenamed} directories\n`, 'green');
  
  // Summary
  log('\n✅ Project renaming completed!\n', 'bright');
  log('📊 Summary:', 'blue');
  log(`   • Files modified: ${filesModified}`, 'green');
  log(`   • Files renamed: ${renameResults.filesRenamed}`, 'green');
  log(`   • Directories renamed: ${renameResults.dirsRenamed}`, 'green');
  
  log('\n⚠️  Next steps:', 'yellow');
  log('   1. Review the changes with: git diff', 'yellow');
  log('   2. Update any remaining references manually', 'yellow');
  log('   3. Test the application to ensure everything works', 'yellow');
  log('   4. Update logo files in design/assets/logos/ if needed', 'yellow');
  log('   5. Update neotool.code-workspace filename if desired', 'yellow');
  log('   6. Commit the changes: git add . && git commit -m "Rename project from neotool to ' + config.packageName + '"', 'yellow');
  log('');
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

