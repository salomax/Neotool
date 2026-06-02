import React from 'react';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import BarChartIcon from '@mui/icons-material/BarChart';
import PieChartIcon from '@mui/icons-material/PieChart';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import DonutLargeIcon from '@mui/icons-material/DonutLarge';
import ViewModuleIcon from '@mui/icons-material/ViewModule';
import RepeatIcon from '@mui/icons-material/Repeat';
import { WidgetDefinition } from './types';
import { CashFlowWidget } from './widgets/CashFlowWidget';
import { CategorySummaryWidget } from './widgets/CategorySummaryWidget';
import { InvestmentAllocationChartWidget } from './widgets/InvestmentAllocationChartWidget';
import { InvestmentTreemapWidget } from './widgets/InvestmentTreemapWidget';
import { MonthSummaryKpiWidget } from './widgets/MonthSummaryKpiWidget';
import { NetWorthWidget } from './widgets/NetWorthWidget';
import { UpcomingRecurringWidget } from './widgets/UpcomingRecurringWidget';

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
  {
    type: 'investment-allocation-chart',
    defaultTitleKey: 'dashboard.widgets.registry.investmentAllocation.title',
    descriptionKey: 'dashboard.widgets.registry.investmentAllocation.description',
    component: InvestmentAllocationChartWidget,
    defaultSize: {
      w: { xs: 12, md: 6 },
      h: { xs: 4, md: 4 },
    },
    icon: <DonutLargeIcon />,
  },
  {
    type: 'upcoming-recurring',
    defaultTitleKey: 'dashboard.widgets.registry.upcomingRecurring.title',
    descriptionKey: 'dashboard.widgets.registry.upcomingRecurring.description',
    component: UpcomingRecurringWidget,
    defaultSize: {
      w: { xs: 12, md: 6 },
      h: { xs: 4, md: 4 },
    },
    icon: <RepeatIcon />,
  },
  {
    type: 'investment-treemap',
    defaultTitleKey: 'dashboard.widgets.registry.investmentTreemap.title',
    descriptionKey: 'dashboard.widgets.registry.investmentTreemap.description',
    component: InvestmentTreemapWidget,
    defaultSize: {
      w: { xs: 12, md: 6 },
      h: { xs: 4, md: 4 },
    },
    icon: <ViewModuleIcon />,
  },
];

export const getWidgetDefinition = (type: string): WidgetDefinition | undefined => {
  return AVAILABLE_WIDGETS.find((w) => w.type === type);
};
