import { ComponentData } from '../types';

export const switcherData: ComponentData = {
  name: "Switcher",
  description: "Responsive grid that switches from 1 to N columns based on available width",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Switcher.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between items", default: "undefined" },
        { name: "threshold", type: "ResponsiveValue<string | number>", required: false, description: "Minimum width before switching to fewer columns", default: "'30rem'" },
        { name: "maxCols", type: "ResponsiveValue<number>", required: false, description: "Maximum number of columns", default: "4" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Switcher", description: "Simple responsive column switcher" },
        { title: "With Threshold", description: "Switcher with custom threshold value" },
        { title: "With Max Cols", description: "Switcher with maximum column limit" },
        { title: "Responsive", description: "Switcher with responsive threshold values" },
        { title: "Different Sizes", description: "Switcher with items of different sizes" }
  ]
};
