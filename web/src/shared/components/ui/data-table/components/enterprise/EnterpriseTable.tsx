"use client";

import "@/styles/themes/agGrid.overrides.css";
import "@/shared/components/ui/data-display/DataTable.ag.css";

import * as React from "react";
import { Box, Button, Stack, TextField, Typography } from "@mui/material";

import { AgGridReact } from "ag-grid-react";
import type {
  ColDef,
  GridApi,
  GridReadyEvent,
  ICellEditorParams,
  ValueFormatterParams,
  Module,
  ColumnState,
} from "ag-grid-community";
import { ModuleRegistry, AllCommunityModule } from "ag-grid-community";

import { savePreset, loadPreset, clearPreset } from "./presets";

import "ag-grid-community/styles/ag-grid.css";
import "ag-grid-community/styles/ag-theme-quartz.css";

const modules: Module[] = [AllCommunityModule];
ModuleRegistry.registerModules(modules);

type Row = { id: number; name: string; percent: number | null; amount: number };

type PercentEditorRef = { getValue(): number | null };

const PercentEditor = React.forwardRef<
  PercentEditorRef,
  ICellEditorParams<Row, number | null>
>((props, ref) => {
  const startVal = React.useMemo(() => {
    const v = props.value ?? null; // ✅ value é number|null (sem any)
    return v == null ? "" : String(Number(v) * 100);
  }, [props.value]);

  const [val, setVal] = React.useState<string>(startVal);
  const inputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const parsePercentToUnit = React.useCallback((): number | null => {
    if (val === "") return null;
    const n = Number(String(val).replace(",", "."));
    if (!Number.isFinite(n)) return null;
    return n > 1 ? n / 100 : n; // 20 -> 0.2
  }, [val]);

  React.useImperativeHandle(ref, () => ({
    getValue: () => parsePercentToUnit(),
  }));

  return (
    <TextField
      inputRef={inputRef}
      size="small"
      type="number"
      inputMode="decimal"
      value={val}
      onChange={(e) => setVal(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === "Enter") props.stopEditing?.();
      }}
      sx={{ width: 100 }}
    />
  );
});
PercentEditor.displayName = "PercentEditor";

const PRESET_KEY = "neotool.enterprise.columns.v1";

export default function EnterpriseTable() {
  const [quickFilter, setQuickFilter] = React.useState("");
  const [rowData, setRowData] = React.useState<Row[]>([
    { id: 1, name: "Alpha", percent: 0.2, amount: 1500 },
    { id: 2, name: "Beta", percent: 0.05, amount: 320 },
    { id: 3, name: "Gamma", percent: 0.725, amount: 980 },
  ]);

  const gridApi = React.useRef<GridApi<Row> | null>(null);

  const columnDefs = React.useMemo<ColDef<Row>[]>(
    () => [
      {
        // ✅ coluna de seleção sem field (evita cast)
        headerName: "",
        width: 48,
        pinned: "left",
        sortable: false,
        filter: false,
      },
      {
        headerName: "ID",
        field: "id",
        filter: "agNumberColumnFilter",
        width: 100,
      },
      {
        headerName: "Name",
        field: "name",
        filter: "agTextColumnFilter",
        flex: 1,
        editable: true,
      },
      {
        headerName: "Percent",
        field: "percent",
        filter: "agNumberColumnFilter",
        editable: true,
        cellEditor: PercentEditor,
        valueFormatter: (p: ValueFormatterParams<Row, number | null>) =>
          p.value == null ? "" : `${(Number(p.value) * 100).toFixed(0)}%`,
      },
      {
        headerName: "Amount",
        field: "amount",
        filter: "agNumberColumnFilter",
        editable: true,
        valueFormatter: (p: ValueFormatterParams<Row, number | null>) => {
          const v = p.value == null ? null : Number(p.value);
          return v == null
            ? ""
            : v.toLocaleString(undefined, {
                style: "currency",
                currency: "USD",
              });
        },
      },
    ],
    [],
  );

  const defaultColDef = React.useMemo<ColDef<Row>>(
    () => ({ sortable: true, resizable: true }),
    [],
  );

  const onGridReady = React.useCallback((e: GridReadyEvent<Row>) => {
    gridApi.current = e.api;
    const state = loadPreset(PRESET_KEY) as ColumnState[] | null;
    if (state) e.api.applyColumnState({ state, applyOrder: true });
  }, []);

  const handleExportCsv = React.useCallback(
    () => gridApi.current?.exportDataAsCsv?.(),
    [],
  );

  const handleSavePreset = React.useCallback(() => {
    const s = gridApi.current?.getColumnState?.();
    if (s) savePreset(PRESET_KEY, s);
  }, []);

  const handleLoadPreset = React.useCallback(() => {
    const st = loadPreset(PRESET_KEY) as ColumnState[] | null;
    if (st)
      gridApi.current?.applyColumnState?.({ state: st, applyOrder: true });
  }, []);

  const handleResetPreset = React.useCallback(() => {
    clearPreset(PRESET_KEY);
    gridApi.current?.resetColumnState?.();
  }, []);

  const handleDeleteSelected = React.useCallback(() => {
    // ✅ evita IRowNode/RowNode — pega direto os dados tipados
    const selected: Row[] = gridApi.current?.getSelectedRows?.() ?? [];
    const ids = new Set<number>(selected.map((r) => r.id));
    setRowData((rows) => rows.filter((r) => !ids.has(r.id)));
  }, []);

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" sx={{ mb: 2 }} data-testid="enterprise-title">
        Enterprise Table
      </Typography>
      <Stack direction="row" spacing={1} sx={{ mb: 1 }} alignItems="center">
        <TextField
          size="small"
          placeholder="Quick filter…"
          value={quickFilter}
          onChange={(e) => setQuickFilter(e.target.value)}
          data-testid="enterprise-quick-filter"
          inputProps={{ "data-testid": "enterprise-quick-filter-input" }}
        />
        <Box sx={{ flexGrow: 1 }} />
        <Button
          variant="outlined"
          onClick={handleDeleteSelected}
          data-testid="enterprise-delete"
        >
          Delete Selected
        </Button>
        <Button
          variant="outlined"
          onClick={handleExportCsv}
          data-testid="enterprise-export"
        >
          Export CSV
        </Button>
        <Button variant="outlined" onClick={handleSavePreset}>
          Save Preset
        </Button>
        <Button variant="outlined" onClick={handleLoadPreset}>
          Load Preset
        </Button>
        <Button variant="text" onClick={handleResetPreset}>
          Reset
        </Button>
      </Stack>

      <div className="ag-theme-quartz" style={{ height: 480 }}>
        <AgGridReact<Row>
          theme="legacy"
          rowData={rowData}
          columnDefs={columnDefs}
          defaultColDef={defaultColDef}
          rowSelection={{
            mode: "multiRow",
            checkboxes: true,
            headerCheckbox: true,
            enableClickSelection: false,
          }}
          onGridReady={onGridReady}
          quickFilterText={quickFilter}
        />
      </div>
    </Box>
  );
}
