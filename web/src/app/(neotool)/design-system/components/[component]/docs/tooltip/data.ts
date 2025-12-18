import { ComponentData } from '../types';

export const tooltipData: ComponentData = {
  name: "Tooltip",
  description: "Displays informative text when a user hovers over an element",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Tooltip.tsx",

  props: [
    { name: "title", type: "ReactNode", required: true, description: "Tooltip content" },
        { name: "children", type: "ReactElement", required: true, description: "Element to attach tooltip to" },
        { name: "placement", type: "'bottom-end' | 'bottom-start' | 'bottom' | 'left-end' | 'left-start' | 'left' | 'right-end' | 'right-start' | 'right' | 'top-end' | 'top-start' | 'top'", required: false, description: "Tooltip placement", default: "'bottom'" },
        { name: "arrow", type: "boolean", required: false, description: "Show arrow", default: "false" },
        { name: "open", type: "boolean", required: false, description: "Control tooltip visibility" },
  ],
  examples: [
    { title: "Basic Tooltip", description: "Simple tooltip on hover" },
        { title: "With Arrow", description: "Tooltip with arrow pointer" },
        { title: "Different Placements", description: "Tooltips in various positions" },
  ]
};
