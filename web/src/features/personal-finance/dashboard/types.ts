import { Layout } from 'react-grid-layout';
import type {
  ComponentType,
  CSSProperties,
  MouseEventHandler,
  ReactNode,
  TouchEventHandler,
} from 'react';

export type WidgetType = string;

export interface WidgetConfig {
  [key: string]: any;
}

export type WidgetSize = {
  xs?: number;
  sm?: number;
  md?: number;
  lg?: number;
  xl?: number;
};

export interface WidgetInstance {
  id: string;
  type: string;
  title?: string; // User override for title
  // Legacy layout (keep for backward compatibility if needed, or remove)
  // layout: {
  //   w: WidgetSize; 
  //   order: number;
  // };
  grid?: {
    w: WidgetSize; // Preferred width at different breakpoints
    h: WidgetSize; // Preferred height at different breakpoints (RGL uses grid units for height)
  };
  config?: WidgetConfig;
  isVisible: boolean;
}

export interface WidgetProps {
  widget: WidgetInstance;
  dragHandleProps?: any; // props for drag handle (className in RGL)
  className?: string;
  style?: CSSProperties;
  onMouseDown?: MouseEventHandler;
  onMouseUp?: MouseEventHandler;
  onTouchEnd?: TouchEventHandler;
}

export interface WidgetDefinition {
  type: string;
  defaultTitle?: string;
  defaultTitleKey?: string;
  description?: string;
  descriptionKey?: string;
  component: ComponentType<WidgetProps>;
  defaultSize: { w: WidgetSize, h: WidgetSize }; // w and h for different breakpoints
  defaultConfig?: WidgetConfig;
  icon?: ReactNode;
}

export type DateRangeOption = 'currentMonth' | '3m' | '6m' | '1y' | '3y' | 'all' | 'custom';

export interface DashboardState {
  widgets: WidgetInstance[];
  layouts: { [key: string]: Layout }; // Store RGL layouts per breakpoint (Layout is array of items)
  isEditMode: boolean;
  dateRange: {
    option: DateRangeOption;
    start: Date | null;
    end: Date | null;
  };
  
  // Actions
  setEditMode: (isEdit: boolean) => void;
  setDateRange: (option: DateRangeOption, start?: Date | null, end?: Date | null) => void;
  addWidget: (type: string) => void;
  removeWidget: (id: string) => void;
  updateLayouts: (layouts: { [key: string]: Layout }) => void;
  updateWidgetConfig: (id: string, config: WidgetConfig) => void;
  toggleWidgetVisibility: (id: string) => void;
  resetLayout: () => void;
}
