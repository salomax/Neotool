import { ComponentData } from '../types';

export const datatableData: ComponentData = {
  name: "DataTable",
  description: "Advanced data table with sorting, pagination, and selection capabilities",
  status: "stable" as const,

  githubUrl: "/web/src/shared/components/ui/data-display/DataTable.tsx",

  props: [
    { name: "columns", type: "ColDef[]", required: true, description: "Column definitions for the table" },
        { name: "rows", type: "T[]", required: true, description: "Data rows to display" },
        { name: "height", type: "number | string", required: false, description: "Table height", default: "560" },
        { name: "loading", type: "boolean", required: false, description: "Loading state", default: "false" },
        { name: "error", type: "string", required: false, description: "Error message to display" },
        { name: "totalRows", type: "number", required: false, description: "Total number of rows for server-side pagination" },
        { name: "page", type: "number", required: false, description: "Current page number", default: "0" },
        { name: "pageSize", type: "number", required: false, description: "Number of rows per page", default: "25" },
        { name: "onPageChange", type: "(page: number, pageSize: number) => void", required: false, description: "Callback fired when page changes" },
        { name: "onRowClicked", type: "(row: T) => void", required: false, description: "Callback fired when a row is clicked" },
        { name: "sort", type: "string", required: false, description: "Current sort configuration" },
        { name: "onSortChange", type: "(sort: string) => void", required: false, description: "Callback fired when sort changes" },
        { name: "selectable", type: "boolean", required: false, description: "Whether rows can be selected", default: "false" },
        { name: "selectionMode", type: "'single' | 'multiple'", required: false, description: "Selection mode", default: "'multiple'" },
        { name: "selectedIds", type: "Array<string | number>", required: false, description: "Currently selected row IDs" },
        { name: "onSelectionChange", type: "(ids: Array<string | number>, rows: T[]) => void", required: false, description: "Callback fired when selection changes" },
        { name: "getRowId", type: "(row: T) => string | number", required: false, description: "Function to get unique row ID" },
        { name: "showToolbar", type: "boolean", required: false, description: "Whether to show the toolbar", default: "true" },
        { name: "enableDensity", type: "boolean", required: false, description: "Whether to enable density controls", default: "true" },
        { name: "enableExport", type: "boolean", required: false, description: "Whether to enable export functionality", default: "true" },
        { name: "enableColumnSelector", type: "boolean", required: false, description: "Whether to enable column selector", default: "false" },
        { name: "initialDensity", type: "'compact' | 'standard' | 'comfortable'", required: false, description: "Initial density setting", default: "'standard'" },
        { name: "gridProps", type: "any", required: false, description: "Additional props for the underlying grid" }
  ],
  examples: [
    { title: "Basic Table", description: "Simple data table with basic functionality. Shows employee data with `columns` and `rows` props." },
        { title: "With Selection", description: "Table with row selection capabilities. Use `selectable={true}` and `selectionMode` to enable multi-select." },
        { title: "With Sorting", description: "Table with sorting functionality. Click column headers to sort. Use `sort` prop for initial sort state." },
        { title: "With Pagination", description: "Table with server-side pagination. Use `totalRows`, `page`, and `pageSize` for pagination controls." },
        { title: "With Toolbar", description: "Table with toolbar features. Enable `showToolbar` and `enableExport` to show export controls." },
        { title: "Custom Columns", description: "Table with custom column configurations. Use `valueFormatter` and `cellRenderer` for custom cell rendering." }
  ]
};
