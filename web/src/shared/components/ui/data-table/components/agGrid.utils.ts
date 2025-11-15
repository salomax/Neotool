// src/components/table/agGrid.utils.ts
// Utilities to standardize AG Grid column behavior (percent, reset-filter)

export type ColDefAny = Record<string, any>;

export function withResetFilter(col: ColDefAny): ColDefAny {
  const next = { ...col };
  const existing = next.getMainMenuItems;
  next.getMainMenuItems = (params: any) => {
    const items = (
      existing ? existing(params) : (params.defaultItems?.slice?.() ?? [])
    ) as any[];
    items.push({
      name: "Reset filter",
      action: () => {
        const colId = params.column.getColId?.();
        const model = params.api.getFilterModel?.() || {};
        if (colId && model[colId]) {
          delete model[colId];
          params.api.setFilterModel?.(model);
        }
      },
    });
    return items;
  };
  return next;
}

export function percentColDef(
  field: string,
  options: ColDefAny = {},
): ColDefAny {
  // Display 0.2 as "20%" and accept "20" or "20%" from the user -> store 0.2
  const base: ColDefAny = {
    headerName: options.headerName ?? "Percent",
    field,
    filter: "agNumberColumnFilter",
    valueFormatter: (p: any) =>
      p.value == null || p.value === ""
        ? ""
        : `${(Number(p.value) * 100).toFixed(options.decimals ?? 0)}%`,
    valueParser: (p: any) => {
      if (p.newValue == null || p.newValue === "") return null;
      const s = String(p.newValue).replace("%", "").replace(",", ".").trim();
      const n = Number(s);
      if (!Number.isFinite(n)) return null;
      return n > 1 ? n / 100 : n;
    },
    ...options,
  };
  return withResetFilter(base);
}
