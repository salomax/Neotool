import * as React from "react";
import { useQuery } from "@apollo/client/react";
import {
  BACEN_INSTITUTION,
  BACEN_INSTITUTION_REPORT_DETAILS,
  BACEN_INSTITUTION_REPORTS,
} from "@/lib/graphql/operations/financial_data/bacen-institutions";
import { safeDecodeId } from "@/shared/utils/idObfuscation";

export function useBacenInstitutionDetail(encodedId: string | null) {
  const bacenCodInst = React.useMemo(() => {
    if (!encodedId) {
      return null;
    }
    const decoded = safeDecodeId(encodedId);
    if (!decoded) {
      return null;
    }
    return decoded;
  }, [encodedId]);

  const [selectedReportId, setSelectedReportId] = React.useState<string | null>(
    null
  );

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

  const { data: reportDetailsData, loading: loadingReport } = useQuery(
    BACEN_INSTITUTION_REPORT_DETAILS,
    {
      variables: {
        id: bacenCodInst,
        reportId: selectedReportId,
        range: { last: 5 * 4 },
      },
      skip: !bacenCodInst || !selectedReportId,
    }
  );

  const institution = (data as any)?.bacenFinanceInstitution;
  const reportDetails = (reportDetailsData as any)?.bacenFinanceInstitution
    ?.reportDetails;

  const reports = React.useMemo(() => {
    const quarters = (reportsData as any)?.bacenFinanceInstitution?.reportsSummary
      ?.quarters;
    if (!quarters || quarters.length === 0) return [];

    const quarterReports = quarters[0].reports || [];

    return [...quarterReports].sort(
      (a: any, b: any) => parseInt(a.reportId) - parseInt(b.reportId)
    );
  }, [reportsData]);

  React.useEffect(() => {
    if (reports.length > 0 && !selectedReportId) {
      setSelectedReportId(reports[0].reportId);
    }
  }, [reports, selectedReportId]);

  return {
    bacenCodInst,
    institution,
    loadingInstitution: loading,
    errorInstitution: error,
    reports,
    selectedReportId,
    setSelectedReportId,
    reportDetails,
    loadingReport,
  };
}
