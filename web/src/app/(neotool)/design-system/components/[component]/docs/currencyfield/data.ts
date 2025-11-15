import { ComponentData } from '../types';

export const currencyfieldData: ComponentData = {
  name: "CurrencyField",
  description: "Formatted input for monetary values with currency symbols",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/CurrencyField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "number", required: false, description: "Currency value" },
        { name: "onChange", type: "(value: number) => void", required: false, description: "Change handler" },
        { name: "currency", type: "string", required: false, description: "Currency code", default: "'USD'" },
        { name: "locale", type: "string", required: false, description: "Locale for formatting", default: "'en-US'" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Currency", description: "Simple currency input" },
        { title: "Different Currencies", description: "Various currency formats" },
        { title: "Formatted Display", description: "Properly formatted currency display" },
  ]
};
