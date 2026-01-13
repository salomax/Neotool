import * as React from "react";
import { 
  Paper, 
  Box, 
  Typography, 
  Avatar, 
  Stack, 
  Grid, 
  Link, 
  Divider,
  Chip
} from "@mui/material";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";

import { useTranslation, i18n } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { 
  BacenFinanceInstitution 
} from "@/lib/graphql/types/__generated__/graphql";
import { formatDate } from "@/shared/utils/date";
import { formatCNPJ, digitsOnly } from "@/shared/utils/br/format";
import { isValidCNPJ } from "@/shared/utils/br/validators";

export interface BacenInstitutionSummaryPaperProps {
  institution: BacenFinanceInstitution;
}

export function BacenInstitutionSummaryPaper({ institution }: BacenInstitutionSummaryPaperProps) {
  const { t } = useTranslation(financialDataTranslations);
  const locale = i18n.language;

  // 1. Institution Name & Logo
  const name = institution.name;
  const logoUrl = institution.asset?.publicUrl || institution.logoAsset?.publicUrl;
  
  // 2. CNPJ
  // Attempt to extract CNPJ from ID or other fields if available.
  // Assuming ID might be the CNPJ or we check if ID looks like a CNPJ.
  // The requirement says: "Extracted from the institution ID if it contains CNPJ information"
  const potentialCnpj = digitsOnly(institution.id);
  const cnpj = isValidCNPJ(potentialCnpj) ? formatCNPJ(potentialCnpj) : null;

  // 3. Trade Name
  const tradeName = institution.tradeName || institution.name;

  // 4. Founded Date
  const foundedDate = institution.founded 
    ? formatDate(institution.founded, { locale, dateStyle: 'long' })
    : '-';

  // 5. Shareholding Control
  const controlTypeLabel = institution.controlType 
    ? t(`institutionSummary.controlType.${institution.controlType}`)
    : '-';

  // 6. Head Office / Address
  const address = institution.address || '-';

  // 7. Website
  const website = institution.website;

  // 8. Consolidation
  const consolidationLabel = institution.institutionType
    ? t(`institutionSummary.consolidationType.${institution.institutionType}`)
    : '-';

  // 9. Institution Type
  // Prefer tipoConsolidadoBancario, fallback to institutionBankType
  const bankType = institution.tipoConsolidadoBancario || institution.institutionBankType;
  const bankTypeLabel = bankType
    ? t(`institutionSummary.bankType.${bankType}`)
    : '-';

  return (
    <Paper 
      elevation={0} 
      variant="outlined"
      sx={{ 
        p: 3, 
        borderRadius: 2,
        bgcolor: 'background.paper'
      }}
    >
      <Stack spacing={3}>
        {/* Header: Logo + Name + CNPJ */}
        <Stack direction="row" spacing={3} alignItems="flex-start">
          <Avatar
            src={logoUrl || undefined}
            alt={name}
            sx={{
              width: 80,
              height: 80,
              border: "1px solid",
              borderColor: "divider",
              bgcolor: "background.default",
            }}
          >
            <AccountBalanceIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
          </Avatar>
          
          <Box>
            <Typography variant="h5" component="h1" fontWeight="bold" gutterBottom>
              {name}
            </Typography>
            {cnpj && (
              <Chip 
                label={`${t("institutionSummary.cnpj")}: ${cnpj}`} 
                size="small" 
                variant="outlined" 
                color="default"
              />
            )}
            {institution.legalName && institution.legalName !== name && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                {institution.legalName}
              </Typography>
            )}
          </Box>
        </Stack>

        <Divider />

        {/* 3-Column Grid */}
        <Grid container spacing={3}>
          {/* Column 1 */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={2}>
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.tradeName")}
                </Typography>
                <Typography variant="body1">
                  {tradeName}
                </Typography>
              </Box>
              
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.founded")}
                </Typography>
                <Typography variant="body1">
                  {foundedDate}
                </Typography>
              </Box>
            </Stack>
          </Grid>

          {/* Column 2 */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={2}>
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.shareholdingControl")}
                </Typography>
                <Typography variant="body1">
                  {controlTypeLabel}
                </Typography>
              </Box>
              
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.headOffice")}
                </Typography>
                <Typography variant="body1">
                  {address}
                </Typography>
              </Box>
            </Stack>
          </Grid>

          {/* Column 3 */}
          <Grid size={{ xs: 12, md: 4 }}>
            <Stack spacing={2}>
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.website")}
                </Typography>
                {website ? (
                  <Link 
                    href={website.startsWith('http') ? website : `https://${website}`} 
                    target="_blank" 
                    rel="noopener noreferrer"
                    underline="hover"
                    sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis' }}
                  >
                    {website}
                  </Link>
                ) : (
                  <Typography variant="body1">-</Typography>
                )}
              </Box>
              
              <Box>
                <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
                  {t("institutionSummary.consolidation")}
                </Typography>
                <Typography variant="body1">
                  {consolidationLabel}
                </Typography>
              </Box>
            </Stack>
          </Grid>
        </Grid>

        <Divider />

        {/* Footer: Institution Type */}
        <Box>
          <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 0.5 }}>
            {t("institutionSummary.institutionType")}
          </Typography>
          <Typography variant="body1">
            {bankTypeLabel}
          </Typography>
        </Box>
      </Stack>
    </Paper>
  );
}
