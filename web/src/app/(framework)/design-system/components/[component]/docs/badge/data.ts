import { ComponentData } from '../types';

export const badgeData: ComponentData = {
  name: "Badge",
  description: "Small status indicators and notifications",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Badge.tsx",

  props: [
    { name: "badgeContent", type: "ReactNode", required: true, description: "Content to display in badge" },
        { name: "children", type: "ReactNode", required: true, description: "Element to attach badge to" },
        { name: "color", type: "'default' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'", required: false, description: "Badge color", default: "'default'" },
        { name: "variant", type: "'standard' | 'dot'", required: false, description: "Badge style", default: "'standard'" },
        { name: "max", type: "number", required: false, description: "Maximum number to show", default: "99" },
  ],
  examples: [
    { title: "Notification Badge", description: "Badge showing notification count" },
        { title: "Status Indicator", description: "Dot badge for status indication" },
        { title: "Custom Content", description: "Badge with custom content" },
  ]
};
