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
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import {
  BacenFinanceInstitution
} from "@/lib/graphql/types/__generated__/graphql";
import { formatDate } from "@/shared/utils/date";
import { formatCNPJ, digitsOnly } from "@/shared/utils/br/format";
import { isValidCNPJ } from "@/shared/utils/br/validators";

const SeparatorDot = () => (
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
);

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

  const detailLabels = [
    controlTypeLabel !== "-" && controlTypeLabel,
    consolidationLabel !== "-" && consolidationLabel,
    bankTypeLabel !== "-" && bankTypeLabel,
  ].filter((label): label is string => Boolean(label));

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
        spacing={2}
        alignItems="flex-start"
      >
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

        <Box sx={{ flex: 1 }}>
          <Typography variant="h6" component="h1" fontWeight="bold">
            {name}
          </Typography>

          {infoLineParts.length > 0 && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.5 }}
            >
              {infoLineParts.map((part, index) => (
                <React.Fragment key={part}>
                  {index > 0 && <SeparatorDot />}
                  {part}
                </React.Fragment>
              ))}
            </Typography>
          )}

          {detailLabels.length > 0 && (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.5 }}
            >
              {detailLabels.map((label, index) => (
                <React.Fragment key={label}>
                  {index > 0 && <SeparatorDot />}
                  {label}
                </React.Fragment>
              ))}
            </Typography>
          )}

          {website && (
            <Box sx={{ mt: 1 }}>
              <Link
                href={website.startsWith("http") ? website : `https://${website}`}
                target="_blank"
                rel="noopener noreferrer"
                underline="hover"
                sx={{
                  fontSize: 13,
                  fontWeight: 500,
                  display: "inline-flex",
                  alignItems: "center",
                  wordBreak: "break-all",
                }}
              >
                {website}
                <OpenInNewIcon
                  fontSize="inherit"
                  sx={{ ml: 0.5 }}
                />
              </Link>
            </Box>
          )}
        </Box>
      </Stack>
    </Paper>
  );
}
