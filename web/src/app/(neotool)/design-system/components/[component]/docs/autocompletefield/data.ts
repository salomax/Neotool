import { ComponentData } from '../types';

export const autocompletefieldData: ComponentData = {
  name: "AutocompleteField",
  description: "Text input with auto-completion suggestions and dropdown",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/AutocompleteField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "Input value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "options", type: "string[]", required: true, description: "Available options" },
        { name: "freeSolo", type: "boolean", required: false, description: "Allow custom input", default: "false" },
        { name: "multiple", type: "boolean", required: false, description: "Allow multiple selections", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Autocomplete", description: "Simple autocomplete with suggestions" },
        { title: "Free Solo", description: "Autocomplete that allows custom input" },
        { title: "Multiple Selection", description: "Select multiple options from suggestions" },
  ]
};
