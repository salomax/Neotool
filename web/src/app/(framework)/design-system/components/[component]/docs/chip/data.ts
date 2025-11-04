import { ComponentData } from '../types';

export const chipData: ComponentData = {
  name: "Chip",
  description: "Compact elements for tags, filters, and selections",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Chip.tsx",

  props: [
    { name: "label", type: "ReactNode", required: true, description: "Chip content" },
        { name: "color", type: "'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'", required: false, description: "Chip color", default: "'default'" },
        { name: "variant", type: "'filled' | 'outlined'", required: false, description: "Chip style", default: "'filled'" },
        { name: "size", type: "'small' | 'medium'", required: false, description: "Chip size", default: "'medium'" },
        { name: "onDelete", type: "() => void", required: false, description: "Delete handler" },
        { name: "clickable", type: "boolean", required: false, description: "Make chip clickable", default: "false" },
  ],
  examples: [
    { title: "Basic Chip", description: "Simple chip with label" },
        { title: "Deletable", description: "Chip with delete functionality" },
        { title: "Clickable", description: "Interactive clickable chip" },
  ]
};
