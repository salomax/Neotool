import { ComponentData } from '../types';

export const switchData: ComponentData = {
  name: "Switch",
  description: "Versatile switch component for toggle states with various customization options",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Switch.tsx",

  props: [
    { name: "checked", type: "boolean", required: false, description: "Whether the switch is checked" },
        { name: "defaultChecked", type: "boolean", required: false, description: "Default checked state (uncontrolled)", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the switch is disabled", default: "false" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the switch is read-only", default: "false" },
        { name: "size", type: "'small' | 'medium'", required: false, description: "Size of the switch", default: "'medium'" },
        { name: "color", type: "'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success' | 'default'", required: false, description: "Color theme of the switch", default: "'primary'" },
        { name: "label", type: "string", required: false, description: "Label for the switch" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the switch" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "showLabel", type: "boolean", required: false, description: "Whether to show the label", default: "true" },
        { name: "labelPlacement", type: "'start' | 'end' | 'top' | 'bottom'", required: false, description: "Label placement", default: "'end'" },
        { name: "labelComponent", type: "React.ReactNode", required: false, description: "Custom label component" },
        { name: "checkedLabel", type: "string", required: false, description: "Custom checked label" },
        { name: "uncheckedLabel", type: "string", required: false, description: "Custom unchecked label" },
        { name: "showStatus", type: "boolean", required: false, description: "Whether to show status text", default: "false" },
        { name: "statusFormatter", type: "(checked: boolean) => string", required: false, description: "Custom status formatter", default: "(checked) => checked ? 'On' : 'Off'" },
        { name: "onChange", type: "(checked: boolean) => void", required: false, description: "Callback fired when the state changes" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic Switch", description: "Simple switch with default settings" },
        { title: "With Status", description: "Switch showing current state" },
        { title: "Custom Labels", description: "Switch with custom checked/unchecked labels" },
        { title: "Different Placements", description: "Switch with various label placements" },
        { title: "Different Sizes", description: "Small and medium sized switches" },
        { title: "Different Colors", description: "Switch with various color themes" }
  ]
};
