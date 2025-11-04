#!/usr/bin/env node

import { execSync } from 'child_process';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const projectRoot = join(__dirname, '..');

console.log('🧪 Running i18n tests with coverage...\n');

try {
  // Change to web directory
  process.chdir(projectRoot);
  
  // Run tests with coverage for i18n
  const command = 'npm run test:coverage -- --reporter=text --reporter=html src/shared/i18n';
  
  console.log(`Running: ${command}\n`);
  
  execSync(command, { 
    stdio: 'inherit',
    cwd: projectRoot
  });
  
  console.log('\n✅ i18n tests completed successfully!');
  console.log('\n📊 Coverage report generated in: coverage/');
  console.log('🌐 Open coverage/index.html in your browser to view detailed coverage');
  
} catch (error) {
  console.error('❌ Tests failed:', error.message);
  process.exit(1);
}
