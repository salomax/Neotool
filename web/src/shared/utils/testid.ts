/**
 * Utility functions for generating data-testid attributes
 */

/**
 * Generates a data-testid attribute from component name and optional name prop
 * @param componentName - The name of the component (e.g., 'Paper', 'Button')
 * @param name - Optional name prop passed to the component
 * @returns The generated data-testid string
 * 
 * @example
 * generateTestId('Paper', 'search-filters') // returns 'paper-search-filters'
 * generateTestId('Button', 'submit') // returns 'button-submit'
 * generateTestId('Paper') // returns 'paper'
 */
export function generateTestId(componentName: string, name?: string): string {
  const normalizedComponentName = componentName.toLowerCase();
  
  if (!name || name === '') {
    return normalizedComponentName;
  }
  
  return `${normalizedComponentName}-${name}`;
}

/**
 * Generates data-testid props for a component
 * @param componentName - The name of the component
 * @param name - Optional name prop
 * @param existingTestId - Existing data-testid prop (takes precedence)
 * @returns Object with data-testid property
 * 
 * @example
 * getTestIdProps('Paper', 'search-filters') // returns { 'data-testid': 'paper-search-filters' }
 * getTestIdProps('Button', 'submit', 'custom-id') // returns { 'data-testid': 'custom-id' }
 */
export function getTestIdProps(
  componentName: string, 
  name?: string, 
  existingTestId?: string
): { 'data-testid': string } {
  return {
    'data-testid': existingTestId || generateTestId(componentName, name)
  };
}
