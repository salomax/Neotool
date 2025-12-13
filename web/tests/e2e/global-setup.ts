/**
 * Global setup for Playwright E2E tests
 * This runs once before all tests
 */

import { FullConfig } from '@playwright/test';
import { currentConfig } from './config/environments';

async function globalSetup(config: FullConfig) {
  console.log('Running global setup...');
  console.log(`Environment: ${process.env.PLAYWRIGHT_TEST_ENV || 'development'}`);
  console.log(`Base URL: ${currentConfig.baseURL}`);
  console.log(`GraphQL Endpoint: ${currentConfig.graphqlEndpoint}`);
  
  // Verify backend is accessible
  try {
    const response = await fetch(currentConfig.graphqlEndpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: '{ __typename }' }),
    });
    
    if (!response.ok) {
      console.warn(`Warning: GraphQL endpoint returned ${response.status}`);
    } else {
      console.log('✓ GraphQL endpoint is accessible');
    }
  } catch (error) {
    console.warn('Warning: Could not verify GraphQL endpoint:', error);
  }
  
  // Additional setup can be added here:
  // - Create test database state
  // - Seed test data
  // - Setup test authentication
  // - Verify test environment
  
  console.log('Global setup completed');
}

export default globalSetup;
