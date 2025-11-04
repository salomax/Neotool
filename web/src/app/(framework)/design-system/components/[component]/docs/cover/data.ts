import { ComponentData } from '../types';

export const coverData: ComponentData = {
  name: "Cover",
  description: "Centered block layout with optional header and footer for hero sections",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Cover.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between sections", default: "undefined" },
        { name: "minHeight", type: "ResponsiveValue<string | number>", required: false, description: "Minimum height of the cover", default: "'50vh'" },
        { name: "top", type: "ReactNode", required: false, description: "Content to place at the top", default: "undefined" },
        { name: "bottom", type: "ReactNode", required: false, description: "Content to place at the bottom", default: "undefined" },
        { name: "center", type: "boolean", required: false, description: "Whether to center the main content", default: "true" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Cover", description: "Simple centered layout" },
        { title: "With Header Footer", description: "Cover with header and footer sections" },
        { title: "Custom Height", description: "Cover with custom minimum height" },
        { title: "Not Centered", description: "Cover without centering main content" },
        { title: "Full Page", description: "Full-page cover layout" }
  ]
};
