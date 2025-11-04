import { ComponentData } from '../types';

export const checkboxfieldData: ComponentData = {
  name: "CheckboxField",
  description: "Boolean input with checkbox styling and form integration",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/CheckboxField.tsx",
  type: "mui-wrapper" as const,
  muiDocsUrl: "https://mui.com/material-ui/react-checkbox/",
  props: [
    { name: "label", type: "string", required: false, description: "Checkbox label text" },
        { name: "checked", type: "boolean", required: false, description: "Checked state", default: "false" },
        { name: "onChange", type: "(event: ChangeEvent) => void", required: false, description: "Change handler" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the checkbox", default: "false" },
        { name: "indeterminate", type: "boolean", required: false, description: "Indeterminate state", default: "false" },
  ],
  examples: [
    { title: "Basic Checkbox", description: "Simple checkbox with label" },
        { title: "States", description: "Different checkbox states" },
        { title: "Indeterminate", description: "Indeterminate checkbox state" },
  ]
};
