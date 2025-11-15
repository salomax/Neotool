import { ComponentData } from '../types';

export const frameData: ComponentData = {
  name: "Frame",
  description: "Aspect-ratio container for media and charts with optional cropping",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/layout/Frame.tsx",

  props: [
    { name: "gap", type: "ResponsiveValue<SpacingValue>", required: false, description: "Spacing inside the frame", default: "undefined" },
        { name: "ratio", type: "ResponsiveValue<string>", required: false, description: "Aspect ratio (e.g., '16/9', '4/3', '1/1')", default: "'16/9'" },
        { name: "crop", type: "boolean", required: false, description: "Whether to crop overflowing content", default: "true" },
        { name: "background", type: "ResponsiveValue<string>", required: false, description: "Background color", default: "undefined" },
        { name: "as", type: "ElementType", required: false, description: "HTML element to render as", default: "'div'" },
        { name: "className", type: "string", required: false, description: "Additional CSS class name" },
        { name: "style", type: "CSSProperties", required: false, description: "Inline styles" },
        { name: "children", type: "ReactNode", required: true, description: "Child elements" }
  ],
  examples: [
    { title: "Basic Frame", description: "Simple aspect-ratio container" },
        { title: "Square Frame", description: "1:1 square aspect ratio" },
        { title: "Four Three", description: "4:3 aspect ratio for traditional media" },
        { title: "No Crop", description: "Frame without content cropping" },
        { title: "With Background", description: "Frame with custom background color" }
  ]
};
