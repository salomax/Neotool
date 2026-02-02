#!/usr/bin/env node

/**
 * Development script that loads environment variables from .env.local
 * and then starts Next.js dev server
 */

import { spawn } from 'child_process';
import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Path to infra/.env.local relative to this script
const envPath = join(__dirname, '../.env.local');
const env = { ...process.env };

if (existsSync(envPath)) {
  const envContent = readFileSync(envPath, 'utf-8');
  const lines = envContent.split('\n');
  
  for (const line of lines) {
    // Skip comments and empty lines
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    
    // Parse KEY=VALUE format
    const match = trimmed.match(/^([^=]+)=(.*)$/);
    if (match) {
      const [, key, value] = match;
      // Remove quotes if present
      const cleanValue = value.replace(/^["']|["']$/g, '');
      // Only set if not already set (env vars take precedence)
      if (!env[key]) {
        env[key] = cleanValue;
      }
    }
  }
  
  console.log(`✓ Loaded environment variables from ${envPath}`);
} else {
  console.warn(`⚠ Environment file not found: ${envPath}`);
  console.warn('  Create infra/.env.local or use web/.env.local for local development');
}

// Start Next.js dev server with the loaded environment variables
const nextDev = spawn('next', ['dev'], {
  stdio: 'inherit',
  shell: true,
  env,
  cwd: join(__dirname, '..'),
});

nextDev.on('error', (error) => {
  console.error('Failed to start Next.js:', error);
  process.exit(1);
});

nextDev.on('exit', (code) => {
  process.exit(code || 0);
});

// Handle Ctrl+C
process.on('SIGINT', () => {
  nextDev.kill('SIGINT');
});

process.on('SIGTERM', () => {
  nextDev.kill('SIGTERM');
});

