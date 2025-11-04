"use client";

import React, { useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import InputLabel from "@mui/material/InputLabel";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import Chip from "@mui/material/Chip";
import { useTranslation } from '@/shared/i18n';
import { multilangTranslations } from './i18n';
// import { getSupportedLanguages } from '@/shared/i18n/language-config';

export default function MultiLangPage() {
  const { t } = useTranslation(multilangTranslations);
  const [selectedLanguage, setSelectedLanguage] = useState('en');
  const supportedLanguages = [
    { code: 'en', name: 'English' },
    { code: 'pt', name: 'Português' },
    { code: 'es', name: 'Español' },
    { code: 'fr', name: 'Français' },
    { code: 'de', name: 'Deutsch' }
  ];

  const handleLanguageChange = (event: any) => {
    const newLanguage = event.target.value as string;
    setSelectedLanguage(newLanguage);
    // In a real app, you would change the i18n language here
    // i18n.changeLanguage(newLanguage);
  };

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          {t('title')}
        </Typography>
        <FormControl sx={{ minWidth: 200 }}>
          <InputLabel>{t('selectLanguage')}</InputLabel>
          <Select
            value={selectedLanguage}
            onChange={handleLanguageChange}
            label={t('selectLanguage')}
          >
            {supportedLanguages.map((lang) => (
              <MenuItem key={lang.code} value={lang.code}>
                {lang.name}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Box>
      
      <Box sx={{ mb: 3 }}>
        <Typography variant="h6" gutterBottom>
          {t('welcome')}
        </Typography>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('description')}
        </Typography>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2 }}>
          <Typography variant="body2">
            {t('currentLanguage')}:
          </Typography>
          <Chip label={selectedLanguage.toUpperCase()} color="primary" />
        </Box>
        
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
          <Typography variant="body2">
            {t('supportedLanguages')}:
          </Typography>
          {supportedLanguages.map((lang) => (
            <Chip 
              key={lang.code} 
              label={lang.name} 
              variant={lang.code === selectedLanguage ? "filled" : "outlined"}
              size="small"
            />
          ))}
        </Box>
      </Box>
      
      <Box sx={{ p: 2, border: '1px dashed #ccc', borderRadius: 1 }}>
        <Typography variant="body1" color="text.secondary">
          This page demonstrates dynamic language support. The translation object can contain 
          any number of languages, and the system will automatically register all of them.
        </Typography>
        <Typography variant="body2" sx={{ mt: 1 }}>
          Try switching languages to see the translations in action!
        </Typography>
      </Box>
    </Container>
  );
}
