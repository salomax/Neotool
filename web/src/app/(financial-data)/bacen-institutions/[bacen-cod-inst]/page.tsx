"use client";

import { useParams } from "next/navigation";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { Page, SectionHeader } from "@/shared/components/ui/layout";
import { Box, Typography, Stack, List, ListItemButton, ListItemText, CircularProgress, Tooltip, IconButton, Accordion, AccordionSummary, AccordionDetails } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { LineChart, CurrencyDisplay, MetricLineChart } from "@/shared/components/ui/data-display";
import { BacenInstitutionSummaryPaper } from "@/features/financial_data/components/BacenInstitutionSummaryPaper";
import { BacenInstitutionBalanceSummaryGrid } from "@/features/financial_data/components/BacenInstitutionBalanceSummaryGrid";
import { BacenInstitutionReportDetail } from "@/features/financial_data/components/BacenInstitutionReportDetail";
import { useBacenInstitutionDetail } from "@/features/financial_data/hooks/useBacenInstitutionDetail";

export default function BacenInstitutionDetailPage() {
  const params = useParams();
  const encodedId = params["bacen-cod-inst"] as string;
  const { t } = useTranslation(financialDataTranslations);
  const {
    bacenCodInst,
    institution,
    loadingInstitution,
    errorInstitution,
    reports,
    selectedReportId,
    setSelectedReportId,
    reportDetails,
    loadingReport,
  } = useBacenInstitutionDetail(encodedId);

  usePageTitle(institution?.name || t("pageTitle"));
  
  // Set breadcrumb label to institution name instead of obfuscated code
  // Use the actual encoded segment value from the URL as the key
  useBreadcrumbLabel(encodedId, institution?.name || null);

  if (!bacenCodInst) {
    return (
      <Page>
        <Typography color="error">
          {t("messages.invalidInstitutionId")}
        </Typography>
      </Page>
    );
  }

  if (loadingInstitution) {
    return (
      <Page>
        <Typography>{t("actions.loading")}</Typography>
      </Page>
    );
  }

  if (errorInstitution) {
    return (
      <Page>
        <Typography color="error">
          {t("messages.unableToLoadInstitution")}
        </Typography>
      </Page>
    );
  }

  if (!institution) {
    return (
      <Page>
        <Typography>{t("messages.institutionNotFound")}</Typography>
      </Page>
    );
  }

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      sidebar={
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ p: 3, pb: 2 }}>
            <Typography variant="h6">{t("reports")}</Typography>
          </Box>
          <List sx={{ flex: 1, overflow: 'auto', px: 2 }}>
            {reports.map((report: any) => (
              <ListItemButton 
                key={report.reportId}
                selected={selectedReportId === report.reportId}
                onClick={() => setSelectedReportId(report.reportId)}
                sx={{ 
                  borderRadius: 1, 
                  mb: 0.5,
                  '&.Mui-selected': {
                    bgcolor: 'action.selected',
                    fontWeight: 'medium'
                  }
                }}
              >
                <ListItemText 
                  primary={report.name} 
                  slotProps={{ primary: { variant: "body2" } }}
                />
              </ListItemButton>
            ))}
          </List>
        </Box>
      }
    >


      <Page>
        <Box sx={{ mb: 4 }}>
          <BacenInstitutionSummaryPaper institution={institution} />
        </Box>
        {loadingReport ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : selectedReportId === "1" ? (
          <Box sx={{ mt: 4 }}>
            <SectionHeader
              title={t("lastBalanceSummary.title")}
              actions={
                <Tooltip title={t("lastBalanceSummary.tooltip")}>
                  <IconButton size="small">
                    <InfoOutlinedIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              }
            />
            <BacenInstitutionBalanceSummaryGrid institutionId={institution.id} />
          </Box>
        ) : reportDetails ? (
          <BacenInstitutionReportDetail reportDetails={reportDetails} />
        ) : (
          <Typography color="text.secondary">
            {t("messages.selectReport")}
          </Typography>
        )}
      </Page>
    </SidebarLayout>
  );
}
