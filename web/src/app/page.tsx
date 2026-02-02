"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Stack,
  styled
} from "@/shared/ui/mui-imports";
import { Wordmark } from "@/shared/ui/brand";

const HeroTypography = styled(Typography)(({ theme }) => ({
  fontWeight: 500,
  color: theme.palette.text.primary,
  marginBottom: theme.spacing(2),
  letterSpacing: '-0.02em',
  fontSize: '3rem',
  [theme.breakpoints.up('md')]: {
    fontSize: '4rem',
  },
  [theme.breakpoints.up('lg')]: {
    fontSize: '5rem',
  },
  [theme.breakpoints.up('xl')]: {
    fontSize: '6rem',
  },
})) as typeof Typography;

const SubtitleTypography = styled(Typography)(({ theme }) => ({
  marginLeft: "auto",
  marginRight: "auto",
  fontWeight: 400,
  maxWidth: 500,
  color: theme.palette.text.secondary,
  fontSize: '1rem',
  [theme.breakpoints.up('md')]: {
    fontSize: '1.5rem',
  },
  [theme.breakpoints.up('lg')]: {
    fontSize: '2rem',
  },
  [theme.breakpoints.up('xl')]: {
    fontSize: '2.5rem',
  },
})) as typeof Typography;

export default function WelcomePage() {
  return (
    <>
      <Container maxWidth="lg" sx={{ height: '100vh', display: 'flex', flexDirection: 'column', justifyContent: 'center', py: 4, position: 'relative', zIndex: 1 }}>
        <Box sx={{ textAlign: "center", mb: 8, mt: -10 }}>
          <Stack alignItems="center" spacing={2} sx={{ mb: 4 }}>
            <Wordmark variant="blue" size="large" />
          </Stack>
          
          <HeroTypography 
            variant="h2" 
            component="h1"
          >
            Clarity for your
            <br />
            financial life.
          </HeroTypography>
          
          <SubtitleTypography 
            variant="h6"
          >
            Consolidated market data and personal
            <br />
            finance, in one place.
          </SubtitleTypography>
        </Box>
      </Container>
    </>
  );
}
