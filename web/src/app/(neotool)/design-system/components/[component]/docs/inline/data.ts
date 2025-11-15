import { ComponentData } from '../types';

export const inlineData: ComponentData = {
  name: "Inline",
  description: "Horizontal row layout component with optional wrapping for arranging content in a row",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/layout/Inline.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between items", default: "undefined" },
        { name: "align", type: "ResponsiveValue<AlignValue>", required: false, description: "Cross-axis alignment", default: "undefined" },
        { name: "justify", type: "ResponsiveValue<JustifyValue>", required: false, description: "Main-axis justification", default: "undefined" },
        { name: "wrap", type: "boolean", required: false, description: "Whether to wrap items to new lines", default: "true" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Inline", description: "Simple horizontal layout with wrapping" },
        { title: "No Wrap", description: "Horizontal layout without wrapping" },
        { title: "Centered", description: "Inline with centered alignment" },
        { title: "Space Between", description: "Inline with space-between justification" },
        { title: "Many Items", description: "Inline layout with many items that wrap" }
  ]
};
