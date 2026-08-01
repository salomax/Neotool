#!/usr/bin/env node

/**
 * Development script that loads environment variables from .env.local and
 * lets .env.devpod override workspace-aware values when present.
 */

import { spawn } from 'child_process';
import { readFileSync, existsSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const env = { ...process.env };
const processEnvKeys = new Set(Object.keys(process.env));

function loadEnvFile(envPath, { override = false } = {}) {
  if (!existsSync(envPath)) {
    return false;
  }

  const envContent = readFileSync(envPath, 'utf-8');
  const lines = envContent.split('\n');

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }

    const match = trimmed.match(/^([^=]+)=(.*)$/);
    if (!match) {
      continue;
    }

    const [, key, value] = match;
    const cleanValue = value.replace(/^["']|["']$/g, '');
    if (processEnvKeys.has(key)) {
      continue;
    }
    if (override || !env[key]) {
      env[key] = cleanValue;
    }
  }

  return true;
}

const localEnvPath = join(__dirname, '../.env.local');
const devpodEnvPath = join(__dirname, '../.env.devpod');

const loadedLocal = loadEnvFile(localEnvPath);
const loadedDevpod = loadEnvFile(devpodEnvPath, { override: true });

if (loadedLocal) {
  console.log(`✓ Loaded environment variables from ${localEnvPath}`);
}
if (loadedDevpod) {
  console.log(`✓ Applied DevPod overrides from ${devpodEnvPath}`);
}
if (!loadedLocal && !loadedDevpod) {
  console.warn(`⚠ Environment files not found: ${localEnvPath}, ${devpodEnvPath}`);
  console.warn('  Create web/.env.local or let the DevPod bootstrap generate web/.env.devpod');
}

// Start Next.js dev server with the loaded environment variables.
// Respect NODE_OPTIONS from the environment (e.g. devcontainer remoteEnv sets 1536 for
// the 11GB DevPod). Only apply a default heap cap when none is configured.
if (!/\bmax-old-space-size=\d+/i.test(env.NODE_OPTIONS ?? '')) {
  env.NODE_OPTIONS = `${env.NODE_OPTIONS ?? ''} --max-old-space-size=6144`.trim();
}
const nextDev = spawn('next', ['dev'], {
  stdio: 'inherit',
  shell: false,
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
