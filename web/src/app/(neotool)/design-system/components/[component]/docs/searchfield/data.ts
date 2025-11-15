import { ComponentData } from '../types';

export const searchfieldData: ComponentData = {
  name: "SearchField",
  description: "Input field specifically for search queries",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/SearchField.tsx",

  props: [
    { name: "placeholder", type: "string", required: false, description: "Search placeholder", default: "'Search...'" },
        { name: "value", type: "string", required: false, description: "Search value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "onSearch", type: "(query: string) => void", required: false, description: "Search handler" },
        { name: "loading", type: "boolean", required: false, description: "Loading state", default: "false" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Search", description: "Simple search field" },
        { title: "With Loading", description: "Search field with loading state" },
        { title: "Advanced Search", description: "Search with filters and options" },
  ]
};
