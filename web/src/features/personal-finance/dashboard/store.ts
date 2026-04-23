import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Layout } from 'react-grid-layout';
import { WidgetInstance, DashboardState } from './types';
import { v4 as uuidv4 } from 'uuid';

const NET_WORTH_WIDGET_ID = 'net-worth-1';
const CATEGORY_SUMMARY_WIDGET_ID = 'category-summary-1';
const LEGACY_CATEGORY_DONUT_WIDGET_ID = 'category-donut-1';

const DEFAULT_WIDGETS: WidgetInstance[] = [
  { id: 'month-summary-kpi-1', type: 'month-summary-kpi', isVisible: true },
  { id: 'cash-flow-1', type: 'cash-flow', isVisible: true },
  { id: NET_WORTH_WIDGET_ID, type: 'net-worth', isVisible: true },
  { id: CATEGORY_SUMMARY_WIDGET_ID, type: 'category-summary', isVisible: true },
];

const DEFAULT_LAYOUTS: { [key: string]: Layout } = {
  lg: [
    { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 8, h: 4 },
    { i: 'month-summary-kpi-1', x: 8, y: 0, w: 4, h: 4 },
    { i: 'cash-flow-1', x: 0, y: 4, w: 8, h: 4 },
    { i: CATEGORY_SUMMARY_WIDGET_ID, x: 8, y: 4, w: 4, h: 4 },
  ],
  md: [
    { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 8, h: 4 },
    { i: 'month-summary-kpi-1', x: 8, y: 0, w: 4, h: 4 },
    { i: 'cash-flow-1', x: 0, y: 4, w: 8, h: 4 },
    { i: CATEGORY_SUMMARY_WIDGET_ID, x: 8, y: 4, w: 4, h: 4 },
  ],
  sm: [
    { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 8, h: 4 },
    { i: 'month-summary-kpi-1', x: 8, y: 0, w: 4, h: 4 },
    { i: 'cash-flow-1', x: 0, y: 4, w: 8, h: 4 },
    { i: CATEGORY_SUMMARY_WIDGET_ID, x: 8, y: 4, w: 4, h: 4 },
  ],
  xs: [
    { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 8, h: 4 },
    { i: 'month-summary-kpi-1', x: 8, y: 0, w: 4, h: 4 },
    { i: 'cash-flow-1', x: 0, y: 4, w: 8, h: 4 },
    { i: CATEGORY_SUMMARY_WIDGET_ID, x: 8, y: 4, w: 4, h: 4 },
  ],
  xxs: [
    { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 8, h: 4 },
    { i: 'month-summary-kpi-1', x: 8, y: 0, w: 4, h: 4 },
    { i: 'cash-flow-1', x: 0, y: 4, w: 8, h: 4 },
    { i: CATEGORY_SUMMARY_WIDGET_ID, x: 8, y: 4, w: 4, h: 4 },
  ],
};

const LEGACY_WIDGET_TYPES = ['transaction-history', 'spending-breakdown'];
const SUPPORTED_WIDGET_TYPES = ['month-summary-kpi', 'cash-flow', 'net-worth', 'category-summary'];
const BREAKPOINT_KEYS = ['lg', 'md', 'sm', 'xs', 'xxs'] as const;

function migrateCategorySummary(widgets: WidgetInstance[] | undefined, layouts: any) {
  if (!Array.isArray(widgets)) return { widgets, layouts, changed: false };

  let changed = false;
  const nextWidgets = widgets.map((widget) => {
    if (widget.type !== 'category-donut') return widget;
    changed = true;
    const nextId =
      widget.id === LEGACY_CATEGORY_DONUT_WIDGET_ID ? CATEGORY_SUMMARY_WIDGET_ID : widget.id;
    return {
      ...widget,
      id: nextId,
      type: 'category-summary',
    };
  });

  const nextLayouts = { ...(layouts ?? {}) };
  for (const breakpoint of BREAKPOINT_KEYS) {
    const current = Array.isArray(nextLayouts[breakpoint]) ? nextLayouts[breakpoint] : [];
    const migrated = current.map((item: any) => {
      if (item?.i !== LEGACY_CATEGORY_DONUT_WIDGET_ID) return item;
      changed = true;
      return { ...item, i: CATEGORY_SUMMARY_WIDGET_ID };
    });
    nextLayouts[breakpoint] = migrated;
  }

  return { widgets: nextWidgets, layouts: nextLayouts, changed };
}

function getStateRoot(persistedState: any) {
  if (persistedState && typeof persistedState === 'object' && persistedState.state) {
    return persistedState.state;
  }
  return persistedState;
}

function withStateRoot(persistedState: any, nextStateRoot: any) {
  if (persistedState && typeof persistedState === 'object' && persistedState.state) {
    return { ...persistedState, state: nextStateRoot };
  }
  return nextStateRoot;
}

function ensureNetWorthLayouts(layouts: any) {
  const source = layouts && typeof layouts === 'object' ? layouts : {};
  let changed = false;
  const nextLayouts = { ...source };

  for (const breakpoint of BREAKPOINT_KEYS) {
    const existing = Array.isArray(source[breakpoint]) ? source[breakpoint] : [];
    const hasNetWorth = existing.some(
      (item: any) => item?.i === NET_WORTH_WIDGET_ID || item?.i?.startsWith('net-worth-')
    );

    if (hasNetWorth) {
      nextLayouts[breakpoint] = existing;
      continue;
    }

    const fallbackDefault = (DEFAULT_LAYOUTS[breakpoint] as any[]).find(
      (item: any) => item.i === NET_WORTH_WIDGET_ID
    ) ?? { i: NET_WORTH_WIDGET_ID, x: 0, y: 0, w: 12, h: 4 };

    const nextY = existing.reduce((maxY: number, item: any) => {
      const itemBottom = (item?.y ?? 0) + (item?.h ?? 0);
      return Math.max(maxY, itemBottom);
    }, 0);

    nextLayouts[breakpoint] = [
      ...existing,
      {
        ...fallbackDefault,
        y: Math.max(fallbackDefault.y ?? 0, nextY),
      },
    ];
    changed = true;
  }

  return changed ? nextLayouts : layouts;
}

function migratePersistedState(persistedState: any) {
  let root = getStateRoot(persistedState);
  const migratedCategorySummary = migrateCategorySummary(root?.widgets, root?.layouts);
  if (migratedCategorySummary.changed) {
    root = {
      ...root,
      widgets: migratedCategorySummary.widgets,
      layouts: migratedCategorySummary.layouts,
    };
  }

  const option = root?.dateRange?.option;
  const legacyDateOptions = ['7d', '30d', '90d', 'ytd'];
  if (legacyDateOptions.includes(option)) {
    root = {
      ...root,
      dateRange: { ...root.dateRange, option: 'currentMonth' },
    };
  }

  const widgets = root?.widgets as WidgetInstance[] | undefined;
  const hasInvalidWidgets =
    Array.isArray(widgets) &&
    widgets.some(
      (w) =>
        LEGACY_WIDGET_TYPES.includes(w?.type) ||
        !SUPPORTED_WIDGET_TYPES.includes(w?.type)
    );

  if (hasInvalidWidgets) {
    root = {
      ...root,
      widgets: DEFAULT_WIDGETS,
      layouts: DEFAULT_LAYOUTS,
    };
  }

  if (root?.dateRange?.option == null) {
    root = {
      ...root,
      dateRange: {
        option: 'currentMonth',
        start: null,
        end: null,
      },
    };
  }

  const migratedWidgets = root?.widgets as WidgetInstance[] | undefined;
  const hasNetWorth =
    Array.isArray(migratedWidgets) && migratedWidgets.some((widget) => widget.type === 'net-worth');
  if (Array.isArray(migratedWidgets) && !hasNetWorth) {
    root = {
      ...root,
      widgets: [...migratedWidgets, { id: NET_WORTH_WIDGET_ID, type: 'net-worth', isVisible: true }],
    };
  }

  const nextLayouts = ensureNetWorthLayouts(root?.layouts);
  if (nextLayouts !== root?.layouts) {
    root = {
      ...root,
      layouts: nextLayouts,
    };
  }

  return withStateRoot(persistedState, root);
}

function normalizeStateAtRuntime(state: DashboardState) {
  const supportedWidgets = state.widgets.filter((w) => SUPPORTED_WIDGET_TYPES.includes(w.type));
  if (supportedWidgets.length === state.widgets.length) {
    return state;
  }
  return {
    ...state,
    widgets: DEFAULT_WIDGETS,
    layouts: DEFAULT_LAYOUTS,
    dateRange: {
      option: state.dateRange?.option ?? 'currentMonth',
      start: state.dateRange?.start ?? null,
      end: state.dateRange?.end ?? null,
    },
  };
}

export const useDashboardStore = create<DashboardState>()(
  persist(
    (set, get) => ({
      widgets: DEFAULT_WIDGETS,
      layouts: DEFAULT_LAYOUTS,
      isEditMode: false,
      dateRange: {
        option: 'currentMonth',
        start: null,
        end: null,
      },
      setEditMode: (isEditMode) => set({ isEditMode }),
      setDateRange: (option, start = null, end = null) =>
        set({ dateRange: { option, start, end } }),
      addWidget: (type) => {
        const id = `${type}-${uuidv4()}`;
        const newWidget: WidgetInstance = { id, type, isVisible: true };

        const newLayoutItem = { i: id, x: 0, y: 0, w: 4, h: 2 };

        set((state) => ({
          widgets: [...state.widgets, newWidget],
          layouts: Object.keys(state.layouts).reduce<{ [key: string]: Layout }>((acc, key) => {
            const current = state.layouts[key];
            return { ...acc, [key]: current ? [...current, newLayoutItem] : [newLayoutItem] };
          }, {}),
        }));
      },
      removeWidget: (id) =>
        set((state) => ({
          widgets: state.widgets.filter((w) => w.id !== id),
          layouts: Object.keys(state.layouts).reduce<{ [key: string]: Layout }>((acc, key) => {
            const current = state.layouts[key];
            return { ...acc, [key]: current ? current.filter((l) => l.i !== id) : [] };
          }, {}),
        })),
      toggleEditMode: () => set((state) => ({ isEditMode: !state.isEditMode })),
      updateLayouts: (layouts) =>
        set((state) => ({
          layouts: { ...state.layouts, ...layouts },
        })),
      updateWidgetConfig: (id, config) =>
        set((state) => ({
          widgets: state.widgets.map((w) =>
            w.id === id ? { ...w, config: { ...w.config, ...config } } : w
          ),
        })),
      toggleWidgetVisibility: (id) =>
        set((state) => ({
          widgets: state.widgets.map((w) =>
            w.id === id ? { ...w, isVisible: !w.isVisible } : w
          ),
        })),
      resetLayout: () =>
        set({
          widgets: DEFAULT_WIDGETS,
          layouts: DEFAULT_LAYOUTS,
        }),
    }),
    {
      name: 'dashboard-storage',
      version: 4,
      migrate: (persistedState: any) => migratePersistedState(persistedState),
      onRehydrateStorage: () => (state) => {
        if (!state) return;
        const normalized = normalizeStateAtRuntime(state);
        if (normalized !== state) {
          state.resetLayout();
        }
      },
    }
  )
);
