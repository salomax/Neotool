"use client";

import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { useParams } from "next/navigation";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { Page, SectionHeader } from "@/shared/components/ui/layout";
import { Box, Paper, Typography, Stack, List, ListItemButton, ListItemText, CircularProgress, Tooltip, IconButton, Accordion, AccordionSummary, AccordionDetails, Divider } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { BACEN_INSTITUTION, BACEN_INSTITUTION_REPORTS, BACEN_INSTITUTION_REPORT_DETAILS } from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { safeDecodeId } from "@/shared/utils/idObfuscation";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { LineChart, CurrencyDisplay } from "@/shared/components/ui/data-display";
import { BacenInstitutionSummaryPaper } from "@/features/financial_data/components/BacenInstitutionSummaryPaper";
import { BacenInstitutionBalanceSummaryGrid } from "@/features/financial_data/components/BacenInstitutionBalanceSummaryGrid";

export default function BacenInstitutionDetailPage() {
  const params = useParams();
  const encodedId = params["bacen-cod-inst"] as string;
  const bacenCodInst = React.useMemo(() => {
    const decoded = safeDecodeId(encodedId);
    if (!decoded) {
      return null;
    }
    return decoded;
  }, [encodedId]);
  const { t } = useTranslation(financialDataTranslations);
  const [selectedReportId, setSelectedReportId] = React.useState<string | null>(null);

  const { data, loading, error } = useQuery(BACEN_INSTITUTION, {
    variables: {
      id: bacenCodInst,
    },
    skip: !bacenCodInst,
  });

  const { data: reportsData } = useQuery(BACEN_INSTITUTION_REPORTS, {
    variables: {
      id: bacenCodInst,
      anoMes: null, // Latest available quarter
      range: { last: 1 }, // Latest available quarter
    },
    skip: !bacenCodInst,
  });

  const { data: reportDetailsData, loading: loadingReport } = useQuery(BACEN_INSTITUTION_REPORT_DETAILS, {
    variables: {
      id: bacenCodInst,
      reportId: selectedReportId,
      range: { last: 5 * 4 }, // 5 years * 4 quarters per year
    },
    skip: !bacenCodInst || !selectedReportId,
  });

  const institution = (data as any)?.bacenFinanceInstitution;
  const reportDetails = (reportDetailsData as any)?.bacenFinanceInstitution?.reportDetails;

  const reports = React.useMemo(() => {
    const quarters = (reportsData as any)?.bacenFinanceInstitution?.reportsSummary?.quarters;
    if (!quarters || quarters.length === 0) return [];
    
    // Get reports from the first quarter
    const quarterReports = quarters[0].reports || [];
    
    // Sort by reportId numeric
    return [...quarterReports].sort((a: any, b: any) => parseInt(a.reportId) - parseInt(b.reportId));
  }, [reportsData]);

  // Select first report by default when reports are loaded
  React.useEffect(() => {
    if (reports.length > 0 && !selectedReportId) {
      setSelectedReportId(reports[0].reportId);
    }
  }, [reports, selectedReportId]);

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

  if (loading) {
    return (
      <Page>
        <Typography>{t("actions.loading")}</Typography>
      </Page>
    );
  }

  if (error) {
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
        </Box>

        {loadingReport ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
            <CircularProgress />
          </Box>
        ) : reportDetails ? (
          <Box>
            <Typography variant="h5" sx={{ mb: 3 }}>
              {reportDetails.name}
            </Typography>

            {reportDetails.accounts && reportDetails.accounts.length > 0 ? (
              <Box>
                <Paper
                  sx={{
                    p: 2,
                    borderRadius: 2,
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <Stack
                    spacing={0}
                    divider={<Divider />}
                  >
                    {reportDetails.accounts.map((account: any, accountIndex: number) => {
                      const latestEntry = account.valuesByQuarter?.[0];
                      const rawValue = latestEntry?.value;

                      const isPercentageMetric =
                        account.id === "INDICE_DE_BASILEIA" ||
                        account.id === "INDICE_DE_IMOBILIZACAO" ||
                        account.name?.toLowerCase().includes("basileia") ||
                        account.name?.toLowerCase().includes("índice") ||
                        account.name?.toLowerCase().includes("indice");

                      const percentageValue =
                        rawValue == null
                          ? null
                          : typeof rawValue === 'number'
                          ? rawValue * 100
                          : Number(rawValue) * 100;

                      return (
                        <Accordion
                          key={account.id || accountIndex}
                          disableGutters
                          elevation={0}
                          sx={{
                            '&:before': { display: 'none' },
                          }}
                        >
                          <AccordionSummary
                            expandIcon={<ExpandMoreIcon />}
                            sx={{
                              px: 0,
                              minHeight: 56,
                              '& .MuiAccordionSummary-content': {
                                m: 0,
                              },
                            }}
                          >
                            <Box
                              sx={{
                                display: 'flex',
                                alignItems: 'center',
                                width: '100%',
                                justifyContent: 'space-between',
                                gap: 2,
                              }}
                            >
                              <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                {account.name}
                              </Typography>
                              <Box
                                sx={{
                                  display: 'flex',
                                  alignItems: 'center',
                                  gap: 1,
                                }}
                              >
                                {rawValue == null ? (
                                  <Typography variant="body2" color="text.secondary">
                                    {t("messages.noReportData")}
                                  </Typography>
                                ) : isPercentageMetric ? (
                                  <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                    {percentageValue?.toLocaleString('pt-BR', {
                                      minimumFractionDigits: 1,
                                      maximumFractionDigits: 1,
                                    })}
                                    %
                                  </Typography>
                                ) : (
                                  <CurrencyDisplay
                                    value={
                                      typeof rawValue === 'number'
                                        ? rawValue
                                        : Number(rawValue)
                                    }
                                    currency="BRL"
                                    locale="pt-BR"
                                    format="large"
                                    variant="body2"
                                  />
                                )}
                              </Box>
                            </Box>
                          </AccordionSummary>
                          <AccordionDetails sx={{ px: 0, pb: 2 }}>
                            <Box sx={{ mt: 1 }}>
                              {account.group && (
                                <Typography
                                  variant="caption"
                                  color="text.secondary"
                                  sx={{ mb: 2, display: 'block' }}
                                >
                                  Grupo: {account.group}
                                </Typography>
                              )}

                              {account.valuesByQuarter && account.valuesByQuarter.length > 0 ? (
                                <LineChart
                                  data={
                                    account.id === "INDICE_DE_BASILEIA" ||
                                    account.id === "INDICE_DE_IMOBILIZACAO"
                                      ? account.valuesByQuarter.map((item: any) => ({
                                          ...item,
                                          value:
                                            item.value === null ||
                                            item.value === undefined
                                              ? item.value
                                              : Number(item.value) * 100,
                                        }))
                                      : account.valuesByQuarter
                                  }
                                  xKey="quarter"
                                  xDateFormat={(value: any) => {
                                    const str = String(value);
                                    if (str.length !== 6) {
                                      return str;
                                    }
                                    const year = str.slice(0, 4);
                                    const month = str.slice(4, 6);
                                    return `${month}/${year}`;
                                  }}
                                  height={300}
                                  series={[
                                    {
                                      id: 'value',
                                      name: account.name || t("value") || "Valor",
                                      dataKey: 'value',
                                      yAxisId: 'left-axis',
                                    },
                                  ]}
                                  axes={[
                                    {
                                      id: 'left-axis',
                                      side: 'left',
                                      label: account.name,
                                      format:
                                        account.name
                                          ?.toLowerCase()
                                          .includes("basileia") ||
                                        account.name
                                          ?.toLowerCase()
                                          .includes("índice")
                                          ? "percent"
                                          : "currency",
                                      currency: 'BRL',
                                    },
                                  ]}
                                  scale
                                  scaleMinPadding={0.05}
                                  scaleMaxPadding={0.15}
                                  consolidateDecimal
                                  showArea
                                />
                              ) : (
                                <Typography variant="body2" color="text.secondary">
                                  {t("messages.noReportData")}
                                </Typography>
                              )}
                            </Box>
                          </AccordionDetails>
                        </Accordion>
                      );
                    })}
                  </Stack>
                </Paper>
              </Box>
            ) : (
              <Typography color="text.secondary">
                {t("messages.noReportData")}
              </Typography>
            )}
          </Box>
        ) : (
          <Typography color="text.secondary">
            {t("messages.selectReport")}
          </Typography>
        )}
      </Page>
    </SidebarLayout>
  );
}
