import { ComponentData } from '../types';

export const fileuploaderData: ComponentData = {
  name: "FileUploader",
  description: "File upload component with drag and drop support",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/FileUploader.tsx",

  props: [
    { name: "onUpload", type: "(files: File[]) => void", required: true, description: "Upload handler" },
        { name: "accept", type: "string", required: false, description: "Accepted file types", default: "'*'" },
        { name: "multiple", type: "boolean", required: false, description: "Allow multiple files", default: "false" },
        { name: "maxSize", type: "number", required: false, description: "Maximum file size in bytes" },
        { name: "disabled", type: "boolean", required: false, description: "Disable upload", default: "false" },
  ],
  examples: [
    { title: "Basic Upload", description: "Simple file upload area" },
        { title: "Drag & Drop", description: "Drag and drop file upload" },
        { title: "Multiple Files", description: "Upload multiple files at once" },
  ]
};
