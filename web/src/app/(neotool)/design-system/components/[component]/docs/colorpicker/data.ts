import { ComponentData } from '../types';

export const colorpickerData: ComponentData = {
  name: "ColorPicker",
  description: "Comprehensive color picker with preset colors and multiple format support",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/ColorPicker.tsx",

  props: [
    { name: "value", type: "string", required: false, description: "Selected color value", default: "'#000000'" },
        { name: "onChange", type: "(color: string) => void", required: false, description: "Callback fired when color changes" },
        { name: "variant", type: "'standard' | 'outlined' | 'filled'", required: false, description: "Visual variant of the button", default: "'standard'" },
        { name: "size", type: "'small' | 'medium' | 'large'", required: false, description: "Size of the component", default: "'medium'" },
        { name: "showPresets", type: "boolean", required: false, description: "Whether to show preset colors", default: "true" },
        { name: "showCustomInput", type: "boolean", required: false, description: "Whether to show custom color input", default: "true" },
        { name: "showHexInput", type: "boolean", required: false, description: "Whether to show hex input format", default: "true" },
        { name: "showRgbInput", type: "boolean", required: false, description: "Whether to show RGB input format", default: "false" },
        { name: "showHslInput", type: "boolean", required: false, description: "Whether to show HSL input format", default: "false" },
        { name: "presets", type: "string[]", required: false, description: "Array of preset colors" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the component is disabled", default: "false" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the component is read-only", default: "false" },
        { name: "placeholder", type: "string", required: false, description: "Placeholder text", default: "'Select a color'" },
        { name: "label", type: "string", required: false, description: "Label for the color picker" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the component" },
        { name: "error", type: "boolean", required: false, description: "Whether to show error state", default: "false" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic Color Picker", description: "Simple color picker with presets" },
        { title: "With Custom Input", description: "Color picker with custom input field" },
        { title: "All Formats", description: "Color picker supporting Hex, RGB, and HSL" },
        { title: "Different Variants", description: "Standard, outlined, and filled variants" }
  ]
};
