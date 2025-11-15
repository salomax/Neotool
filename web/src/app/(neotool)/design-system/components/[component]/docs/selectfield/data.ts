import { ComponentData } from '../types';

export const selectfieldData: ComponentData = {
  name: "SelectField",
  description: "Dropdown selection component with options",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/SelectField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Select label text" },
        { name: "value", type: "string | string[]", required: false, description: "Selected value(s)" },
        { name: "onChange", type: "(event: ChangeEvent) => void", required: false, description: "Change handler" },
        { name: "options", type: "Array<{value: string, label: string}>", required: true, description: "Select options" },
        { name: "multiple", type: "boolean", required: false, description: "Allow multiple selections", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the select", default: "false" },
  ],
  examples: [
    { title: "Basic Select", description: "Simple dropdown selection" },
        { title: "Multiple Select", description: "Multi-selection dropdown" },
        { title: "With Options", description: "Select with predefined options" },
  ]
};
