import { ComponentData } from '../types';

export const maskedfieldData: ComponentData = {
  name: "MaskedField",
  description: "Input field with predefined masks",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/MaskedField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "Input value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "mask", type: "string", required: true, description: "Input mask pattern" },
        { name: "placeholder", type: "string", required: false, description: "Placeholder text" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Phone Mask", description: "Phone number with formatting" },
        { title: "Date Mask", description: "Date input with mask" },
        { title: "Custom Mask", description: "Custom mask pattern" },
  ]
};
