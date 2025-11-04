import { ComponentData } from '../types';

export const radiogroupfieldData: ComponentData = {
  name: "RadioGroupField",
  description: "Group of radio buttons for single selection",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/RadioGroupField.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Group label text" },
        { name: "value", type: "string", required: false, description: "Selected value" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Change handler" },
        { name: "options", type: "Array<{value: string, label: string}>", required: true, description: "Radio options" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the group", default: "false" },
  ],
  examples: [
    { title: "Basic Radio Group", description: "Simple radio button group" },
        { title: "With Labels", description: "Radio group with descriptive labels" },
        { title: "Vertical Layout", description: "Radio buttons in vertical layout" },
  ]
};
