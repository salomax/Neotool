import { unstable_noStore as noStore } from 'next/cache';
import { generateConfigScript } from './runtime-config';

/**
 * Server Component that injects runtime configuration into the HTML
 * This script runs before React hydration, making config available immediately
 *
 * Uses noStore() to ensure this is always rendered at request time,
 * not cached at build time, so environment variables are read correctly.
 */
export function RuntimeConfigScript() {
  // Force dynamic rendering - ensures process.env is read at request time
  noStore();

  const configScript = generateConfigScript();

  return (
    <script
      id="runtime-config"
      dangerouslySetInnerHTML={{ __html: configScript }}
    />
  );
}
