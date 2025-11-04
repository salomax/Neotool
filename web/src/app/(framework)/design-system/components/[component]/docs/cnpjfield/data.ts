import { ComponentData } from '../types';

export const cnpjfieldData: ComponentData = {
  name: "CNPJField",
  description: "Input field for Brazilian company registration number (CNPJ)",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/br/CNPJField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "CNPJ value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic CNPJ", description: "Simple CNPJ input field" },
        { title: "With Formatting", description: "CNPJ field with automatic formatting" },
        { title: "With Validation", description: "CNPJ field with validation" },
  ]
};
