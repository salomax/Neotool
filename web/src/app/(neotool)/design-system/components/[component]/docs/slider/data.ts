import { ComponentData } from '../types';

export const sliderData: ComponentData = {
  name: "Slider",
  description: "Versatile slider component with single value, range, and various customization options",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/Slider.tsx",

  props: [
    { name: "value", type: "number | number[]", required: false, description: "Current value(s) of the slider", default: "0" },
        { name: "min", type: "number", required: false, description: "Minimum value of the slider", default: "0" },
        { name: "max", type: "number", required: false, description: "Maximum value of the slider", default: "100" },
        { name: "step", type: "number", required: false, description: "Step size for the slider", default: "1" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the slider is disabled", default: "false" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the slider is read-only", default: "false" },
        { name: "showValue", type: "boolean", required: false, description: "Whether to show value labels", default: "true" },
        { name: "showChips", type: "boolean", required: false, description: "Whether to show value chips", default: "false" },
        { name: "showMinMax", type: "boolean", required: false, description: "Whether to show min/max labels", default: "false" },
        { name: "showMarks", type: "boolean", required: false, description: "Whether to show step marks", default: "false" },
        { name: "marks", type: "Array<{value: number, label: string}>", required: false, description: "Custom marks for the slider" },
        { name: "orientation", type: "'horizontal' | 'vertical'", required: false, description: "Orientation of the slider", default: "'horizontal'" },
        { name: "size", type: "'small' | 'medium'", required: false, description: "Size of the slider", default: "'medium'" },
        { name: "color", type: "'primary' | 'secondary' | 'error' | 'warning' | 'info' | 'success'", required: false, description: "Color theme of the slider", default: "'primary'" },
        { name: "range", type: "boolean", required: false, description: "Whether to use range mode (dual thumbs)", default: "false" },
        { name: "label", type: "string", required: false, description: "Label for the slider" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the slider" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "valueFormatter", type: "(value: number) => string", required: false, description: "Custom value formatter", default: "(val) => val.toString()" },
        { name: "onChange", type: "(value: number | number[]) => void", required: false, description: "Callback fired when the value changes" },
        { name: "onChangeCommitted", type: "(value: number | number[]) => void", required: false, description: "Callback fired when the value change is committed" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic Slider", description: "Simple slider with default settings" },
        { title: "With Value Display", description: "Slider showing current value" },
        { title: "Range Slider", description: "Dual-thumb range selection" },
        { title: "With Marks", description: "Slider with step marks and labels" },
        { title: "Vertical Slider", description: "Vertically oriented slider" },
        { title: "Custom Formatter", description: "Slider with custom value formatting" }
  ]
};
