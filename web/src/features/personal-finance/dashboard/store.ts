import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { Layout } from 'react-grid-layout';
import { WidgetInstance, WidgetType, DashboardState } from './types';
import { v4 as uuidv4 } from 'uuid';

const DEFAULT_WIDGETS: WidgetInstance[] = [
  { id: 'net-worth-1', type: 'net-worth', isVisible: true },
  { id: 'transaction-history-1', type: 'transaction-history', isVisible: true },
  { id: 'spending-breakdown-1', type: 'spending-breakdown', isVisible: true },
];

const DEFAULT_LAYOUTS: { [key: string]: Layout } = {
  lg: [
    { i: 'net-worth-1', x: 0, y: 0, w: 4, h: 2 },
    { i: 'transaction-history-1', x: 4, y: 0, w: 8, h: 4 },
    { i: 'spending-breakdown-1', x: 0, y: 2, w: 6, h: 4 },
  ],
  md: [
    { i: 'net-worth-1', x: 0, y: 0, w: 4, h: 2 },
    { i: 'transaction-history-1', x: 4, y: 0, w: 8, h: 4 },
    { i: 'spending-breakdown-1', x: 0, y: 2, w: 6, h: 4 },
  ],
  sm: [
    { i: 'net-worth-1', x: 0, y: 0, w: 12, h: 2 },
    { i: 'transaction-history-1', x: 0, y: 2, w: 12, h: 4 },
    { i: 'spending-breakdown-1', x: 0, y: 6, w: 12, h: 4 },
  ],
  xs: [
    { i: 'net-worth-1', x: 0, y: 0, w: 12, h: 2 },
    { i: 'transaction-history-1', x: 0, y: 2, w: 12, h: 4 },
    { i: 'spending-breakdown-1', x: 0, y: 6, w: 12, h: 4 },
  ],
  xxs: [
    { i: 'net-worth-1', x: 0, y: 0, w: 12, h: 2 },
    { i: 'transaction-history-1', x: 0, y: 2, w: 12, h: 4 },
    { i: 'spending-breakdown-1', x: 0, y: 6, w: 12, h: 4 },
  ],
};

export const useDashboardStore = create<DashboardState>()(
  persist(
    (set, get) => ({
      widgets: DEFAULT_WIDGETS,
      layouts: DEFAULT_LAYOUTS,
      isEditMode: false,
      dateRange: {
        option: '90d',
        start: null,
        end: null,
      },
      setEditMode: (isEditMode) => set({ isEditMode }),
      setDateRange: (option, start = null, end = null) => 
        set({ dateRange: { option, start, end } }),
      addWidget: (type) => {
        const id = `${type}-${uuidv4()}`;
        const newWidget: WidgetInstance = { id, type, isVisible: true };
        
        // Add new widget to layouts at first available position (0,0 for now)
        // In a real app, we'd find the first empty spot
        const newLayoutItem = { i: id, x: 0, y: 0, w: 4, h: 2 };
        
        set((state) => ({
          widgets: [...state.widgets, newWidget],
          layouts: Object.keys(state.layouts).reduce<{ [key: string]: Layout }>((acc, key) => {
            const current = state.layouts[key];
            return { ...acc, [key]: current ? [...current, newLayoutItem] : [newLayoutItem] };
          }, {})
        }));
      },
      removeWidget: (id) =>
        set((state) => ({
          widgets: state.widgets.filter((w) => w.id !== id),
          layouts: Object.keys(state.layouts).reduce<{ [key: string]: Layout }>((acc, key) => {
            const current = state.layouts[key];
            return { ...acc, [key]: current ? current.filter((l) => l.i !== id) : [] };
          }, {})
        })),
      toggleEditMode: () => set((state) => ({ isEditMode: !state.isEditMode })),
      updateLayouts: (layouts) =>
        set((state) => ({
          layouts: { ...state.layouts, ...layouts },
        })),
      updateWidgetConfig: (id, config) =>
        set(state => ({
          widgets: state.widgets.map(w => 
            w.id === id ? { ...w, config: { ...w.config, ...config } } : w
          ),
        })),
      toggleWidgetVisibility: (id) =>
        set(state => ({
          widgets: state.widgets.map(w => 
            w.id === id ? { ...w, isVisible: !w.isVisible } : w
          ),
        })),
      resetLayout: () => set({ 
        widgets: DEFAULT_WIDGETS,
        layouts: DEFAULT_LAYOUTS 
      }),
    }),
    {
      name: 'dashboard-storage',
      version: 2,
      migrate: (persistedState: any) => {
        const option = persistedState?.state?.dateRange?.option;

        if (option === '7d' || option === '30d') {
          return {
            ...persistedState,
            state: {
              ...persistedState.state,
              dateRange: {
                ...persistedState.state.dateRange,
                option: '90d',
              },
            },
          };
        }

        return persistedState;
      },
    }
  )
);
