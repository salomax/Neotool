"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Stack,
  styled,
  Link
} from "@/shared/ui/mui-imports";
import { Wordmark, Logo } from "@/shared/ui/brand";
import { useTranslation } from "@/shared/i18n";
import { landingTranslations } from "./i18n";
import LinkedInIcon from "@mui/icons-material/LinkedIn";

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
  const { t } = useTranslation(landingTranslations);

  return (
    <>
      <Container maxWidth="lg" sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', py: 4, position: 'relative', zIndex: 1 }}>
        <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', textAlign: "center", mb: 8, mt: -10 }}>
          <Stack alignItems="center" spacing={2} sx={{ mb: 4 }}>
            <Wordmark variant="blue" size="medium" />
          </Stack>
          
          <HeroTypography 
            variant="h3" 
            component="h2"
          >
            {t("title.line1")}
            <br />
            {t("title.line2")}
          </HeroTypography>
          
          <SubtitleTypography 
            variant="h6"
          >
            {t("subtitle.line1")}
            <br />
            {t("subtitle.line2")}
          </SubtitleTypography>
        </Box>

        <Box component="footer" sx={{ 
          display: 'flex', 
          justifyContent: 'space-between', 
          alignItems: 'center', 
          width: '100%', 
          py: 3,
          borderTop: '1px solid',
          borderColor: 'divider',
          flexWrap: 'wrap',
          gap: 2
        }}>
          <Box>
            <Logo size="small" /> 
          </Box>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 2, sm: 3 }} alignItems="center">
              <Link href="mailto:contato@invistus.com.br" color="text.secondary" underline="hover" sx={{ fontSize: '0.875rem' }}>
                  contato@invistus.com.br
              </Link>
              <Link 
                  href="https://www.linkedin.com/company/invistus" 
                  target="_blank" 
                  rel="noopener noreferrer"
                  color="text.secondary"
                  underline="none"
                  sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}
              >
                  <LinkedInIcon fontSize="small" />
                  <Typography variant="body2" fontWeight="bold">LinkedIn</Typography>
              </Link>
          </Stack>

          <Typography variant="body2" color="text.secondary">
            © 2026 Invistus — Todos os direitos reservados
          </Typography>
        </Box>
      </Container>
    </>
  );
}
