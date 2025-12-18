import { ComponentData } from '../types';

export const textfieldData: ComponentData = {
  name: "TextField",
  description: "Input fields for text data with validation and formatting",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/TextField.tsx",
  type: "mui-simple" as const,
  muiDocsUrl: "https://mui.com/material-ui/react-text-field/",
  props: [
    { name: "label", type: "string", required: false, description: "Input label text" },
        { name: "placeholder", type: "string", required: false, description: "Placeholder text" },
        { name: "value", type: "string", required: false, description: "Input value" },
        { name: "onChange", type: "(event: ChangeEvent) => void", required: false, description: "Change handler" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the input", default: "false" },
        { name: "required", type: "boolean", required: false, description: "Required field", default: "false" },
        { name: "type", type: "'text' | 'email' | 'password' | 'number'", required: false, description: "Input type", default: "'text'" },
  ],
  examples: [
    { title: "Basic Input", description: "Simple text input with label" },
        { title: "Validation", description: "Input with error states and validation" },
        { title: "Types", description: "Different input types (email, password, number)" },
  ]
};
