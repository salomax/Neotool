import { ComponentData } from '../types';

export const clusterData: ComponentData = {
  name: "Cluster",
  description: "Wrapped row layout for chips, filters, and similar items with minimum item width",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Cluster.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between items", default: "undefined" },
        { name: "align", type: "ResponsiveValue<AlignValue>", required: false, description: "Cross-axis alignment", default: "undefined" },
        { name: "justify", type: "ResponsiveValue<JustifyValue>", required: false, description: "Main-axis justification", default: "undefined" },
        { name: "minItemWidth", type: "ResponsiveValue<string | number>", required: false, description: "Minimum width for each item before wrapping", default: "'auto'" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Cluster", description: "Simple wrapped layout for chips" },
        { title: "With Min Width", description: "Cluster with minimum item width" },
        { title: "Centered", description: "Cluster with centered alignment" },
        { title: "Space Between", description: "Cluster with space-between justification" },
        { title: "Different Sizes", description: "Cluster with items of different sizes" }
  ]
};
