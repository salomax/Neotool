"use client";

import React from "react";
import {
  LineChart,
  BarChart,
  PieChart,
  AreaChart,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  Line,
  Bar,
  Pie,
  Area,
  Cell,
} from "recharts";
import { Box, Typography, Paper, useTheme } from "@mui/material";

export type ChartType = 'line' | 'bar' | 'pie' | 'area';

export interface ChartData {
  name: string;
  value: number;
  [key: string]: any;
}

export interface ChartProps {
  type: ChartType;
  data: ChartData[];
  title?: string;
  width?: number | string;
  height?: number | string;
  showLegend?: boolean;
  showTooltip?: boolean;
  showGrid?: boolean;
  colors?: string[];
  xAxisKey?: string;
  yAxisKey?: string;
  dataKey?: string;
  nameKey?: string;
  valueKey?: string;
  strokeWidth?: number;
  fillOpacity?: number;
  animationDuration?: number;
  margin?: {
    top?: number;
    right?: number;
    bottom?: number;
    left?: number;
  };
  sx?: any;
}

const defaultColors = [
  '#8884d8', '#82ca9d', '#ffc658', '#ff7300', '#00ff00',
  '#ff00ff', '#00ffff', '#ffff00', '#ff0000', '#0000ff'
];

export const Chart: React.FC<ChartProps> = ({
  type,
  data,
  title,
  width = '100%',
  height = 300,
  showLegend = true,
  showTooltip = true,
  showGrid = true,
  colors = defaultColors,
  xAxisKey = 'name',
  yAxisKey = 'value',
  dataKey = 'value',
  nameKey = 'name',
  valueKey = 'value',
  strokeWidth = 2,
  fillOpacity = 0.6,
  animationDuration = 800,
  margin = { top: 5, right: 30, left: 20, bottom: 5 },
  sx,
}) => {
  const theme = useTheme();

  const renderChart = () => {
    const commonProps = {
      data,
      margin,
      animationDuration,
    };

    switch (type) {
      case 'line':
        return (
          <LineChart {...commonProps}>
            {showGrid && <CartesianGrid strokeDasharray="3 3" />}
            <XAxis dataKey={xAxisKey} />
            <YAxis />
            {showTooltip && <Tooltip />}
            {showLegend && <Legend />}
            <Line
              type="monotone"
              dataKey={dataKey}
              stroke={colors[0]}
              strokeWidth={strokeWidth}
              dot={{ fill: colors[0], strokeWidth: 2, r: 4 }}
            />
          </LineChart>
        );

      case 'bar':
        return (
          <BarChart {...commonProps}>
            {showGrid && <CartesianGrid strokeDasharray="3 3" />}
            <XAxis dataKey={xAxisKey} />
            <YAxis />
            {showTooltip && <Tooltip />}
            {showLegend && <Legend />}
            <Bar dataKey={dataKey} fill={colors[0]} />
          </BarChart>
        );

      case 'area':
        return (
          <AreaChart {...commonProps}>
            {showGrid && <CartesianGrid strokeDasharray="3 3" />}
            <XAxis dataKey={xAxisKey} />
            <YAxis />
            {showTooltip && <Tooltip />}
            {showLegend && <Legend />}
            <Area
              type="monotone"
              dataKey={dataKey}
              stroke={colors[0]}
              fill={colors[0]}
              fillOpacity={fillOpacity}
              strokeWidth={strokeWidth}
            />
          </AreaChart>
        );

      case 'pie':
        return (
          <PieChart {...commonProps}>
            {showTooltip && <Tooltip />}
            {showLegend && <Legend />}
            <Pie
              data={data}
              cx="50%"
              cy="50%"
              labelLine={false}
              label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}
              outerRadius={80}
              fill="#8884d8"
              dataKey={valueKey}
              nameKey={nameKey}
            >
              {data.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={colors[index % colors.length]} />
              ))}
            </Pie>
          </PieChart>
        );

      default:
        return null;
    }
  };

  return (
    <Paper sx={{ p: 2, ...sx }}>
      {title && (
        <Typography variant="h6" component="h3" gutterBottom>
          {title}
        </Typography>
      )}
      <Box sx={{ width, height }}>
        <ResponsiveContainer width="100%" height="100%">
          {renderChart()}
        </ResponsiveContainer>
      </Box>
    </Paper>
  );
};

export default Chart;
