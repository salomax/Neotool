import { ComponentData } from '../types';

export const numberfieldData: ComponentData = {
  name: "NumberField",
  description: "Input field specifically for numerical values with validation",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/NumberField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "number", required: false, description: "Numeric value" },
        { name: "onChange", type: "(value: number) => void", required: false, description: "Change handler" },
        { name: "min", type: "number", required: false, description: "Minimum value" },
        { name: "max", type: "number", required: false, description: "Maximum value" },
        { name: "step", type: "number", required: false, description: "Step increment", default: "1" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Number", description: "Simple numeric input" },
        { title: "With Constraints", description: "Number input with min/max values" },
        { title: "Decimal Input", description: "Number input with decimal precision" },
  ]
};
