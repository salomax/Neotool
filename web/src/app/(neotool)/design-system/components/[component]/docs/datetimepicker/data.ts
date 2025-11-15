import { ComponentData } from '../types';

export const datetimepickerData: ComponentData = {
  name: "DateTimePicker",
  description: "Comprehensive date and time picker component with various customization options",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/primitives/DateTimePicker.tsx",

  props: [
    { name: "value", type: "Dayjs | null", required: false, description: "Current value of the date time picker" },
        { name: "defaultValue", type: "Dayjs | null", required: false, description: "Default value (uncontrolled)", default: "null" },
        { name: "disabled", type: "boolean", required: false, description: "Whether the picker is disabled", default: "false" },
        { name: "readOnly", type: "boolean", required: false, description: "Whether the picker is read-only", default: "false" },
        { name: "required", type: "boolean", required: false, description: "Whether the picker is required", default: "false" },
        { name: "label", type: "string", required: false, description: "Label for the picker" },
        { name: "placeholder", type: "string", required: false, description: "Placeholder text", default: "'Select date and time'" },
        { name: "helperText", type: "string", required: false, description: "Helper text below the picker" },
        { name: "error", type: "boolean", required: false, description: "Error state", default: "false" },
        { name: "errorMessage", type: "string", required: false, description: "Error message" },
        { name: "size", type: "'small' | 'medium'", required: false, description: "Size of the picker", default: "'medium'" },
        { name: "variant", type: "'standard' | 'outlined' | 'filled'", required: false, description: "Variant of the input", default: "'outlined'" },
        { name: "showTime", type: "boolean", required: false, description: "Whether to show time picker", default: "true" },
        { name: "showDate", type: "boolean", required: false, description: "Whether to show date picker", default: "true" },
        { name: "format", type: "string", required: false, description: "Format for display" },
        { name: "minDateTime", type: "Dayjs | null", required: false, description: "Minimum selectable date" },
        { name: "maxDateTime", type: "Dayjs | null", required: false, description: "Maximum selectable date" },
        { name: "disablePast", type: "boolean", required: false, description: "Whether to disable past dates", default: "false" },
        { name: "disableFuture", type: "boolean", required: false, description: "Whether to disable future dates", default: "false" },
        { name: "showSeconds", type: "boolean", required: false, description: "Whether to show seconds in time picker", default: "false" },
        { name: "use24HourFormat", type: "boolean", required: false, description: "Whether to use 24-hour format", default: "true" },
        { name: "showCalendarIcon", type: "boolean", required: false, description: "Whether to show calendar icon", default: "true" },
        { name: "showClockIcon", type: "boolean", required: false, description: "Whether to show clock icon", default: "true" },
        { name: "calendarIcon", type: "React.ReactNode", required: false, description: "Custom calendar icon" },
        { name: "clockIcon", type: "React.ReactNode", required: false, description: "Custom clock icon" },
        { name: "onChange", type: "(value: Dayjs | null) => void", required: false, description: "Callback fired when the value changes" },
        { name: "onOpen", type: "() => void", required: false, description: "Callback fired when the picker opens" },
        { name: "onClose", type: "() => void", required: false, description: "Callback fired when the picker closes" },
        { name: "className", type: "string", required: false, description: "Custom CSS class name" },
        { name: "data-testid", type: "string", required: false, description: "Test identifier for testing" }
  ],
  examples: [
    { title: "Basic DateTimePicker", description: "Simple date and time picker with default settings" },
        { title: "Date Only", description: "Date picker without time selection" },
        { title: "Time Only", description: "Time picker without date selection" },
        { title: "With Seconds", description: "DateTime picker including seconds" },
        { title: "12-Hour Format", description: "DateTime picker with 12-hour format and AM/PM" },
        { title: "With Constraints", description: "DateTime picker with date range constraints" }
  ]
};
