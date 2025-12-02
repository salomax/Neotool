import { ComponentData } from '../types';

export const drawerData: ComponentData = {
  name: "Drawer",
  description: "Sliding panel for navigation, sidebars, and overlays",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/layout/Drawer.tsx",

  props: [
    { name: "open", type: "boolean", required: true, description: "Controls drawer visibility" },
        { name: "onClose", type: "() => void", required: true, description: "Callback when drawer is closed" },
        { name: "title", type: "string", required: false, description: "Optional title displayed in drawer header" },
        { name: "showCloseButton", type: "boolean", required: false, description: "Show close button in header", default: "true" },
        { name: "showMenuButton", type: "boolean", required: false, description: "Show menu button in header", default: "false" },
        { name: "onMenuClick", type: "() => void", required: false, description: "Callback when menu button is clicked" },
        { name: "variant", type: "'temporary' | 'persistent' | 'permanent'", required: false, description: "Drawer variant type", default: "'temporary'" },
        { name: "anchor", type: "'left' | 'right' | 'top' | 'bottom'", required: false, description: "Drawer anchor position", default: "'left'" },
        { name: "width", type: "number | string", required: false, description: "Drawer width (for left/right anchors)", default: "280" },
        { name: "height", type: "number | string", required: false, description: "Drawer height (for top/bottom anchors)", default: "'100%'" },
        { name: "footer", type: "ReactNode", required: false, description: "Footer content (e.g., action buttons). Always visible at the bottom of the drawer." },
        { name: "children", type: "ReactNode", required: true, description: "Drawer content" },
  ],
  examples: [
    { title: "Basic Drawer", description: "Simple drawer with navigation content" },
        { title: "With Title", description: "Drawer with header title and close button" },
        { title: "Different Anchors", description: "Drawer positioned on different sides" },
        { title: "Persistent Drawer", description: "Drawer that stays open and pushes content" },
        { title: "Custom Styling", description: "Drawer with custom colors and styling" },
  ]
};
