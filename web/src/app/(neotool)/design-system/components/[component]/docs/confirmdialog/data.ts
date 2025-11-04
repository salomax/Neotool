import { ComponentData } from '../types';

export const confirmdialogData: ComponentData = {
  name: "ConfirmDialog",
  description: "Modal dialog for user confirmation",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/ConfirmDialog.tsx",

  props: [
    { name: "open", type: "boolean", required: true, description: "Dialog visibility" },
        { name: "title", type: "string", required: true, description: "Dialog title" },
        { name: "message", type: "string", required: true, description: "Confirmation message" },
        { name: "onConfirm", type: "() => void", required: true, description: "Confirm handler" },
        { name: "onCancel", type: "() => void", required: true, description: "Cancel handler" },
        { name: "confirmText", type: "string", required: false, description: "Confirm button text", default: "'Confirm'" },
        { name: "cancelText", type: "string", required: false, description: "Cancel button text", default: "'Cancel'" },
  ],
  examples: [
    { title: "Basic Confirmation", description: "Simple confirmation dialog" },
        { title: "Delete Confirmation", description: "Confirmation for destructive actions" },
        { title: "Custom Buttons", description: "Dialog with custom button text" },
  ]
};
