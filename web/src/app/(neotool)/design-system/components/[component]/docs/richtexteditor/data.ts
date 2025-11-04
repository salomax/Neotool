import { ComponentData } from '../types';

export const richtexteditorData: ComponentData = {
  name: "RichTextEditor",
  description: "Rich text editor with comprehensive formatting options and toolbar",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/RichTextEditor.tsx",

  props: [
    { name: "value", type: "string", required: false, description: "HTML content of the editor" },
        { name: "onChange", type: "(value: string) => void", required: false, description: "Callback fired when content changes" },
        { name: "placeholder", type: "string", required: false, description: "Placeholder text when editor is empty", default: "'Start typing...'" },
        { name: "minHeight", type: "number", required: false, description: "Minimum height of the editor", default: "200" },
        { name: "maxHeight", type: "number", required: false, description: "Maximum height of the editor", default: "400" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the editor is read-only", default: "false" },
        { name: "showToolbar", type: "boolean", required: false, description: "Whether to show the toolbar", default: "true" },
        { name: "toolbarPosition", type: "'top' | 'bottom'", required: false, description: "Position of the toolbar", default: "'top'" },
        { name: "allowedFormats", type: "string[]", required: false, description: "Array of allowed formatting options" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic Editor", description: "Simple rich text editor with default toolbar" },
        { title: "Read Only", description: "Display formatted content without editing" },
        { title: "Custom Toolbar", description: "Editor with limited formatting options" },
        { title: "Bottom Toolbar", description: "Editor with toolbar positioned at bottom" }
  ]
};
