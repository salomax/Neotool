import { ComponentData } from '../types';

export const percentfieldData: ComponentData = {
  name: "PercentField",
  description: "Input field for percentage values with proper formatting",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/PercentField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "number", required: false, description: "Percentage value (0-100)" },
        { name: "onChange", type: "(value: number) => void", required: false, description: "Change handler" },
        { name: "min", type: "number", required: false, description: "Minimum percentage", default: "0" },
        { name: "max", type: "number", required: false, description: "Maximum percentage", default: "100" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Percentage", description: "Simple percentage input" },
        { title: "With Range", description: "Percentage input with min/max constraints" },
        { title: "Formatted Display", description: "Percentage with proper formatting" },
  ]
};
