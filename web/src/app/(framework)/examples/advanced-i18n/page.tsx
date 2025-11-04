"use client";

import React from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Paper from "@mui/material/Paper";
import { useTranslation } from '@/shared/i18n';
import { customersTranslations } from '../customers/i18n';
import { productsTranslations } from '../products/i18n';

export default function AdvancedI18nPage() {
  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>
        Advanced I18n Patterns
      </Typography>
      
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
        <Approach1 />
        <Approach2 />
        <Approach3 />
      </Box>
    </Container>
  );
}

// Approach 1: Single domain with auto-fallback
function Approach1() {
  const { t } = useTranslation(customersTranslations);

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Approach 1: Single Domain with Auto-Fallback
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Single function that automatically falls back to common translations
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button variant="contained">
          {t('addCustomer')} {/* Domain-specific */}
        </Button>
        <Button>
          {t('save')} {/* Falls back to common */}
        </Button>
        <Button>
          {t('cancel')} {/* Falls back to common */}
        </Button>
      </Box>
    </Paper>
  );
}

// Approach 2: Direct access to domain and common
function Approach2() {
  const { t, tDomain, tCommon } = useTranslation(customersTranslations);

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Approach 2: Direct Access to Domain and Common
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Explicit access to domain and common translation functions
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button variant="contained">
          {tDomain('addCustomer')} {/* Explicitly domain */}
        </Button>
        <Button>
          {tCommon('save')} {/* Explicitly common */}
        </Button>
        <Button>
          {tCommon('cancel')} {/* Explicitly common */}
        </Button>
      </Box>
    </Paper>
  );
}

// Approach 3: Multiple domains
function Approach3() {
  const { t, getDomain, common } = useTranslation([
    customersTranslations,
    productsTranslations
  ]);

  return (
    <Paper sx={{ p: 2 }}>
      <Typography variant="h6" gutterBottom>
        Approach 3: Multiple Domains
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Handles multiple domains with smart fallback
      </Typography>
      <Box sx={{ display: 'flex', gap: 1 }}>
        <Button variant="contained">
          {t('addCustomer')} {/* From customers domain */}
        </Button>
        <Button variant="contained">
          {t('addProduct')} {/* From products domain */}
        </Button>
        <Button>
          {t('save')} {/* Falls back to common */}
        </Button>
        <Button>
          {t('cancel')} {/* Falls back to common */}
        </Button>
      </Box>
    </Paper>
  );
}
