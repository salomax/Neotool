import { ComponentData } from '../types';

export const togglefieldData: ComponentData = {
  name: "ToggleField",
  description: "Switch input for boolean values with toggle styling",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/ToggleField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Toggle label text" },
        { name: "checked", type: "boolean", required: false, description: "Toggle state", default: "false" },
        { name: "onChange", type: "(checked: boolean) => void", required: false, description: "Change handler" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the toggle", default: "false" },
        { name: "color", type: "'primary' | 'secondary' | 'success' | 'error' | 'warning' | 'info'", required: false, description: "Toggle color", default: "'primary'" },
  ],
  examples: [
    { title: "Basic Toggle", description: "Simple on/off switch" },
        { title: "With Labels", description: "Toggle with descriptive labels" },
        { title: "Different States", description: "Various toggle states and colors" },
  ]
};
