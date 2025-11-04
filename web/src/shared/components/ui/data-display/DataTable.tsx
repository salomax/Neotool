"use client";

import "@/styles/themes/agGrid.overrides.css";
import "@/shared/components/ui/data-display/DataTable.ag.css";
import * as React from "react";
import { useTheme } from "@mui/material/styles";
import {
  Box,
  LinearProgress,
  Alert,
  Stack,
  Pagination,
  Typography,
  IconButton,
  ToggleButtonGroup,
  ToggleButton,
  Tooltip,
} from "@mui/material";
import DownloadIcon from "@mui/icons-material/Download";
import DensitySmallIcon from "@mui/icons-material/DensitySmall";
import DensityMediumIcon from "@mui/icons-material/DensityMedium";
import DensityLargeIcon from "@mui/icons-material/DensityLarge";

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

  tableId?: string;
  enableFilterBar?: boolean;

  percentFilterColumns?: string[];
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

function clone<T>(obj: T): T {
  // Deep clone that preserves functions
  if (obj === null || typeof obj !== 'object') return obj;
  if (obj instanceof Date) return new Date(obj.getTime()) as T;
  if (Array.isArray(obj)) return obj.map(item => clone(item)) as T;
  
  const cloned = {} as T;
  for (const key in obj) {
    if (obj.hasOwnProperty(key)) {
      const value = obj[key];
      if (typeof value === 'function') {
        // Preserve functions
        cloned[key] = value;
      } else {
        cloned[key] = clone(value);
      }
    }
  }
  return cloned;
}

function applyTextContainsFilter(defs: any[]): any[] {
  const deep = (arr: any[]): any[] =>
    arr.map((d: any) => {
      if (d?.children) return { ...d, children: deep(d.children) };
      const type = (d?.dataType || "").toLowerCase();
      const explicitFilter = d?.filter;
      const isNumeric = type === "number";
      const isBool = type === "boolean";
      const isDate = type === "date" || type === "dateString";
      if (!isNumeric && !isBool && !isDate && !explicitFilter) {
        const fp = { ...(d.filterParams ?? {}) };
        fp.filterOptions = ["contains"];
        fp.caseSensitive = false;
        fp.debounceMs = fp.debounceMs ?? 150;
        fp.buttons = ["reset", "apply"];
        fp.closeOnApply = true;
        return { ...d, filter: "agTextColumnFilter", filterParams: fp };
      }
      if (explicitFilter === "agTextColumnFilter") {
        const fp = { ...(d.filterParams ?? {}) };
        fp.filterOptions = ["contains"];
        fp.caseSensitive = false;
        fp.debounceMs = fp.debounceMs ?? 150;
        fp.buttons = ["reset", "apply"];
        fp.closeOnApply = true;
        return { ...d, filter: "agTextColumnFilter", filterParams: fp };
      }
      return d;
    });
  return deep(defs);
}

function applyPercentFilter(defs: any[], percentIds: Set<string>): any[] {
  const deep = (arr: any[]): any[] =>
    arr.map((d: any) => {
      if (d?.children) return { ...d, children: deep(d.children) };
      const id = d?.colId ?? d?.field ?? d?.headerName;
      if (percentIds.has(id)) {
        const fp = { ...(d.filterParams ?? {}) };
        fp.filterValueGetter = (p: any) => {
          const field = d.field;
          const val = field ? p.data?.[field] : p.value;
          return (typeof val === "number" ? val : 0) * 100;
        };
        fp.buttons = ["reset", "apply"];
        fp.closeOnApply = true;
        return {
          ...d,
          filter: "agNumberColumnFilter",
          dataType: "number",
          filterParams: fp,
        };
      }
      return d;
    });
  return deep(defs);
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
  tableId,
  enableFilterBar = false,
  percentFilterColumns = [],
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
  const colApiRef = React.useRef<GridApi<T> | null>(null);

  const [density, setDensity] = React.useState<Density>(initialDensity);
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
  const withTextFilters = React.useMemo(
    () => applyTextContainsFilter(clone(normalized)),
    [normalized],
  );
  const colDefs = React.useMemo(
    () => applyPercentFilter(withTextFilters, new Set(percentFilterColumns)),
    [withTextFilters, percentFilterColumns],
  );

  const [quick, setQuick] = React.useState("");
  React.useEffect(() => {
    apiRef.current?.setGridOption('quickFilterText', quick);
  }, [quick]);

  function loadViews(): any[] {
    if (!tableId) return [];
    try {
      return JSON.parse(localStorage.getItem(`dt:views:${tableId}`) || "[]");
    } catch {
      return [];
    }
  }
  function saveView(name: string) {
    if (!apiRef.current || !colApiRef.current || !tableId) return;
    const view = {
      name,
      timestamp: Date.now(),
      quick,
      sortModel: apiRef.current.getColumnState().filter(col => col.sort).map(col => ({ colId: col.colId, sort: col.sort })),
      colState: colApiRef.current.getColumnState(),
      filterModel: apiRef.current.getFilterModel(),
    };
    const key = `dt:views:${tableId}`;
    const next = [...loadViews().filter((v) => v.name != name), view];
    localStorage.setItem(key, JSON.stringify(next));
  }
  function applyView(v: any) {
    if (!apiRef.current || !colApiRef.current) return;
    setQuick(v.quick ?? "");
    colApiRef.current.applyColumnState({
      state: v.colState ?? [],
      applyOrder: true,
      defaultState: {} as any,
    });
    apiRef.current.setFilterModel(v.filterModel ?? null);
    if (v.sortModel && v.sortModel.length > 0) {
      apiRef.current.applyColumnState({
        state: v.sortModel.map((s: SortModelItem) => ({ colId: s.colId, sort: s.sort })),
        defaultState: { sort: null }
      });
    }
  }
  function clearFilters() {
    if (!apiRef.current) return;
    setQuick("");
    apiRef.current.setFilterModel(null);
    apiRef.current.applyColumnState({
      state: [],
      defaultState: { sort: null }
    });
  }

  const onGridReady = (params: { api: GridApi<T> }) => {
    apiRef.current = params.api;
    colApiRef.current = params.api;

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

  React.useEffect(() => {
    if (!apiRef.current) return;
    apiRef.current.setGridOption("rowHeight", rowHeights[density]);
    apiRef.current.setGridOption("headerHeight", headerHeights[density]);
    apiRef.current.resetRowHeights();
  }, [density]);

  React.useEffect(() => {
    if (!colApiRef.current) return;
    const state = parseSort(_sort).map((s) => ({
      colId: s.colId,
      sort: s.sort,
    }));
    colApiRef.current.applyColumnState({ defaultState: { sort: null }, state });
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
    const ids = nodes.map((n) => (n.data as any)?.id ?? n.id);
    const selectedRows = nodes.map((n) => n.data as T);
    props.onSelectionChange!(ids, selectedRows);
  };

  return (
    <Stack spacing={1} sx={{ width: "100%", height: "100%" }}>
      {showToolbar && (
        <Stack
          direction="row"
          spacing={1}
          alignItems="center"
          justifyContent="space-between"
        >
          <Stack direction="row" spacing={1} alignItems="center">
            {enableDensity && (
              <ToggleButtonGroup
                size="small"
                exclusive
                value={density}
                onChange={(_, v) => v && setDensity(v)}
                aria-label="density"
              >
                <ToggleButton value="compact" aria-label="compact">
                  <DensitySmallIcon fontSize="small" />
                </ToggleButton>
                <ToggleButton value="standard" aria-label="standard">
                  <DensityMediumIcon fontSize="small" />
                </ToggleButton>
                <ToggleButton value="comfortable" aria-label="comfortable">
                  <DensityLargeIcon fontSize="small" />
                </ToggleButton>
              </ToggleButtonGroup>
            )}
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center">
            {enableExport && (
              <Tooltip title="Export CSV">
                <IconButton onClick={() => apiRef.current?.exportDataAsCsv()}>
                  <DownloadIcon />
                </IconButton>
              </Tooltip>
            )}
          </Stack>
        </Stack>
      )}

      {enableFilterBar && (
        <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 1 }}>
          <input
            value={quick}
            onChange={(e) => setQuick(e.target.value)}
            placeholder="Search…"
            style={{
              padding: 8,
              border: "1px solid var(--mui-palette-divider)",
              borderRadius: 8,
              width: 260,
            }}
            aria-label="Quick search"
          />
          {tableId && (
            <>
              <button
                onClick={() => {
                  const name = prompt("Save view as:");
                  if (name) saveView(name);
                }}
              >
                Save view
              </button>
              <select
                onChange={(e) => {
                  const v = loadViews().find((x) => x.name === e.target.value);
                  if (v) applyView(v);
                }}
                defaultValue=""
              >
                <option value="" disabled>
                  Select view…
                </option>
                {loadViews().map((v) => (
                  <option key={v.name} value={v.name}>
                    {v.name}
                  </option>
                ))}
              </select>
              <button onClick={clearFilters}>Clear</button>
            </>
          )}
        </Stack>
      )}

      {loading && <LinearProgress />}
      {error && <Alert severity="error">{error}</Alert>}

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
            filter: true,
            filterParams: {
              buttons: ["reset", "apply"],
              closeOnApply: true,
              debounceMs: 150,
            } as any,
          }}
          overlayNoRowsTemplate={`<span class="ag-overlay-loading-center">No data</span>`}
          suppressPaginationPanel
          domLayout="normal"
          onGridReady={onGridReady}
          onRowClicked={(e: RowClickedEvent<T>) => e.data && _onRowClicked?.(e.data)}
          onSortChanged={() => {
            if (!_onSortChange || !apiRef.current) return;
            const model = apiRef.current.getColumnState().filter(col => col.sort).map(col => ({ colId: col.colId, sort: col.sort })) as SortModelItem[];
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
        <Pagination
          page={effectivePage + 1}
          count={pageCount}
          onChange={handlePageChange}
          color="primary"
          size="small"
        />
      </Stack>
    </Stack>
  );
}

export default DataTable;
