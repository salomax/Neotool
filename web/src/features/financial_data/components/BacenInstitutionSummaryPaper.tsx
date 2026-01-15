import * as React from "react";
import {
  Paper,
  Box,
  Typography,
  Avatar,
  Stack,
  Link,
  useTheme,
} from "@mui/material";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import OpenInNewIcon from "@mui/icons-material/OpenInNew";

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
  const theme = useTheme();

  const name = institution.name;
  const logoUrl = institution.asset?.publicUrl || institution.logoAsset?.publicUrl;
  
  const potentialCnpj = digitsOnly(institution.id);
  const cnpj = isValidCNPJ(potentialCnpj) ? formatCNPJ(potentialCnpj) : null;

  const foundedDate = institution.founded 
    ? formatDate(institution.founded, { locale, dateStyle: 'long' })
    : '-';

  const controlTypeLabel = institution.controlType 
    ? t(`institutionSummary.controlType.${institution.controlType}`)
    : '-';

  const address = institution.address || '-';

  const website = institution.website;

  const consolidationLabel = institution.institutionType
    ? t(`institutionSummary.consolidationType.${institution.institutionType}`)
    : '-';

  const bankType = institution.tipoConsolidadoBancario || institution.institutionBankType;
  const bankTypeLabel = bankType
    ? t(`institutionSummary.bankType.${bankType}`)
    : '-';

  const legalName =
    institution.legalName && institution.legalName !== name
      ? institution.legalName
      : null;

  const cnpjPart = cnpj ? `${t("institutionSummary.cnpj")}: ${cnpj}` : null;
  const addressPart = address && address !== "-" ? address : null;

  const infoLineParts = [legalName, cnpjPart, addressPart].filter(
    (part): part is string => Boolean(part)
  );

  const infoLine = infoLineParts.join(" • ");

  return (
    <Paper 
      elevation={0} 
      variant="outlined"
      sx={{ 
        p: 3, 
        borderRadius: 2,
        bgcolor: 'background.paper',
        boxShadow: "0 5px 15px rgba(15, 23, 42, 0.08)"
      }}
    >
      <Stack
        direction="row"
        spacing={3}
        alignItems="flex-start"
        justifyContent="space-between"
      >
        <Stack direction="row" spacing={2} alignItems="center">
          <Avatar
            src={logoUrl || undefined}
            alt={name}
            sx={{
              width: 56,
              height: 56,
              borderRadius: 2,
              bgcolor: "background.default",
            }}
          >
            <AccountBalanceIcon sx={{ fontSize: 28, color: "text.secondary" }} />
          </Avatar>

          <Box>
            <Typography variant="h6" component="h1" fontWeight="bold">
              {name}
            </Typography>

            {infoLine && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ mt: 0.5 }}
              >
                {infoLine}
              </Typography>
            )}

            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.5 }}
            >
              {[
                controlTypeLabel !== "-" && controlTypeLabel,
                consolidationLabel !== "-" && consolidationLabel,
                bankTypeLabel !== "-" && bankTypeLabel,
              ]
                .filter((label): label is string => Boolean(label))
                .map((label, index, array) => (
                  <React.Fragment key={label}>
                    {index > 0 && (
                      <Box
                        component="span"
                        sx={{
                          display: "inline-block",
                          width: 4,
                          height: 4,
                          borderRadius: "50%",
                          bgcolor: "text.secondary",
                          mx: 1,
                          verticalAlign: "middle",
                        }}
                      />
                    )}
                    {label}
                  </React.Fragment>
                ))}
            </Typography>
          </Box>
        </Stack>

        {website && (
          <Box sx={{ textAlign: "right", ml: 2 }}>
            <Link
              href={website.startsWith("http") ? website : `https://${website}`}
              target="_blank"
              rel="noopener noreferrer"
              underline="hover"
              sx={{
                fontSize: 13,
                fontWeight: 500,
                whiteSpace: "nowrap",
                maxWidth: 240,
                display: "inline-block",
                overflow: "hidden",
                textOverflow: "ellipsis",
              }}
            >
              {website}
              <OpenInNewIcon
                fontSize="inherit"
                sx={{ ml: 0.5, verticalAlign: "text-bottom" }}
              />
            </Link>
          </Box>
        )}
      </Stack>
    </Paper>
  );
}
