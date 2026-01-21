"use client";

import React from "react";
import { 
  Box, 
  Typography, 
  Container
} from "@/shared/ui/mui-imports";
import { Logo, Wordmark } from "@/shared/ui/brand";

export default function WelcomePage() {
  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ textAlign: "center", mb: 6 }}>
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'center' }}>
          <Logo variant="blue" size="medium" />
        </Box>
        <Typography variant="h2" component="h1" gutterBottom sx={{ display: 'flex', justifyContent: 'center' }}>
          <Wordmark variant="blue" size="medium" />
        </Typography>
        {/* <Typography variant="h5" color="text.secondary" sx={{ mx: "auto" }}>
          A comprehensive framework for building modern web applications
        </Typography> */}
      </Box>
    </Container>
  );
}
