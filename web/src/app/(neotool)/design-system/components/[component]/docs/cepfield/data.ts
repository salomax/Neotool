import { ComponentData } from '../types';

export const cepfieldData: ComponentData = {
  name: "CEPField",
  description: "Input field for Brazilian postal codes (CEP)",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/br/CEPField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "CEP value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "onBlur", type: "() => void", required: false, description: "Blur handler for validation" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic CEP", description: "Simple CEP input field" },
        { title: "With Validation", description: "CEP field with validation" },
        { title: "Auto Complete", description: "CEP field with address lookup" },
  ]
};
