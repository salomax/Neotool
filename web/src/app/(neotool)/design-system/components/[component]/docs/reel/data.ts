import { ComponentData } from '../types';

export const reelData: ComponentData = {
  name: "Reel",
  description: "Horizontal scrolling container with scroll-snap support for carousels and galleries",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Reel.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing between items", default: "undefined" },
        { name: "height", type: "ResponsiveValue<string | number>", required: false, description: "Height of the reel", default: "'auto'" },
        { name: "showScrollbar", type: "boolean", required: false, description: "Whether to show scrollbar", default: "false" },
        { name: "snapAlign", type: "'start' | 'center' | 'end' | 'none'", required: false, description: "Scroll snap alignment", default: "'start'" },
        { name: "itemWidth", type: "ResponsiveValue<string | number>", required: false, description: "Fixed width for items", default: "undefined" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Reel", description: "Simple horizontal scrolling container" },
        { title: "With Scrollbar", description: "Reel with visible scrollbar" },
        { title: "Center Snap", description: "Reel with center scroll snap alignment" },
        { title: "Fixed Item Width", description: "Reel with fixed item widths" },
        { title: "Many Items", description: "Reel with many scrollable items" }
  ]
};
