import { ComponentData } from '../types';

export const progressbarData: ComponentData = {
  name: "ProgressBar",
  description: "Versatile progress indicator component with linear, circular, and step variants",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/ProgressBar.tsx",

  props: [
    { name: "value", type: "number", required: false, description: "Current progress value (0-100)", default: "0" },
        { name: "indeterminate", type: "boolean", required: false, description: "Whether the progress is indeterminate", default: "false" },
        { name: "variant", type: "'linear' | 'circular' | 'step'", required: false, description: "Progress bar variant", default: "'linear'" },
        { name: "size", type: "'small' | 'medium' | 'large'", required: false, description: "Progress bar size", default: "'medium'" },
        { name: "color", type: "'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'", required: false, description: "Progress bar color", default: "'primary'" },
        { name: "thickness", type: "number", required: false, description: "Progress bar thickness (for circular)", default: "4" },
        { name: "width", type: "number | string", required: false, description: "Progress bar width (for linear)" },
        { name: "height", type: "number", required: false, description: "Progress bar height (for linear)", default: "8" },
        { name: "showPercentage", type: "boolean", required: false, description: "Whether to show percentage", default: "true" },
        { name: "showLabel", type: "boolean", required: false, description: "Whether to show label", default: "true" },
        { name: "label", type: "string", required: false, description: "Custom label text" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the progress bar" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "errorMessage", type: "string", required: false, description: "Error message" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the progress bar is disabled", default: "false" },
        { name: "currentStep", type: "number", required: false, description: "Current step index (0-based) for step variant", default: "0" },
        { name: "totalSteps", type: "number", required: false, description: "Total number of steps for step variant", default: "3" },
        { name: "steps", type: "string[]", required: false, description: "Step labels for step variant", default: "[]" },
        { name: "showStepContent", type: "boolean", required: false, description: "Whether to show step content for step variant", default: "false" },
        { name: "stepContent", type: "React.ReactNode[]", required: false, description: "Step content for step variant", default: "[]" },
        { name: "clickable", type: "boolean", required: false, description: "Whether steps are clickable for step variant", default: "false" },
        { name: "onStepClick", type: "(stepIndex: number) => void", required: false, description: "Callback fired when step is clicked for step variant" },
        { name: "stepStatus", type: "('completed' | 'active' | 'pending' | 'error')[]", required: false, description: "Step status for step variant", default: "[]" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic ProgressBar", description: "Simple progress bar with default settings" },
        { title: "Linear Progress", description: "Linear progress bar variant" },
        { title: "Circular Progress", description: "Circular progress bar variant" },
        { title: "Step Progress", description: "Step-by-step progress indicator" },
        { title: "Indeterminate", description: "Indeterminate progress for loading states" },
        { title: "With Status", description: "Progress bar with different status indicators" }
  ]
};
