import { ComponentData } from '../types';

export const passwordfieldData: ComponentData = {
  name: "PasswordField",
  description: "Input field for passwords with visibility toggle",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/PasswordField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Field label text" },
        { name: "value", type: "string", required: false, description: "Password value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "showPassword", type: "boolean", required: false, description: "Show password text", default: "false" },
        { name: "onToggleVisibility", type: "() => void", required: false, description: "Toggle visibility handler" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the field", default: "false" },
  ],
  examples: [
    { title: "Basic Password", description: "Simple password input with toggle" },
        { title: "With Validation", description: "Password field with strength indicator" },
        { title: "Confirm Password", description: "Password confirmation field" },
  ]
};
