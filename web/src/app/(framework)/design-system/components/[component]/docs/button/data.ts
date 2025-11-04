import { ComponentData } from '../types';

export const buttonData: ComponentData = {
  name: "Button",
  description: "Interactive elements for user actions with various styles and states",
  status: "stable",

  githubUrl: "/web/src/shared/components/ui/primitives/Button.tsx",
  props: [
    { name: "variant", type: "'contained' | 'outlined' | 'text'", required: false, description: "Button style variant", default: "'contained'" },
    { name: "color", type: "'primary' | 'secondary' | 'success' | 'error' | 'info' | 'warning'", required: false, description: "Button color theme", default: "'primary'" },
    { name: "size", type: "'small' | 'medium' | 'large'", required: false, description: "Button size", default: "'medium'" },
    { name: "disabled", type: "boolean", required: false, description: "Disable the button", default: "false" },
    { name: "loading", type: "boolean", required: false, description: "Show loading state with spinner", default: "false" },
    { name: "loadingText", type: "string", required: false, description: "Text to show when loading (optional)" },
    { name: "loadingIconSize", type: "number", required: false, description: "Size of the loading spinner", default: "20" },
    { name: "onClick", type: "(event: MouseEvent) => void", required: false, description: "Click handler function" },
    { name: "children", type: "ReactNode", required: true, description: "Button content" },
  ],
  examples: [
    { title: "Basic Usage", description: "Simple button with default styling" },
    { title: "Variants", description: "Different button styles (contained, outlined, text)" },
    { title: "Colors", description: "Various color themes" },
    { title: "Sizes", description: "Small, medium, and large button sizes" },
    { title: "Loading", description: "Button with loading state and spinner" },
  ]
};

