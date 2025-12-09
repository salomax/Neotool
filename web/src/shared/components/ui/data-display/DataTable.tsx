"use client";

import "@/styles/themes/agGrid.overrides.css";
import "@/shared/components/ui/data-display/DataTable.ag.css";
import * as React from "react";
import { useTheme } from "@mui/material/styles";
import {
  Box,
  LinearProgress,
  Stack,
  Pagination,
  Typography,
  IconButton,
  Tooltip,
} from "@mui/material";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import DownloadIcon from "@mui/icons-material/Download";

import { AgGridReact } from "ag-grid-react";
import type {
  ColDef,
  ColGroupDef,
  RowClickedEvent,
  Module,
  GridApi,
  SortModelItem,
  GetRowIdParams,
} from "ag-grid-community";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";

if (!(globalThis as any).__agGridAllModulesRegistered) {
  ModuleRegistry.registerModules([AllCommunityModule as unknown as Module]);
  (globalThis as any).__agGridAllModulesRegistered = true;
}

import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";
import "./DataTable.ag.css";

export type Density = "compact" | "standard" | "comfortable";

export type { ColDef, ColGroupDef } from "ag-grid-community";

export type DataTableProps<T extends { id?: string | number } = any> = {
  columns: (ColDef<T> | ColGroupDef<T>)[];
  rows: T[];
  height?: number | string;
  loading?: boolean;
  error?: string;
  totalRows?: number;
  page?: number;
  pageSize?: number;
  onPageChange?: (_page: number, _pageSize: number) => void;
  onRowClicked?: (_row: T) => void;
  sort?: string;
  onSortChange?: (_sort: string) => void;
  selectable?: boolean;
  selectionMode?: "single" | "multiple";
  selectedIds?: Array<string | number>;
  onSelectionChange?: (_ids: Array<string | number>, _rows: T[]) => void;
  getRowId?: (_row: T) => string | number;
  showToolbar?: boolean;
  enableDensity?: boolean;
  enableExport?: boolean;
  enableColumnSelector?: boolean;
  initialDensity?: Density;
  gridProps?: any;
};

function parseSort(sort?: string): SortModelItem[] {
  if (!sort) return [];
  return sort.split(",").map((p) => {
    const [colId, dir] = p.split(":");
    return { colId, sort: dir === "desc" ? "desc" : "asc" } as SortModelItem;
  });
}
function stringifySort(model: SortModelItem[] | undefined): string {
  if (!model || !model.length) return "";
  return model.map((m) => `${m.colId}:${m.sort}`).join(",");
}

function normalizeColDefs(defs: any[]): any[] {
  const used = new Set<string>();
  const makeId = (d: any, idx: number, path: string[]) => {
    const base =
      (typeof d.colId === "string" && d.colId) ||
      (typeof d.field === "string" && d.field) ||
      (typeof d.headerName === "string" &&
        d.headerName.replace(/\s+/g, "_").toLowerCase()) ||
      `col_${idx}`;
    let id = [...path, base].join("__");
    let i = 1;
    while (used.has(id)) id = `${[...path, base].join("__")}__${i++}`;
    used.add(id);
    return id;
  };
  const walk = (arr: any[], path: string[] = []): any[] =>
    arr.map((d, idx) => {
      if (d && Array.isArray(d.children)) {
        const groupKey = String(d.headerName ?? `g${idx}`);
        return { ...d, children: walk(d.children, [...path, groupKey]) };
      }
      const id = makeId(d || {}, idx, path);
      return { colId: id, ...d };
    });
  return walk(defs);
}


export function DataTable<T extends { id?: string | number }>(
  props: DataTableProps<T>,
) {
  const {
    columns,
    rows,
    height = "100%",
    loading = false,
    error,
    totalRows,
    page: _page = 0,
    pageSize: _pageSize = 25,
  onPageChange: _onPageChange,
  onRowClicked: _onRowClicked,
  sort: _sort,
  onSortChange: _onSortChange,
  selectable = false,
  selectionMode = "multiple",
  selectedIds: _selectedIds,
  onSelectionChange: _onSelectionChange,
  getRowId: _getRowId,
  showToolbar = true,
  enableDensity = true,
  enableExport = true,
  initialDensity = "standard",
  gridProps: _gridProps = {},
  } = props;

  const theme = useTheme();
  const isServer =
    typeof totalRows === "number" && typeof _onPageChange === "function";

  const [intPage, setIntPage] = React.useState(0);
  const effectivePage = isServer ? _page! : intPage;
  const clientTotal = rows.length;
  const effectiveTotal = isServer ? (totalRows as number) : clientTotal;
  const pSize = _pageSize ?? 25;
  const pageCount = Math.max(1, Math.ceil(effectiveTotal / pSize));

  const clientSlice = React.useMemo(() => {
    if (isServer) return rows;
    const start = effectivePage * pSize;
    return rows.slice(start, start + pSize);
  }, [rows, effectivePage, pSize, isServer]);

  const handlePageChange = (_: any, p: number) => {
    if (isServer) _onPageChange?.(p - 1, pSize);
    else setIntPage(p - 1);
  };

  const gridThemeClass =
    theme.palette.mode === "dark" ? "ag-theme-quartz-dark" : "ag-theme-quartz";
  const apiRef = React.useRef<GridApi<T> | null>(null);

  // Fixed density to "standard"
  const density: Density = "standard";
  const rowHeights = { compact: 28, standard: 36, comfortable: 44 } as const;
  const headerHeights = { compact: 32, standard: 40, comfortable: 48 } as const;

  const selectionCol: ColDef<T> | undefined = selectable
    ? {
        colId: "__select__",
        headerName: "",
        maxWidth: 48,
        pinned: "left",
      }
    : undefined;

  const normalized = React.useMemo(
    () =>
      normalizeColDefs(
        selectionCol ? [selectionCol, ...columns] : (columns as any[]),
      ),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [selectable, selectionCol],
  );
  const colDefs = React.useMemo(
    () => normalized,
    [normalized],
  );

  const onGridReady = (params: { api: GridApi<T> }) => {
    apiRef.current = params.api;

    if (_sort) {
      const state = parseSort(_sort).map((s) => ({
        colId: s.colId,
        sort: s.sort,
      }));
      params.api.applyColumnState({
        defaultState: { sort: null },
        state,
      });
    }
    params.api.setGridOption("rowHeight", rowHeights[density]);
    params.api.setGridOption("headerHeight", headerHeights[density]);
  };

  // Density is now fixed, so this effect is no longer needed
  // Row heights are set in onGridReady

  React.useEffect(() => {
    if (!apiRef.current) return;
    const state = parseSort(_sort).map((s) => ({
      colId: s.colId,
      sort: s.sort,
    }));
    apiRef.current.applyColumnState({ defaultState: { sort: null }, state });
  }, [_sort]);

  const getRowIdCb = React.useCallback(
    (p: GetRowIdParams<T>) => {
      const id = _getRowId?.(p.data) ?? (p.data as any)?.id;
      return String(id ?? Math.random().toString(36).substr(2, 9));
    },
    [_getRowId],
  );

  const handleSelectionChanged = () => {
    if (!apiRef.current || !props.onSelectionChange) return;
    const nodes = apiRef.current.getSelectedNodes();
    const ids = nodes.map((n: any) => (n.data as any)?.id ?? n.id);
    const selectedRows = nodes.map((n: any) => n.data as T);
    props.onSelectionChange!(ids, selectedRows);
  };

  return (
    <Stack spacing={1} sx={{ width: "100%", height: "100%" }}>
      {loading && <LinearProgress />}
      <ErrorAlert error={error} />

      <Box className={gridThemeClass} sx={{ height, width: "100%" }}>
        <AgGridReact<T>
          theme="legacy"
          rowData={clientSlice}
          columnDefs={colDefs as any}
          animateRows
          defaultColDef={{
            flex: 1,
            sortable: true,
            resizable: true,
            filter: false,
          }}
          overlayNoRowsTemplate={`<span class="ag-overlay-loading-center">No data</span>`}
          suppressPaginationPanel
          domLayout="normal"
          onGridReady={onGridReady}
          onRowClicked={(e: RowClickedEvent<T>) => e.data && _onRowClicked?.(e.data)}
          onSortChanged={() => {
            if (!_onSortChange || !apiRef.current) return;
            const model = apiRef.current.getColumnState().filter((col: any) => col.sort).map((col: any) => ({ colId: col.colId, sort: col.sort })) as SortModelItem[];
            _onSortChange(stringifySort(model));
          }}
          rowSelection={{
            mode: selectionMode === "single" ? "singleRow" : "multiRow",
            enableClickSelection: !selectable,
            checkboxes: selectable,
            headerCheckbox: selectable && selectionMode === "multiple",
          }}
          getRowId={getRowIdCb}
          onSelectionChanged={handleSelectionChanged}
          {...props.gridProps}
        />
      </Box>

      <Stack direction="row" justifyContent="space-between" alignItems="center">
        <Typography variant="body2">
          {effectiveTotal.toLocaleString()} rows
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          {enableExport && (
            <Tooltip title="Export CSV">
              <IconButton 
                onClick={() => apiRef.current?.exportDataAsCsv()}
                size="small"
              >
                <DownloadIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          <Pagination
            page={effectivePage + 1}
            count={pageCount}
            onChange={handlePageChange}
            color="primary"
            size="small"
          />
        </Stack>
      </Stack>
    </Stack>
  );
}

export default DataTable;
