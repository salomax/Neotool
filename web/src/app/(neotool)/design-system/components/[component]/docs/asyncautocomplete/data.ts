import { ComponentData } from '../types';

export const asyncautocompleteData: ComponentData = {
  name: "AsyncAutocomplete",
  description: "Autocomplete with asynchronous data loading",
  status: "beta" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/AsyncAutocomplete.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "Input value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "loadOptions", type: "(query: string) => Promise<Option[]>", required: true, description: "Function to load options asynchronously" },
        { name: "loading", type: "boolean", required: false, description: "Loading state", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Async", description: "Simple async autocomplete" },
        { title: "With Loading", description: "Async autocomplete with loading state" },
        { title: "API Integration", description: "Autocomplete connected to API" },
  ]
};
