import { ComponentData } from '../types';

export const chartData: ComponentData = {
  name: "Chart",
  description: "Data visualization component with multiple chart types (line, bar, pie, area)",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/Chart.tsx",
  
  props: [
    { name: "type", type: "'line' | 'bar' | 'pie' | 'area'", required: true, description: "Chart type" },
        { name: "data", type: "ChartData[]", required: true, description: "Chart data array" },
        { name: "title", type: "string", required: false, description: "Chart title" },
        { name: "width", type: "number | string", required: false, description: "Chart width", default: "'100%'" },
        { name: "height", type: "number | string", required: false, description: "Chart height", default: "300" },
        { name: "showLegend", type: "boolean", required: false, description: "Show legend", default: "true" },
        { name: "showTooltip", type: "boolean", required: false, description: "Show tooltip", default: "true" },
        { name: "showGrid", type: "boolean", required: false, description: "Show grid", default: "true" },
        { name: "colors", type: "string[]", required: false, description: "Custom colors array" },
        { name: "xAxisKey", type: "string", required: false, description: "X-axis data key", default: "'name'" },
        { name: "yAxisKey", type: "string", required: false, description: "Y-axis data key", default: "'value'" },
        { name: "dataKey", type: "string", required: false, description: "Data key for chart", default: "'value'" },
        { name: "nameKey", type: "string", required: false, description: "Name key for pie chart", default: "'name'" },
        { name: "valueKey", type: "string", required: false, description: "Value key for pie chart", default: "'value'" },
        { name: "strokeWidth", type: "number", required: false, description: "Line/area stroke width", default: "2" },
        { name: "fillOpacity", type: "number", required: false, description: "Area fill opacity", default: "0.6" },
        { name: "animationDuration", type: "number", required: false, description: "Animation duration in ms", default: "800" },
        { name: "margin", type: "object", required: false, description: "Chart margins" },
        { name: "sx", type: "object", required: false, description: "Custom styles" },
  ],
  examples: [
    { title: "Line Chart", description: "Time series data visualization" },
        { title: "Bar Chart", description: "Categorical data comparison" },
        { title: "Pie Chart", description: "Proportional data representation" },
        { title: "Area Chart", description: "Cumulative data over time" },
        { title: "Custom Colors", description: "Charts with custom color schemes" },
        { title: "Responsive Charts", description: "Charts that adapt to container size" },
  ]
};
