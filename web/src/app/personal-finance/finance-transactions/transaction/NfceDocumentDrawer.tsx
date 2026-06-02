'use client';

import { useCallback, useMemo } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTranslation as useReactI18nTranslation } from 'react-i18next';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  IconButton,
  Link,
  List,
  ListItem,
  Stack,
  Tooltip,
  Typography,
  alpha,
} from '@mui/material';
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined';
import OpenInNewOutlinedIcon from '@mui/icons-material/OpenInNewOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import StoreIcon from '@mui/icons-material/Store';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display/CurrencyAmountParts';
import { InfoField } from '@/shared/components/ui/data-display';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { formatDateTime } from '@/shared/utils/date';
import { formatCnpj } from '@/lib/utils/formatCnpj';
import { TaxLoadSummary, TaxPercentBar } from './TaxLoadSummary';
import {
  NfceDocumentDocument,
  type NfceDocumentQuery,
  type NfceDocumentQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';

export interface NfceDocumentDrawerProps {
  open: boolean;
  onClose: () => void;
  transactionId: string | null;
}

function formatAccessKey(value: string): string {
  const digits = value.replace(/\D+/g, '');
  return digits.replace(/(.{4})/g, '$1 ').trim();
}

function formatCpf(value: string | null | undefined): string | null {
  const digits = String(value ?? '').replace(/\D+/g, '');
  if (digits.length !== 11) return value ?? null;
  return `${digits.slice(0, 3)}.${digits.slice(3, 6)}.${digits.slice(6, 9)}-${digits.slice(9, 11)}`;
}

function formatCep(value: string | null | undefined): string | null {
  const digits = String(value ?? '').replace(/\D+/g, '');
  if (digits.length !== 8) return value ?? null;
  return `${digits.slice(0, 5)}-${digits.slice(5, 8)}`;
}

function formatNumber(value: number | null | undefined, locale: string): string {
  if (value == null) return '-';
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: value % 1 === 0 ? 0 : 2,
    maximumFractionDigits: 4,
  }).format(value);
}

function formatUnit(value: string | null | undefined): string {
  const normalized = String(value ?? '').trim().toUpperCase();
  if (normalized === 'CDA') return 'UN';
  return normalized || '-';
}

export function NfceDocumentDrawer({
  open,
  onClose,
  transactionId,
}: NfceDocumentDrawerProps) {
  const { t, tCommon } = useTranslation(personalFinanceTranslations);
  const { i18n } = useReactI18nTranslation();
  const toast = useToast();
  const currencyFormatter = useMemo(
    () =>
      new Intl.NumberFormat(i18n.language, {
        style: 'currency',
        currency: 'BRL',
      }),
    [i18n.language],
  );
  const formatCurrency = useCallback(
    (value: number | null | undefined) => currencyFormatter.format(Number(value ?? 0)),
    [currencyFormatter],
  );

  const { data, loading, error } = useQuery<NfceDocumentQuery, NfceDocumentQueryVariables>(
    NfceDocumentDocument,
    {
      variables: { transactionId: transactionId ?? '' },
      skip: !open || !transactionId,
      fetchPolicy: 'cache-and-network',
    },
  );

  const document = data?.nfceDocument ?? null;
  const items = useMemo(() => document?.items ?? [], [document?.items]);
  const issuerDisplayName = document?.issuerTradeName?.trim() || document?.issuerName || '-';
  const formattedPostalCode = formatCep(document?.issuerPostalCode);
  const issuerAddress = [document?.issuerAddress, formattedPostalCode ? `CEP ${formattedPostalCode}` : null]
    .filter(Boolean)
    .join(' • ');
  const totalTaxValue = useMemo(() => {
    if (document?.totalTax != null) return document.totalTax;
    const sum = items.reduce((acc, item) => acc + Number(item.taxAmount ?? 0), 0);
    return sum > 0 ? sum : null;
  }, [document?.totalTax, items]);
  const totalTaxPercent = useMemo(() => {
    if (totalTaxValue == null || !document?.totalValue) return null;
    return Math.max(0, Math.min(100, (totalTaxValue / document.totalValue) * 100));
  }, [document, totalTaxValue]);

  const handleCopyAccessKey = useCallback(async () => {
    if (!document?.accessKey) return;

    try {
      await navigator.clipboard.writeText(document.accessKey);
      toast.success(t('financeTransactions.nfceDocument.accessKeyCopied'));
    } catch (err) {
      toast.error(extractErrorMessage(err, tCommon('errors.default')));
    }
  }, [document, t, tCommon, toast]);

  if (!open) return null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="lg" ariaLabel={t('financeTransactions.nfceDocument.drawerTitle')}>
      <Drawer.Header
        title={t('financeTransactions.nfceDocument.drawerTitle')}
        showCloseButton
        closeButtonAriaLabel={t('financeTransactions.actions.close')}
      />
      <Drawer.Body>
        {loading ? (
          <Stack spacing={2} alignItems="center" justifyContent="center" sx={{ minHeight: 240, p: 3 }}>
            <CircularProgress />
            <Typography color="text.secondary">{tCommon('loading')}</Typography>
          </Stack>
        ) : error ? (
          <Box sx={{ p: 2 }}>
            <Alert severity="error">{extractErrorMessage(error, tCommon('errors.default'))}</Alert>
          </Box>
        ) : !document ? (
          <Box sx={{ p: 2 }}>
            <Alert severity="info">{t('financeTransactions.nfceDocument.noDocument')}</Alert>
          </Box>
        ) : (
          <Stack spacing={2}>
            <Stack sx={{ gap: 1 }}>
              <Label sx={{ px: 2, mb: 0 }} icon={<StoreIcon fontSize="small" />}>
                {t('financeTransactions.nfceDocument.issuerSection')}
              </Label>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={2.25}>
                    <Typography
                      variant="h6"
                      sx={{
                        fontWeight: 700,
                        letterSpacing: 0.5,
                        textTransform: 'uppercase',
                      }}
                    >
                      {issuerDisplayName}
                    </Typography>

                    <Stack
                      direction={{ xs: 'column', sm: 'row' }}
                      spacing={{ xs: 2, sm: 6 }}
                      sx={{ justifyContent: 'space-between' }}
                    >
                      <Stack spacing={0.5} sx={{ minWidth: 0, flex: 1 }}>
                        <Label sx={{ mb: 0 }}>{t('financeTransactions.nfceDocument.legalName')}</Label>
                        <Typography variant="body2" color="text.secondary" sx={{ wordBreak: 'break-word' }}>
                          {document.issuerName}
                        </Typography>
                      </Stack>
                      <Stack spacing={0.5} sx={{ minWidth: 0, flex: 1 }}>
                        <Label sx={{ mb: 0 }}>{t('financeTransactions.nfceDocument.cnpj')}</Label>
                        <Typography variant="body2" color="text.secondary">
                          {formatCnpj(document.issuerCnpj)}
                        </Typography>
                      </Stack>
                    </Stack>

                    <Stack spacing={0.5}>
                      <Label sx={{ mb: 0 }}>{t('financeTransactions.nfceDocument.address')}</Label>
                      <Typography variant="body2" color="text.secondary">
                        {issuerAddress || [document.municipality, document.state, document.countryCode].filter(Boolean).join(' • ')}
                      </Typography>
                    </Stack>

                  </Stack>
                </CardContent>
              </Card>
            </Stack>


          <Stack sx={{ gap: 1 }}>
            <Stack direction="row" alignItems="center" sx={{ px: 2 }}>
              <Label sx={{ mb: 0, mt: 2 }} icon={<ShoppingCartIcon fontSize="small" />}>
                {t('financeTransactions.nfceDocument.itemsSection')}
              </Label>
              <Label sx={{ ml: 'auto', mb: -3 }}>
                {t('financeTransactions.nfceDocument.itemsCount', { count: items.length })}
              </Label>
            </Stack>
            <Box
              sx={{
                border: '1px solid',
                borderColor: 'divider',
                borderRadius: 1.5,
                overflow: 'hidden',
                bgcolor: 'background.paper',
              }}
            >
              <List disablePadding>
                {items.map((item, index) => {
                  const taxPercent =
                    item.taxAmount != null && item.totalPrice > 0
                      ? Math.max(0, Math.min(100, (item.taxAmount / item.totalPrice) * 100))
                      : null;

                  return (
                    <ListItem
                      key={item.id}
                      disablePadding
                      sx={{
                        display: 'block',
                        px: 3,
                        py: 2.5,
                        borderBottom:
                          index < items.length - 1 ? '1px solid' : 'none',
                        borderBottomColor: 'divider',
                      }}
                    >
                      <Stack spacing={1}>
                        <Stack direction="row" alignItems="flex-start">
                          <Typography
                            variant="subtitle2"
                            sx={{ fontWeight: 700, textTransform: 'uppercase', flex: 1, pr: 2 }}
                          >
                            {item.rawDescription}
                          </Typography>
                          <CurrencyAmountParts
                            value={item.totalPrice}
                            currency="BRL"
                            locale={i18n.language}
                            variant="subtitle1"
                            sx={{ fontWeight: 700, whiteSpace: 'nowrap', fontSize: '1.8em', lineHeight: 1 }}
                          />
                        </Stack>

                        <Box
                          sx={{
                            display: { xs: 'block', sm: 'grid' },
                            gridTemplateColumns: { sm: 'auto auto 160px 1fr' },
                            columnGap: 4,
                            rowGap: 2,
                            alignItems: 'flex-start',
                          }}
                        >
                          <InfoField
                            label={t('financeTransactions.nfceDocument.quantity')}
                            value={`${formatNumber(item.quantity, i18n.language)} ${formatUnit(item.unit)}`}
                            sx={{ gridColumn: { sm: '1' } }}
                          />
                          <InfoField
                            label={t('financeTransactions.nfceDocument.unitPrice')}
                            value={formatCurrency(item.unitPrice)}
                            sx={{ gridColumn: { sm: '2' } }}
                          />
                          <InfoField
                            label={t('financeTransactions.nfceDocument.totalTax')}
                            value={item.taxAmount != null ? formatCurrency(item.taxAmount) : '—'}
                            footer={taxPercent != null ? <TaxPercentBar percent={taxPercent} /> : null}
                            sx={{ gridColumn: { sm: '3' }, minWidth: { xs: 160, sm: 'unset' } }}
                          />
                        </Box>
                      </Stack>
                    </ListItem>
                  );
                })}
              </List>
            </Box>

          </Stack>

            <Stack sx={{ gap: 1 }}>
              <Stack direction="row" alignItems="center" sx={{ px: 2, mt: 2 }}>
                <Label sx={{ mb: 0 }} icon={<ReceiptLongOutlinedIcon fontSize="small" />}>
                  {t('financeTransactions.nfceDocument.totalsSection')}
                </Label>
              </Stack>
              <Card variant="outlined">
                <CardContent>
                  <Stack spacing={4}>
                    <Box
                      sx={{
                        display: { xs: 'block', sm: 'grid' },
                        gridTemplateColumns: { sm: 'auto auto 160px 1fr' },
                        columnGap: 4,
                        rowGap: 2,
                        alignItems: 'flex-start',
                      }}
                    >
                      <InfoField
                        label={t('financeTransactions.nfceDocument.emissionDate')}
                        value={formatDateTime(
                          String(document.emissionDate),
                          i18n.language,
                          Intl.DateTimeFormat().resolvedOptions().timeZone,
                        )}
                        sx={{ gridColumn: { sm: '1', minWidth: 188 } }}
                      />
                      {totalTaxValue != null ? (
                        <Box sx={{ gridColumn: { sm: '3' } }}>
                          <TaxLoadSummary
                            percent={totalTaxPercent}
                            amount={totalTaxValue}
                            label={t('financeTransactions.nfceDocument.taxLoadLabel')}
                          />
                        </Box>
                      ) : null}
                      <CurrencyAmountParts
                        value={document.totalValue}
                        currency="BRL"
                        locale={i18n.language}
                        variant="subtitle1"
                        sx={{
                          gridColumn: { sm: '4' },
                          justifySelf: { sm: 'end' },
                          ml: { xs: 'auto', sm: 0 },
                          fontWeight: 800,
                          whiteSpace: 'nowrap',
                          fontSize: '2.2em',
                          lineHeight: 1,
                        }}
                      />
                    </Box>
                    {document.buyerCpf ? (
                      <InfoField
                        label={t('financeTransactions.nfceDocument.buyerCpf')}
                        value={formatCpf(document.buyerCpf)}
                      />
                    ) : null}
                    <Stack spacing={1}>
                      <Label sx={{ mb: 0 }}>{t('financeTransactions.nfceDocument.accessKey')}</Label>
                      <Box
                        sx={(theme) => ({
                          display: 'flex',
                          alignItems: 'center',
                          gap: 1,
                          px: 2,
                          py: 1.5,
                          borderRadius: 1,
                          bgcolor: alpha(theme.palette.text.primary, 0.02),
                        })}
                      >
                        <Typography
                          variant="body2"
                          title={formatAccessKey(document.accessKey)}
                          sx={{
                            flex: 1,
                            minWidth: 0,
                            fontVariantNumeric: 'tabular-nums',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {formatAccessKey(document.accessKey)}
                        </Typography>
                        <Tooltip title={t('financeTransactions.nfceDocument.copyAccessKey')}>
                          <IconButton
                            size="small"
                            onClick={handleCopyAccessKey}
                            aria-label={t('financeTransactions.nfceDocument.copyAccessKey')}
                          >
                            <ContentCopyOutlinedIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        {document.sourceUrl ? (
                          <>
                            <Divider orientation="vertical" flexItem />
                            <Link
                              href={document.sourceUrl ?? undefined}
                              target="_blank"
                              rel="noreferrer"
                              underline="hover"
                              sx={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: 0.5,
                                fontWeight: 500,
                                whiteSpace: 'nowrap',
                              }}
                            >
                              {t('financeTransactions.nfceDocument.viewOnSefaz')}
                              <OpenInNewOutlinedIcon fontSize="small" />
                            </Link>
                          </>
                        ) : null}
                      </Box>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Stack>
          </Stack>
        )}
      </Drawer.Body>
    </Drawer>
  );
}
