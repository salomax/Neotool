import { ComponentData } from '../types';

export const linkData: ComponentData = {
  name: "Link",
  description: "Navigational elements for directing users",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Link.tsx",

  props: [
    { name: "href", type: "string", required: true, description: "Link destination" },
        { name: "children", type: "ReactNode", required: true, description: "Link content" },
        { name: "target", type: "'_blank' | '_self' | '_parent' | '_top'", required: false, description: "Link target", default: "'_self'" },
        { name: "color", type: "'inherit' | 'primary' | 'secondary' | 'error' | 'info' | 'success' | 'warning'", required: false, description: "Link color", default: "'primary'" },
        { name: "underline", type: "'always' | 'hover' | 'none'", required: false, description: "Underline style", default: "'hover'" },
  ],
  examples: [
    { title: "Basic Link", description: "Simple text link" },
        { title: "External Link", description: "Link that opens in new tab" },
        { title: "Styled Link", description: "Link with custom styling" },
  ]
};
