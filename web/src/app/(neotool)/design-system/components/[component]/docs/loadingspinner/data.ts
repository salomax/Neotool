import { ComponentData } from '../types';

export const loadingspinnerData: ComponentData = {
  name: "LoadingSpinner",
  description: "Indicates ongoing processes and loading states",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/LoadingSpinner.tsx",
  
  props: [
    { name: "size", type: "number | 'small' | 'medium' | 'large'", required: false, description: "Spinner size", default: "'medium'" },
        { name: "color", type: "'inherit' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'", required: false, description: "Spinner color", default: "'primary'" },
        { name: "message", type: "string", required: false, description: "Loading message" },
        { name: "thickness", type: "number", required: false, description: "Stroke thickness", default: "3.6" },
  ],
  examples: [
    { title: "Basic Spinner", description: "Simple loading spinner" },
        { title: "With Message", description: "Spinner with loading text" },
        { title: "Different Sizes", description: "Various spinner sizes" },
  ]
};
