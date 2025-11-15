"use client";

import React, { useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import { useTranslation } from '@/shared/i18n';
import { customersTranslations } from '../customers/i18n';

export default function CustomersSmartPage() {
  const { t } = useTranslation(customersTranslations);
  const [dialogOpen, setDialogOpen] = useState(false);

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('title')} {/* Domain-specific */}
        </Typography>
        <Button variant="contained" onClick={() => setDialogOpen(true)}>
          {t('addCustomer')} {/* Domain-specific */}
        </Button>
      </Box>
      
      <Box sx={{ p: 2, border: '1px dashed #ccc', borderRadius: 1, mb: 3 }}>
        <Typography variant="body1" color="text.secondary">
          This page demonstrates the smart i18n approach. The translation function 
          automatically falls back to common translations when domain translations are not found.
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          Notice how 'save' and 'cancel' fall back to common translations automatically!
        </Typography>
      </Box>

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
        <DialogTitle>
          {t('createCustomer')} {/* Domain-specific */}
        </DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            label={t('name')} // Domain-specific
            margin="normal"
          />
          <TextField
            fullWidth
            label={t('email')} // Domain-specific
            margin="normal"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>
            {t('cancel')} {/* Falls back to common */}
          </Button>
          <Button variant="contained" onClick={() => setDialogOpen(false)}>
            {t('save')} {/* Falls back to common */}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
