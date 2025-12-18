import { ComponentData } from '../types';

export const stackData: ComponentData = {
  name: "Stack",
  description: "Vertical layout component using flexbox for arranging content in a column",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/layout/Stack.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between items", default: "undefined" },
        { name: "align", type: "ResponsiveValue<AlignValue>", required: false, description: "Cross-axis alignment", default: "undefined" },
        { name: "justify", type: "ResponsiveValue<JustifyValue>", required: false, description: "Main-axis justification", default: "undefined" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Stack", description: "Simple vertical layout with default spacing" },
        { title: "With Gap", description: "Stack with custom spacing between items" },
        { title: "Centered", description: "Stack with centered alignment and justification" },
        { title: "Space Between", description: "Stack with space-between justification" },
        { title: "Responsive", description: "Stack with responsive gap values" }
  ]
};
