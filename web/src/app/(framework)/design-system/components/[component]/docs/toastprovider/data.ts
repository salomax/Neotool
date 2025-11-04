import { ComponentData } from '../types';

export const toastproviderData: ComponentData = {
  name: "ToastProvider",
  description: "Provides context for displaying toast notifications",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/ToastProvider.tsx",

  props: [
    { name: "children", type: "ReactNode", required: true, description: "Child components" },
        { name: "position", type: "'top-right' | 'top-left' | 'bottom-right' | 'bottom-left'", required: false, description: "Toast position", default: "'top-right'" },
        { name: "maxToasts", type: "number", required: false, description: "Maximum number of toasts", default: "5" },
  ],
  examples: [
    { title: "Basic Provider", description: "Simple toast provider setup" },
        { title: "With Position", description: "Toast provider with custom position" },
        { title: "Multiple Toasts", description: "Provider handling multiple toasts" },
  ]
};
