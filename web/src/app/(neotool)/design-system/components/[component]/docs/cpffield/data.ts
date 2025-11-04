import { ComponentData } from '../types';

export const cpffieldData: ComponentData = {
  name: "CPFField",
  description: "Input field for Brazilian individual registration number (CPF)",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/br/CPFField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "CPF value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic CPF", description: "Simple CPF input field" },
        { title: "With Formatting", description: "CPF field with automatic formatting" },
        { title: "With Validation", description: "CPF field with validation" },
  ]
};
