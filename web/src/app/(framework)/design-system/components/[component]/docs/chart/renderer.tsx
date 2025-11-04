"use client";

import React from "react";
import Box from '@mui/material/Box';
import { Chart } from '@/shared/components/ui/data-display/Chart';
import { ComponentRendererProps } from '../types';

export const ChartRenderer: React.FC<ComponentRendererProps> = ({ example }) => {
  switch (example) {
    case 'Line Chart':
      return (
        <Chart
          type="line"
          data={[
            { name: 'Jan', value: 400 },
            { name: 'Feb', value: 300 },
            { name: 'Mar', value: 200 },
            { name: 'Apr', value: 500 },
            { name: 'May', value: 800 },
            { name: 'Jun', value: 600 },
          ]}
          title="Sales Over Time"
          showLegend={true}
          showTooltip={true}
          showGrid={true}
          height={250}
        />
      );
    
    case 'Bar Chart':
      return (
        <Chart
          type="bar"
          data={[
            { name: 'Q1', value: 400 },
            { name: 'Q2', value: 300 },
            { name: 'Q3', value: 200 },
            { name: 'Q4', value: 500 },
          ]}
          title="Quarterly Sales"
          showLegend={true}
          showTooltip={true}
          showGrid={true}
          height={250}
        />
      );
    
    case 'Pie Chart':
      return (
        <Chart
          type="pie"
          data={[
            { name: 'Desktop', value: 400 },
            { name: 'Mobile', value: 300 },
            { name: 'Tablet', value: 200 },
            { name: 'Other', value: 100 },
          ]}
          title="Device Usage"
          showLegend={true}
          showTooltip={true}
          height={250}
        />
      );
    
    case 'Area Chart':
      return (
        <Chart
          type="area"
          data={[
            { name: 'Jan', value: 400 },
            { name: 'Feb', value: 300 },
            { name: 'Mar', value: 200 },
            { name: 'Apr', value: 500 },
            { name: 'May', value: 800 },
            { name: 'Jun', value: 600 },
          ]}
          title="User Growth"
          showLegend={true}
          showTooltip={true}
          showGrid={true}
          height={250}
        />
      );
    
    case 'Custom Colors':
      return (
        <Chart
          type="line"
          data={[
            { name: 'Jan', value: 400 },
            { name: 'Feb', value: 300 },
            { name: 'Mar', value: 200 },
            { name: 'Apr', value: 500 },
            { name: 'May', value: 800 },
            { name: 'Jun', value: 600 },
          ]}
          title="Custom Styled Chart"
          colors={['#ff6b6b', '#4ecdc4', '#45b7d1', '#96ceb4', '#feca57']}
          showLegend={true}
          showTooltip={true}
          showGrid={true}
          height={250}
        />
      );
    
    case 'Responsive Charts':
      return (
        <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(250px, 1fr))', gap: 2 }}>
          <Chart
            type="line"
            data={[
              { name: 'Jan', value: 400 },
              { name: 'Feb', value: 300 },
              { name: 'Mar', value: 200 },
            ]}
            title="Sales"
            showLegend={false}
            showTooltip={true}
            showGrid={true}
            height={200}
          />
          <Chart
            type="bar"
            data={[
              { name: 'Q1', value: 400 },
              { name: 'Q2', value: 300 },
              { name: 'Q3', value: 200 },
            ]}
            title="Revenue"
            showLegend={false}
            showTooltip={true}
            showGrid={true}
            height={200}
          />
          <Chart
            type="pie"
            data={[
              { name: 'Desktop', value: 400 },
              { name: 'Mobile', value: 300 },
              { name: 'Tablet', value: 200 },
            ]}
            title="Devices"
            showLegend={false}
            showTooltip={true}
            height={200}
          />
        </Box>
      );
    
    default:
      return (
        <Chart
          type="line"
          data={[
            { name: 'Jan', value: 400 },
            { name: 'Feb', value: 300 },
            { name: 'Mar', value: 200 },
            { name: 'Apr', value: 500 },
          ]}
          title="Sample Chart"
          showLegend={true}
          showTooltip={true}
          showGrid={true}
          height={250}
        />
      );
  }
};
