import { ComponentData } from '../types';

export const datepickerfieldData: ComponentData = {
  name: "DatePickerField",
  description: "Date selection input with calendar popup",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/forms/form/DatePickers.tsx",

  props: [
    { name: "label", type: "string", required: false, description: "Date picker label" },
        { name: "value", type: "Date | null", required: false, description: "Selected date" },
        { name: "onChange", type: "(date: Date | null) => void", required: false, description: "Change handler" },
        { name: "minDate", type: "Date", required: false, description: "Minimum selectable date" },
        { name: "maxDate", type: "Date", required: false, description: "Maximum selectable date" },
        { name: "disabled", type: "boolean", required: false, description: "Disable the picker", default: "false" },
  ],
  examples: [
    { title: "Basic Date Picker", description: "Simple date selection" },
        { title: "With Constraints", description: "Date picker with min/max dates" },
        { title: "Different Formats", description: "Various date formats and locales" },
  ]
};
