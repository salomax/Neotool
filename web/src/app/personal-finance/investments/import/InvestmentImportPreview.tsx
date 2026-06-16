'use client';

import React from 'react';
import { useQuery } from '@apollo/client/react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  alpha,
} from '@mui/material';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { FinancialAccountType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

export type InvestmentConflictResolution = 'UPDATE' | 'KEEP_EXISTING';

export interface ConfirmInvestmentImportInput {
  jobId: string;
  institutionMappings: Array<{ reviewInstitutionId: string; financialAccountId: string }>;
  rowPatches: Array<{
    rowIndex: number;
    principal?: number;
    conflictResolution?: InvestmentConflictResolution;
    skip?: boolean;
  }>;
}

interface InvestmentImportConflictDiff {
  existingQuantity?: number | null;
  fileQuantity?: number | null;
  existingCurrentValue?: number | null;
  fileCurrentValue?: number | null;
  existingMaturityDate?: string | null;
  fileMaturityDate?: string | null;
}

interface InvestmentImportReviewRow {
  rowIndex: number;
  productName: string;
  assetTypeCode: string;
  issuerName?: string | null;
  providerCode?: string | null;
  indexerLabel?: string | null;
  issueDate?: string | null;
  maturityDate?: string | null;
  quantity?: number | null;
  principal?: number | null;
  currentValue?: number | null;
  requiresPrincipal: boolean;
  conflictingHoldingId?: string | null;
  conflictDiff?: InvestmentImportConflictDiff | null;
}

interface InvestmentImportAccountCandidate {
  financialAccountId: string;
  accountName: string;
}

interface InvestmentImportReviewInstitution {
  reviewInstitutionId: string;
  institutionName: string;
  matchedFinancialAccountId?: string | null;
  matchAmbiguous: boolean;
  matchCandidates: InvestmentImportAccountCandidate[];
  rows: InvestmentImportReviewRow[];
}

export interface InvestmentImportReview {
  source?: string | null;
  institutions: InvestmentImportReviewInstitution[];
}

interface InvestmentImportPreviewProps {
  jobId: string;
  review: InvestmentImportReview;
  onConfirm: (input: ConfirmInvestmentImportInput, rowCount: number) => void;
  confirmLoading: boolean;
  confirmError?: string | null;
}

interface FinancialAccountsResult {
  financialAccounts: Array<{
    id: string;
    name: string;
    type: FinancialAccountType;
  }>;
}

function parseBrazilianMoney(value: string): number | undefined {
  const cleaned = value.replace(/R\$/g, '').replace(/\s/g, '').replace(/\./g, '').replace(',', '.').trim();
  if (!cleaned) return undefined;
  const parsed = Number(cleaned);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function formatMoney(value?: number | null): string {
  if (value == null) return '-';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
}

function formatDate(value?: string | null): string {
  if (!value) return '-';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleDateString('pt-BR', { timeZone: 'UTC' });
}

export function InvestmentImportPreview({
  jobId,
  review,
  onConfirm,
  confirmLoading,
  confirmError,
}: InvestmentImportPreviewProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { data: accountsData } = useQuery<FinancialAccountsResult>(FINANCIAL_ACCOUNTS, {
    fetchPolicy: 'cache-first',
  });

  const investmentAccounts = React.useMemo(
    () =>
      (accountsData?.financialAccounts ?? []).filter(
        (account) => account.type === FinancialAccountType.Investment,
      ),
    [accountsData?.financialAccounts],
  );

  const [accountMappings, setAccountMappings] = React.useState<Record<string, string>>(() =>
    Object.fromEntries(
      review.institutions
        .filter((institution) => !institution.matchAmbiguous && institution.matchedFinancialAccountId)
        .map((institution) => [institution.reviewInstitutionId, institution.matchedFinancialAccountId as string]),
    ),
  );
  const [principalInputs, setPrincipalInputs] = React.useState<Record<number, string>>({});
  const [conflictResolutions, setConflictResolutions] = React.useState<Record<number, InvestmentConflictResolution>>({});
  const [skippedRows, setSkippedRows] = React.useState<Set<number>>(new Set());

  React.useEffect(() => {
    setAccountMappings(
      Object.fromEntries(
        review.institutions
          .filter((institution) => !institution.matchAmbiguous && institution.matchedFinancialAccountId)
          .map((institution) => [institution.reviewInstitutionId, institution.matchedFinancialAccountId as string]),
      ),
    );
    setPrincipalInputs({});
    setConflictResolutions({});
    setSkippedRows(new Set());
  }, [review]);

  const rows = React.useMemo(() => review.institutions.flatMap((institution) => institution.rows), [review.institutions]);
  const activeRows = React.useMemo(
    () => rows.filter((row) => !skippedRows.has(row.rowIndex)),
    [rows, skippedRows],
  );

  const missingAccount = review.institutions.some((institution) => !accountMappings[institution.reviewInstitutionId]);
  const missingPrincipalCount = activeRows.filter((row) => row.requiresPrincipal && !principalInputs[row.rowIndex]?.trim()).length;
  const unresolvedConflictCount = activeRows.filter(
    (row) => !!row.conflictingHoldingId && !conflictResolutions[row.rowIndex],
  ).length;

  const canConfirm = !missingAccount && missingPrincipalCount === 0 && unresolvedConflictCount === 0;

  const handleToggleSkip = React.useCallback((rowIndex: number, checked: boolean) => {
    setSkippedRows((prev) => {
      const next = new Set(prev);
      if (checked) next.add(rowIndex);
      else next.delete(rowIndex);
      return next;
    });
  }, []);

  const handleConfirm = React.useCallback(() => {
    const input: ConfirmInvestmentImportInput = {
      jobId,
      institutionMappings: review.institutions.map((institution) => ({
        reviewInstitutionId: institution.reviewInstitutionId,
        financialAccountId: accountMappings[institution.reviewInstitutionId]!,
      })),
      rowPatches: review.institutions.flatMap((institution) =>
        institution.rows.map((row) => ({
          rowIndex: row.rowIndex,
          principal: principalInputs[row.rowIndex]
            ? parseBrazilianMoney(principalInputs[row.rowIndex] ?? '')
            : undefined,
          conflictResolution: conflictResolutions[row.rowIndex],
          skip: skippedRows.has(row.rowIndex),
        })),
      ),
    };

    onConfirm(input, activeRows.length);
  }, [
    accountMappings,
    activeRows.length,
    conflictResolutions,
    jobId,
    onConfirm,
    principalInputs,
    review.institutions,
    skippedRows,
  ]);

  return (
    <Stack spacing={2} sx={{ p: 1 }}>
      {confirmError ? <Alert severity="error">{confirmError}</Alert> : null}

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" sx={{ mb: 1.5 }}>
          {t('investments.import.drawer.accountSectionTitle')}
        </Typography>
        <Stack spacing={1.5}>
          {review.institutions.map((institution) => {
            const selectableAccounts =
              institution.matchAmbiguous && institution.matchCandidates.length > 0
                ? institution.matchCandidates.map((candidate) => ({ id: candidate.financialAccountId, name: candidate.accountName }))
                : investmentAccounts.map((account) => ({ id: account.id, name: account.name }));

            return (
              <Stack
                key={institution.reviewInstitutionId}
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1.5}
                alignItems={{ xs: 'stretch', sm: 'center' }}
              >
                <Typography sx={{ minWidth: 220, fontWeight: 600 }}>{institution.institutionName}</Typography>
                <TextField
                  select
                  fullWidth
                  size="medium"
                  value={accountMappings[institution.reviewInstitutionId] ?? ''}
                  onChange={(event) =>
                    setAccountMappings((prev) => ({
                      ...prev,
                      [institution.reviewInstitutionId]: event.target.value,
                    }))
                  }
                  error={!accountMappings[institution.reviewInstitutionId]}
                  helperText={
                    institution.matchAmbiguous
                      ? t('investments.import.drawer.accountAmbiguousHint')
                      : undefined
                  }
                >
                  {selectableAccounts.map((account) => (
                    <MenuItem key={account.id} value={account.id}>
                      {account.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
            );
          })}
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('investments.import.drawer.columns.status')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.product')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.indexer')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.issueDate')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.maturityDate')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.principal')}</TableCell>
              <TableCell>{t('investments.import.drawer.columns.skip')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {review.institutions.map((institution) =>
              institution.rows.map((row) => {
                const skipped = skippedRows.has(row.rowIndex);
                const unresolvedPrincipal = row.requiresPrincipal && !principalInputs[row.rowIndex]?.trim() && !skipped;
                const unresolvedConflict = !!row.conflictingHoldingId && !conflictResolutions[row.rowIndex] && !skipped;

                const statusIcon = unresolvedPrincipal ? '⚠' : unresolvedConflict ? '🔄' : '✓';

                return (
                  <React.Fragment key={`${institution.reviewInstitutionId}-${row.rowIndex}`}>
                    <TableRow hover>
                      <TableCell>{statusIcon}</TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>{row.productName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {institution.institutionName}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell>{row.indexerLabel ?? '-'}</TableCell>
                      <TableCell>{formatDate(row.issueDate)}</TableCell>
                      <TableCell>{formatDate(row.maturityDate)}</TableCell>
                      <TableCell sx={{ minWidth: 160 }}>
                        {row.requiresPrincipal ? (
                          <BaseMoneyInput
                            value={parseBrazilianMoney(principalInputs[row.rowIndex] ?? '')}
                            onChange={(value) =>
                              setPrincipalInputs((prev) => ({
                                ...prev,
                                [row.rowIndex]:
                                  typeof value === 'number' && Number.isFinite(value)
                                    ? value.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
                                    : '',
                              }))
                            }
                            variant="standard"
                            size="medium"
                            InputProps={{
                              disableUnderline: true,
                              sx: {
                                borderBottom: (theme) => `1px solid ${theme.palette.divider}`,
                                '& input': { textAlign: 'right' },
                              },
                            }}
                          />
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell>
                        <TextField
                          select
                          size="small"
                          value={skipped ? 'yes' : 'no'}
                          onChange={(event) => handleToggleSkip(row.rowIndex, event.target.value === 'yes')}
                        >
                          <MenuItem value="no">Não</MenuItem>
                          <MenuItem value="yes">Sim</MenuItem>
                        </TextField>
                      </TableCell>
                    </TableRow>
                    {row.conflictDiff && !skipped ? (
                      <TableRow>
                        <TableCell colSpan={7} sx={{ bgcolor: (theme) => alpha(theme.palette.warning.main, 0.06) }}>
                          <Stack spacing={1}>
                            <Typography variant="body2" sx={{ fontWeight: 700 }}>
                              {t('investments.import.drawer.conflictTitle')}
                            </Typography>
                            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                              <Chip
                                size="small"
                                label={`${t('investments.import.drawer.conflictQuantity')}: ${row.conflictDiff.existingQuantity ?? '-'} → ${row.conflictDiff.fileQuantity ?? '-'}`}
                                color={row.conflictDiff.existingQuantity !== row.conflictDiff.fileQuantity ? 'warning' : 'default'}
                              />
                              <Chip
                                size="small"
                                label={`${t('investments.import.drawer.conflictCurrentValue')}: ${formatMoney(row.conflictDiff.existingCurrentValue)} → ${formatMoney(row.conflictDiff.fileCurrentValue)}`}
                                color={row.conflictDiff.existingCurrentValue !== row.conflictDiff.fileCurrentValue ? 'warning' : 'default'}
                              />
                              <Chip
                                size="small"
                                label={`${t('investments.import.drawer.conflictMaturity')}: ${formatDate(row.conflictDiff.existingMaturityDate)} → ${formatDate(row.conflictDiff.fileMaturityDate)}`}
                                color={row.conflictDiff.existingMaturityDate !== row.conflictDiff.fileMaturityDate ? 'warning' : 'default'}
                              />
                            </Stack>
                            <Stack direction="row" spacing={1}>
                              <Button
                                size="small"
                                variant={conflictResolutions[row.rowIndex] === 'UPDATE' ? 'contained' : 'outlined'}
                                onClick={() =>
                                  setConflictResolutions((prev) => ({
                                    ...prev,
                                    [row.rowIndex]: 'UPDATE',
                                  }))
                                }
                              >
                                {t('investments.import.drawer.conflictUpdate')}
                              </Button>
                              <Button
                                size="small"
                                variant={conflictResolutions[row.rowIndex] === 'KEEP_EXISTING' ? 'contained' : 'outlined'}
                                onClick={() =>
                                  setConflictResolutions((prev) => ({
                                    ...prev,
                                    [row.rowIndex]: 'KEEP_EXISTING',
                                  }))
                                }
                              >
                                {t('investments.import.drawer.conflictKeep')}
                              </Button>
                            </Stack>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ) : null}
                  </React.Fragment>
                );
              }),
            )}
          </TableBody>
        </Table>
      </Paper>

      <Divider />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} alignItems={{ xs: 'stretch', sm: 'center' }}>
        <Chip color={missingPrincipalCount > 0 ? 'warning' : 'success'} label={`${missingPrincipalCount} ${t('investments.import.drawer.missingPrincipal')}`} />
        <Chip color={unresolvedConflictCount > 0 ? 'warning' : 'success'} label={`${unresolvedConflictCount} ${t('investments.import.drawer.unresolvedConflicts')}`} />
        <Box sx={{ flex: 1 }} />
        <Button
          variant="contained"
          disabled={!canConfirm || confirmLoading}
          onClick={handleConfirm}
        >
          {confirmLoading
            ? t('investments.import.drawer.confirming')
            : t('investments.import.drawer.confirmPositions', { count: activeRows.length })}
        </Button>
      </Stack>
    </Stack>
  );
}
