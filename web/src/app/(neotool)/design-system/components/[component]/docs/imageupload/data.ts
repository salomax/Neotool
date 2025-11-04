import { ComponentData } from '../types';

export const imageuploadData: ComponentData = {
  name: "ImageUpload",
  description: "Comprehensive image upload component with drag & drop, preview, and validation features",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/ImageUpload.tsx",

  props: [
    { name: "value", type: "File[]", required: false, description: "Current uploaded files" },
        { name: "defaultValue", type: "File[]", required: false, description: "Default files (uncontrolled)", default: "[]" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the upload is disabled", default: "false" },
        { name: "required", type: "boolean", required: false, description: "Whether the upload is required", default: "false" },
        { name: "label", type: "string", required: false, description: "Label for the upload" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the upload" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "errorMessage", type: "string", required: false, description: "Error message" },
        { name: "maxFiles", type: "number", required: false, description: "Maximum number of files allowed", default: "5" },
        { name: "maxFileSize", type: "number", required: false, description: "Maximum file size in bytes", default: "5 * 1024 * 1024" },
        { name: "accept", type: "string", required: false, description: "Accepted file types", default: "'image/*'" },
        { name: "multiple", type: "boolean", required: false, description: "Whether to allow multiple files", default: "true" },
        { name: "showDragDrop", type: "boolean", required: false, description: "Whether to show drag and drop area", default: "true" },
        { name: "showPreview", type: "boolean", required: false, description: "Whether to show file previews", default: "true" },
        { name: "showFileList", type: "boolean", required: false, description: "Whether to show file list", default: "true" },
        { name: "showProgress", type: "boolean", required: false, description: "Whether to show upload progress", default: "false" },
        { name: "uploadText", type: "string", required: false, description: "Custom upload text", default: "'Upload Images'" },
        { name: "dragText", type: "string", required: false, description: "Custom drag text", default: "'Drag and drop images here'" },
        { name: "dropText", type: "string", required: false, description: "Custom drop text", default: "'Drop images here'" },
        { name: "previewSize", type: "number", required: false, description: "Preview image size", default: "100" },
        { name: "compressImages", type: "boolean", required: false, description: "Whether to compress images", default: "false" },
        { name: "imageQuality", type: "number", required: false, description: "Image quality for compression (0-1)", default: "0.8" },
        { name: "maxImageWidth", type: "number", required: false, description: "Maximum image width for compression", default: "1920" },
        { name: "maxImageHeight", type: "number", required: false, description: "Maximum image height for compression", default: "1080" },
        { name: "onChange", type: "(files: File[]) => void", required: false, description: "Callback fired when files change" },
        { name: "onAdd", type: "(files: File[]) => void", required: false, description: "Callback fired when files are added" },
        { name: "onRemove", type: "(file: File, index: number) => void", required: false, description: "Callback fired when files are removed" },
        { name: "onUploadStart", type: "(files: File[]) => void", required: false, description: "Callback fired when upload starts" },
        { name: "onUploadComplete", type: "(files: File[]) => void", required: false, description: "Callback fired when upload completes" },
        { name: "onUploadError", type: "(error: Error) => void", required: false, description: "Callback fired when upload fails" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic ImageUpload", description: "Simple image upload with drag & drop" },
        { title: "With Preview", description: "Image upload with preview thumbnails" },
        { title: "Single File", description: "Upload component for single image only" },
        { title: "With Constraints", description: "Upload with file size and type constraints" },
        { title: "With Compression", description: "Upload with automatic image compression" },
        { title: "Custom Text", description: "Upload with custom text and labels" }
  ]
};
