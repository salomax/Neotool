"use client";

import { useParams } from "next/navigation";
import { useMemo, useEffect } from "react";
import { extractIdFromSemanticPath } from "@/shared/utils/url";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { Page, SectionHeader } from "@/shared/components/ui/layout";
import { 
  Box, 
  Typography, 
  List, 
  ListItemButton, 
  ListItemText, 
  CircularProgress, 
  Tooltip, 
  IconButton, 
  Grid,
  Tabs,
  Tab,
  useTheme,
  useMediaQuery
} from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import {
  BacenInstitutionSummaryPaper,
  BacenInstitutionBalanceSummaryGrid,
  BacenInstitutionReportDetail,
  BacenDataFooter,
  DataRestrictionNotice,
} from "@/widgets/financial-data";
import { useBacenInstitutionDetail } from "@/features/financial-data/view-institution-detail";

export default function BacenInstitutionDetailClient() {
  const params = useParams();
  const rawParam = params["bacen-cod-inst"] as string;

  const encodedId = extractIdFromSemanticPath(rawParam);

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

  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));

  usePageTitle(institution?.name || t("pageTitle"));

  // Update document title when language changes (client-side)
  useEffect(() => {
    if (institution?.name) {
      const title = t('seo.institution.title', { name: institution.name });
      // Ensure we don't set the key as title if translation fails
      if (title && !title.startsWith('seo.')) {
        document.title = title;
      }
    } else if (errorInstitution || (!loadingInstitution && !institution)) {
      const notFound = t('seo.institution.notFoundTitle');
      if (notFound && !notFound.startsWith('seo.')) {
        document.title = notFound;
      }
    }
  }, [institution, t, loadingInstitution, errorInstitution]);

  // Set breadcrumb label to institution name instead of obfuscated code
  // Use the actual encoded segment value from the URL as the key
  useBreadcrumbLabel(rawParam, institution?.name || null);

  const quarterMessage = useMemo(() => {
    const quarterStr = (institution as any)?.quarter;
    if (!quarterStr || quarterStr.length !== 6) return null;

    const year = quarterStr.substring(0, 4);
    const month = quarterStr.substring(4, 6);

    const quarterMap: Record<string, string> = {
      "03": "1",
      "06": "2",
      "09": "3",
      "12": "4",
    };

    const quarterIndex = quarterMap[month];
    if (!quarterIndex) return null;

    return t("lastBalanceSummary.lastUpdateMessage", {
      quarter: t(`lastBalanceSummary.quarters.${quarterIndex}`),
      year,
    });
  }, [institution, t]);

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

  const isRestricted = !!(institution as any)?.dataRestriction?.isRestricted;

  // When data is restricted, show only summary and notice (no reports/balance/report details)
  if (isRestricted) {
    return (
      <Page>
        <Box sx={{ mb: 4 }}>
          <Grid container spacing={4}>
            <Grid size={{ xs: 12, lg: 12 }}>
              <BacenInstitutionSummaryPaper institution={institution} />
            </Grid>
          </Grid>
        </Box>
        <Box sx={{ mb: 4 }}>
          <DataRestrictionNotice
            reason={(institution as any)?.dataRestriction?.reason ?? undefined}
          />
        </Box>
        <BacenDataFooter />
      </Page>
    );
  }

  const content = (
    <Page>
      <Box sx={{ mb: 4, px: { xs: 2, sm: 0 }, pt: { xs: 2, sm: 0 } }}>
        <Grid container spacing={4}>
          <Grid size={{ xs: 12, lg: 12 }}>
            <BacenInstitutionSummaryPaper institution={institution} />
          </Grid>
        </Grid>
      </Box>

      {isMobile && (
        <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3, mx: 0, px: 0 }}>
          <Tabs 
            value={selectedReportId} 
            onChange={(_, v) => setSelectedReportId(v)}
            variant="scrollable"
            scrollButtons="auto"
            allowScrollButtonsMobile
            aria-label="Report tabs"
          >
            {reports.map((report: any) => (
              <Tab key={report.reportId} label={report.name} value={report.reportId} />
            ))}
          </Tabs>
        </Box>
      )}

      {loadingReport ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress />
        </Box>
      ) : selectedReportId === "1" ? (
        <Box sx={{ mt: 4, px: { xs: 2, sm: 0 } }}>
          <SectionHeader
            title={
              <Box>
                <Typography variant="h6" component="h2" sx={{ fontWeight: 600 }}>
                  {t("lastBalanceSummary.title")}
                </Typography>
                {quarterMessage && (
                  <Typography variant="caption" color="text.secondary" display="block">
                    {quarterMessage}
                  </Typography>
                )}
              </Box>
            }
            actions={
              !quarterMessage ? (
                <Tooltip title={t("lastBalanceSummary.tooltip")}>
                  <IconButton size="small">
                    <InfoOutlinedIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              ) : undefined
            }
          />
          <BacenInstitutionBalanceSummaryGrid institutionId={institution.id} />
        </Box>
      ) : reportDetails ? (
        <Box sx={{ px: { xs: 2, sm: 0 } }}>
          <BacenInstitutionReportDetail
            reportDetails={reportDetails}
            quarterMessage={quarterMessage}
          />
        </Box>
      ) : (
        <Typography color="text.secondary">
          {t("messages.selectReport")}
        </Typography>
      )}
      <Box sx={{ px: { xs: 2, sm: 0 } }}>
        <BacenDataFooter />
      </Box>
    </Page>
  );

  if (isMobile) {
    return content;
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
                  mb: .5,
                  borderRadius: 1,
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
      {content}
    </SidebarLayout>
  );
}
