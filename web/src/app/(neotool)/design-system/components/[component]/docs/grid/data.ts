import { ComponentData } from '../types';

export const gridData: ComponentData = {
  name: "Grid",
  description: "CSS Grid layout with auto-fit and template areas support for complex layouts",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Grid.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between grid items", default: "undefined" },
        { name: "minColWidth", type: "ResponsiveValue<string | number>", required: false, description: "Minimum column width for auto-fit", default: "'250px'" },
        { name: "cols", type: "ResponsiveValue<number>", required: false, description: "Fixed number of columns", default: "undefined" },
        { name: "areas", type: "ResponsiveValue<string>", required: false, description: "Grid template areas", default: "undefined" },
        { name: "columns", type: "ResponsiveValue<string>", required: false, description: "Custom grid template columns", default: "undefined" },
        { name: "rows", type: "ResponsiveValue<string>", required: false, description: "Custom grid template rows", default: "undefined" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Grid", description: "Simple grid with auto-fit columns" },
        { title: "With Min Col Width", description: "Grid with minimum column width" },
        { title: "Fixed Columns", description: "Grid with fixed number of columns" },
        { title: "Custom Template", description: "Grid with custom template areas" },
        { title: "Responsive", description: "Grid with responsive column counts" }
  ]
};
