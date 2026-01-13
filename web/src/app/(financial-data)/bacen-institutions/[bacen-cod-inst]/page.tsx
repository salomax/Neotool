"use client";

import * as React from "react";
import { useQuery } from "@apollo/client/react";
import { useParams } from "next/navigation";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { Page } from "@/shared/components/ui/layout";
import { Box, Typography, Avatar, Stack, List, ListItemButton, ListItemText, CircularProgress, Divider } from "@mui/material";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import { BACEN_INSTITUTION, BACEN_INSTITUTION_REPORTS, BACEN_INSTITUTION_REPORT_DETAILS } from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/features/financial_data/i18n";
import { safeDecodeId } from "@/shared/utils/idObfuscation";
import { useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { CurrencyDisplay } from "@/shared/components/ui/data-display";
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
      anoMes: null,
      range: { last: 1 },
    },
    skip: !bacenCodInst,
  });

  const { data: reportDetailsData, loading: loadingReport } = useQuery(BACEN_INSTITUTION_REPORT_DETAILS, {
    variables: {
      id: bacenCodInst,
      reportId: selectedReportId,
      range: { last: 1 },
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
                <ListItemText primary={report.name} />
              </ListItemButton>
            ))}
          </List>
        </Box>
      }
    >
      <Page>
        <Box sx={{ mb: 4 }}>
          <BacenInstitutionSummaryPaper institution={institution} />
          <BacenInstitutionBalanceSummaryGrid institutionId={institution.id} />
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
              <Stack spacing={4}>
                {reportDetails.accounts.map((account: any, accountIndex: number) => (
                  <Box key={accountIndex} sx={{ p: 3, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
                    <Box sx={{ mb: 2 }}>
                      {account.accountNumber && (
                        <Typography variant="h6" sx={{ mb: 0.5 }}>
                          {account.accountNumber}
                        </Typography>
                      )}
                      {account.columnName && (
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                          {account.columnName}
                        </Typography>
                      )}
                      {account.group && (
                        <Typography variant="caption" color="text.secondary">
                          Grupo: {account.group}
                        </Typography>
                      )}
                    </Box>
                    
                    {account.valuesByQuarter && account.valuesByQuarter.length > 0 ? (
                      <Stack spacing={2}>
                        {account.valuesByQuarter.map((val: any) => (
                          <Box key={val.quarter} sx={{ p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
                            <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
                              {val.quarter}
                            </Typography>
                            <Typography variant="h6">
                              <CurrencyDisplay
                                value={val.value}
                                currency="BRL"
                                locale="pt-BR"
                              />
                            </Typography>
                          </Box>
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        {t("messages.noReportData")}
                      </Typography>
                    )}
                  </Box>
                ))}
              </Stack>
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
