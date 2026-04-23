import React from 'react';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import { WidgetDefinition } from './types';
import { CashFlowWidget } from './widgets/CashFlowWidget';
import { CategorySummaryWidget } from './widgets/CategorySummaryWidget';
import { MonthSummaryKpiWidget } from './widgets/MonthSummaryKpiWidget';
import { NetWorthWidget } from './widgets/NetWorthWidget';

export const AVAILABLE_WIDGETS: WidgetDefinition[] = [
  {
    type: 'month-summary-kpi',
    defaultTitleKey: 'dashboard.widgets.registry.periodSummary.title',
    descriptionKey: 'dashboard.widgets.registry.periodSummary.description',
    component: MonthSummaryKpiWidget,
    defaultSize: {
      w: { xs: 12, md: 12 },
      h: { xs: 4, md: 3 },
    },
    icon: <AccountBalanceIcon />,
  },
  {
    type: 'cash-flow',
    defaultTitleKey: 'dashboard.widgets.registry.cashFlow.title',
    descriptionKey: 'dashboard.widgets.registry.cashFlow.description',
    component: CashFlowWidget,
    defaultSize: {
      w: { xs: 12, md: 12 },
      h: { xs: 4, md: 4 },
    },
    icon: <BarChartIcon />,
  },
  {
    type: 'net-worth',
    defaultTitleKey: 'dashboard.widgets.registry.netWorth.title',
    descriptionKey: 'dashboard.widgets.registry.netWorth.description',
    component: NetWorthWidget,
    defaultSize: {
      w: { xs: 12, md: 12 },
      h: { xs: 4, md: 4 },
    },
    icon: <ShowChartIcon />,
  },
  {
    type: 'category-summary',
    defaultTitleKey: 'dashboard.widgets.registry.categoryDonut.title',
    descriptionKey: 'dashboard.widgets.registry.categoryDonut.description',
    component: CategorySummaryWidget,
    defaultSize: {
      w: { xs: 12, md: 12 },
      h: { xs: 8, md: 5 },
    },
    icon: <PieChartIcon />,
  },
];

export const getWidgetDefinition = (type: string): WidgetDefinition | undefined => {
  return AVAILABLE_WIDGETS.find((w) => w.type === type);
};
