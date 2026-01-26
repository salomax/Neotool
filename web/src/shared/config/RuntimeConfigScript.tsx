import { generateConfigScript } from './runtime-config';

/**
 * Server Component that injects runtime configuration into the HTML
 * This script runs before React hydration, making config available immediately
 */
export function RuntimeConfigScript() {
  const configScript = generateConfigScript();

  return (
    <script
      id="runtime-config"
      dangerouslySetInnerHTML={{ __html: configScript }}
    />
  );
}
