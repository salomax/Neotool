"use client";

import React from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";

export default function SettingsPage() {
  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Settings
      </Typography>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
        Application settings and configuration.
      </Typography>

      <Paper sx={{ p: 3 }}>
        <Box>
          <Typography variant="h6" gutterBottom>
            Settings
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Settings content will be displayed here.
          </Typography>
        </Box>
      </Paper>
    </Container>
  );
}
