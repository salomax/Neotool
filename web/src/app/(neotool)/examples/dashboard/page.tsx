"use client";
import React, { useEffect, useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import Paper from "@mui/material/Paper";
import Box from "@mui/material/Box";
import { ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, CartesianGrid } from "recharts";

const GRAPHQL_URL = process.env.NEXT_PUBLIC_GRAPHQL_URL || "http://localhost:4000";

export default function DashboardPage() {
  const [summary, setSummary] = useState<any>(null);
  const [series, setSeries] = useState<any[]>([]);

  useEffect(() => {
    (async()=>{
      try {
        // For now, we'll use mock data since dashboard queries aren't in the supergraph yet
        setSummary({
          totalProducts: 0,
          totalCustomers: 0,
          activeCustomers: 0,
          lowStockProducts: 0,
          totalRevenue: 0
        });
        setSeries([]);
      } catch (error) {
        console.error('Error loading dashboard data:', error);
      }
    })();
  }, []);

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" gutterBottom>Operational Dashboard</Typography>
      {summary && (
        <Grid container spacing={2}>
          <Grid item xs={12} sm={6} md={3}><Paper sx={{ p:2 }}><Typography variant="subtitle2">Products</Typography><Typography variant="h5">{summary.totalProducts}</Typography></Paper></Grid>
          <Grid item xs={12} sm={6} md={3}><Paper sx={{ p:2 }}><Typography variant="subtitle2">Customers</Typography><Typography variant="h5">{summary.totalCustomers}</Typography></Paper></Grid>
          <Grid item xs={12} sm={6} md={3}><Paper sx={{ p:2 }}><Typography variant="subtitle2">Active Customers</Typography><Typography variant="h5">{summary.activeCustomers}</Typography></Paper></Grid>
          <Grid item xs={12} sm={6} md={3}><Paper sx={{ p:2 }}><Typography variant="subtitle2">Inventory Value</Typography><Typography variant="h5">R$ {(summary.inventoryValueCents/100).toLocaleString()}</Typography></Paper></Grid>
        </Grid>
      )}
      <Paper sx={{ mt: 3, p: 2 }}>
        <Typography variant="h6" gutterBottom>New Customers (last 30 days)</Typography>
        <Box sx={{ height: 320 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={series}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" />
              <YAxis />
              <Tooltip />
              <Line type="monotone" dataKey="value" dot={false}/>
            </LineChart>
          </ResponsiveContainer>
        </Box>
      </Paper>
    </Container>
  );
}
