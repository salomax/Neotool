'use client';

import React, { useState, useRef, useCallback } from 'react';
import {
  Box,
  Typography,
  Stack,
  Button,
  Alert,
  LinearProgress,
  CircularProgress,
  Divider,
  Paper,
  IconButton,
  List,
  ListItem,
  ListItemText,
  ListItemAvatar,
  Avatar,
  Chip,
  Checkbox,
  Select,
  MenuItem as SelectMenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
  alpha,
  Autocomplete,
  TextField,
} from '@mui/material';
import {
  CloudUpload as UploadIcon,
  CreditCard as CardIcon,
  CheckCircle as CheckIcon,
  InsertDriveFile as FileIcon,
  HelpOutline as UnmatchedIcon,
  Add as AddIcon,
  SwapHoriz as TransferIcon,
  DeleteOutline as DeleteIcon,
  AccountBalance as BankIcon,
} from '@mui/icons-material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useMutation, useQuery } from '@apollo/client/react';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { SUBMIT_BILL_IMPORT, CONFIRM_BILL_IMPORT, CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { BILL_IMPORT_JOB, FINANCIAL_ACCOUNTS, ISSUERS, TRANSACTION_CATEGORIES } from '@/lib/graphql/operations/banking/queries';
import { CategorySource, FinancialAccount, Issuer, type TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';
import { CreateFinancialAccountDrawer } from './CreateFinancialAccountDrawer';
import { extractErrorCode, extractErrorMessage } from '@/shared/utils/error';
import { NumericFormat } from 'react-number-format';
import dayjs from 'dayjs';
import { DateTimePicker } from '@/shared/components/ui/primitives/DateTimePicker';
import { CategorySelect } from './CategorySelect';

const MAX_FILE_BYTES = 3 * 1024 * 1024; // 3 MB
const ACCEPTED_EXTENSIONS = ['.ofx', '.csv', '.xlsx', '.xls', '.pdf'];
const ACCEPTED_TYPES =
  'application/x-ofx,application/ofx,.ofx,text/csv,.csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,.xlsx,application/vnd.ms-excel,.xls,application/pdf,.pdf';
const POLL_INTERVAL_MS = 2000;
const DEFAULT_ACCOUNT_COLOR = '#1976d2';

const CardSubtypeValues = [
  'CREDIT_CARD',
  'CHARGE_CARD',
  'PREPAID_CARD',
  'VOUCHER_CARD',
] as const;

const CardNetworkValues = [
  'VISA',
  'MASTERCARD',
  'ELO',
  'AMERICAN_EXPRESS',
  'HIPERCARD',
  'DINERS_CLUB',
  'BANDEIRA_PROPRIA',
  'OUTRAS',
] as const;

const CardLevelValues = [
  'STANDARD',
  'GOLD',
  'PLATINUM',
  'INFINITE',
  'BLACK',
] as const;

type BillStep = 'upload' | 'processing' | 'card-matching' | 'tx-review' | 'result' | 'error';

/** Per-card resolution: which financialAccountId to associate with each extracted card. */
interface CardResolution {
  reviewCardId: string;
  financialAccountId: string | null;
}

/** Per-transaction selection state (include/exclude). Key: `${reviewCardId}-${txIndex}` */
type TxSelections = Record<string, boolean>;

type TxCategorySource = CategorySource | null;

type TxCategorySelections = Record<
  string,
  {
    categoryId: string | null;
    categorySource: TxCategorySource;
  }
>;

interface CardAccountOption {
  id: string;
  name: string;
  issuerId: string | null;
  issuerName: string | null;
  issuerKey: string | null;
  maskedNumber: string | null;
}

interface DraftCardResolution {
  draftCardId: string;
  lastFourDigits: string;
  financialAccountId: string | null;
  allowMissingLastFour: boolean;
}

type FlatTxAssignments = Record<number, string | null>;
type FlatTxSelections = Record<number, boolean>;
type FlatTxInclusions = Record<number, boolean>;

interface SharedCardTemplate {
  issuerId: string;
  issuerName: string;
  subtype: string;
  cardNetwork: string;
  cardLevel: string;
  closingDay: string;
  dueDay: string;
  creditLimit: string;
  descriptionBase: string;
}

interface BillDateOverrides {
  closingDate: string;
  dueDate: string;
}

interface DraftBucketSummary {
  draftCardId: string;
  label: string;
  count: number;
  total: number;
}

function toIssuerKey(issuerId?: string | null, issuerName?: string | null): string | null {
  if (issuerId) return `id:${issuerId}`;
  const normalizedName = issuerName?.trim().toLowerCase();
  return normalizedName ? `name:${normalizedName}` : null;
}

function getDayOfMonthFromDateInput(date: string | null | undefined): number | undefined {
  if (!date) return undefined;
  const dateOnlyMatch = /^\d{4}-\d{2}-(\d{2})$/.exec(date);
  if (dateOnlyMatch) {
    const day = Number(dateOnlyMatch[1]);
    return day >= 1 && day <= 31 ? day : undefined;
  }

  const parsed = new Date(date);
  if (Number.isNaN(parsed.getTime())) return undefined;
  const day = parsed.getDate();
  return day >= 1 && day <= 31 ? day : undefined;
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result as string;
      resolve(result.includes(',') ? result.split(',')[1]! : result);
    };
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });
}

function getExtension(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

async function detectEncryptedPdf(file: File): Promise<boolean> {
  // PDF encryption metadata (`/Encrypt`) typically appears near xref/trailer.
  // We scan the first/last chunks to keep client-side detection fast.
  const raw = new Uint8Array(await file.arrayBuffer());
  if (raw.length === 0) return false;

  const chunkSize = Math.min(raw.length, 256 * 1024);
  const decoder = new TextDecoder('latin1');
  const head = decoder.decode(raw.subarray(0, chunkSize));
  const tail = decoder.decode(raw.subarray(Math.max(0, raw.length - chunkSize)));

  return /\/Encrypt\b/.test(`${head}\n${tail}`);
}

function formatTxDisplayDate(date: string | null | undefined, locale?: string): string {
  if (!date) return '-';

  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  const dateObj = dateOnlyMatch
    ? new Date(Number(dateOnlyMatch[1]), Number(dateOnlyMatch[2]) - 1, Number(dateOnlyMatch[3]))
    : new Date(date);

  if (Number.isNaN(dateObj.getTime())) return date;

  return new Intl.DateTimeFormat(locale, { dateStyle: 'short' }).format(dateObj);
}

function getReviewCardLabel(
  card: { lastFourDigits?: string | null },
  index: number,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (card.lastFourDigits?.trim()) {
    return `•••• ${card.lastFourDigits}`;
  }
  return t('financeTransactions.billImport.cardMatchingStep.unnamedCard', { index: index + 1 });
}

function txSelectionKey(reviewCardId: string, txIndex: number): string {
  return `${reviewCardId}-${txIndex}`;
}

function resolveFriendlyImportErrorMessage(
  t: (key: string) => string,
  errorCode?: string | null,
  errorMessage?: string | null,
): string {
  const normalizedCode = (errorCode ?? '').toLowerCase();
  const normalizedMessage = (errorMessage ?? '').toLowerCase();

  if (normalizedCode === 'pdf_password_required' || normalizedMessage.includes('pdf_password_required')) {
    return t('financeTransactions.billImport.errorStep.passwordInvalid');
  }

  if (
    normalizedCode === 'assistant_pdf_reader_failed' ||
    normalizedMessage.includes('llm_provider_error') ||
    normalizedMessage.includes('high demand')
  ) {
    return t('financeTransactions.billImport.errorStep.processingUnavailable');
  }

  return t('financeTransactions.billImport.errorStep.generic');
}

function formatCardDescriptor(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function buildDraftCardName(
  template: SharedCardTemplate,
  issuerLabel: string,
  lastFourDigitsPlaceholder: string,
): string {
  const customBase = template.descriptionBase.trim();
  if (customBase) {
    return `${customBase} ${lastFourDigitsPlaceholder}`;
  }

  const parts = [
    issuerLabel.trim(),
    template.cardLevel ? formatCardDescriptor(template.cardLevel) : '',
    template.cardNetwork ? formatCardDescriptor(template.cardNetwork) : '',
    template.subtype && template.subtype !== 'CREDIT_CARD' ? formatCardDescriptor(template.subtype) : '',
  ].filter(Boolean);

  const baseName = parts.join(' ').trim() || 'Cartao';
  return `${baseName} ${lastFourDigitsPlaceholder}`;
}

export interface BillImportDrawerProps {
  open: boolean;
  onClose: () => void;
  onImportCompleted?: () => void;
}

export function BillImportDrawer({
  open,
  onClose,
  onImportCompleted,
}: BillImportDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [step, setStep] = useState<BillStep>('upload');
  const [file, setFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [isEncryptedPdf, setIsEncryptedPdf] = useState(false);
  const [pdfPassword, setPdfPassword] = useState('');
  const [jobId, setJobId] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [cardResolutions, setCardResolutions] = useState<CardResolution[]>([]);
  const [txSelections, setTxSelections] = useState<TxSelections>({});
  const [txCategorySelections, setTxCategorySelections] = useState<TxCategorySelections>({});
  const [draftCards, setDraftCards] = useState<DraftCardResolution[]>([]);
  const [flatTxAssignments, setFlatTxAssignments] = useState<FlatTxAssignments>({});
  const [flatTxSelections, setFlatTxSelections] = useState<FlatTxSelections>({});
  const [flatTxInclusions, setFlatTxInclusions] = useState<FlatTxInclusions>({});
  const [assignmentLastFour, setAssignmentLastFour] = useState('');
  const [sharedCardTemplate, setSharedCardTemplate] = useState<SharedCardTemplate>({
    issuerId: '',
    issuerName: '',
    subtype: 'CREDIT_CARD',
    cardNetwork: '',
    cardLevel: '',
    closingDay: '',
    dueDay: '',
    creditLimit: '',
    descriptionBase: '',
  });
  const [billDateOverrides, setBillDateOverrides] = useState<BillDateOverrides>({
    closingDate: '',
    dueDate: '',
  });
  const [templateError, setTemplateError] = useState<string | null>(null);

  const statusLabel = useCallback(
    (status: string) => {
      switch (status) {
        case 'PENDING':
        case 'EXTRACTING':
        case 'LLM_PROCESSING':
        case 'EXTRACTED':
        case 'IMPORTING':
        case 'AWAITING_REVIEW':
        case 'DONE':
        case 'FAILED':
          return t(`financeTransactions.billImport.status.${status}`);
        default:
          return status;
      }
    },
    [t],
  );

  // Sub-flow: inline card creation
  const [createCardForIndex, setCreateCardForIndex] = useState<number | null>(null);
  const [isCreateCardDrawerOpen, setIsCreateCardDrawerOpen] = useState(false);
  const [createDraftCardId, setCreateDraftCardId] = useState<string | null>(null);
  // Accounts created inline during this import session — merged with cardAccounts
  // to guarantee they appear in the Select even before Apollo refetch completes.
  const [inlineCreatedAccounts, setInlineCreatedAccounts] = useState<CardAccountOption[]>([]);

  // Reset all state when drawer opens
  React.useEffect(() => {
    if (!open) return;
    setStep('upload');
    setFile(null);
    setFileError(null);
    setIsEncryptedPdf(false);
    setPdfPassword('');
    setJobId(null);
    setErrorMessage(null);
    setCardResolutions([]);
    setTxSelections({});
    setTxCategorySelections({});
    setDraftCards([]);
    setFlatTxAssignments({});
    setFlatTxSelections({});
    setAssignmentLastFour('');
    setSharedCardTemplate({
      issuerId: '',
      issuerName: '',
      subtype: 'CREDIT_CARD',
      cardNetwork: '',
      cardLevel: '',
      closingDay: '',
      dueDay: '',
      creditLimit: '',
      descriptionBase: '',
    });
    setTemplateError(null);
    setInlineCreatedAccounts([]);
    setCreateDraftCardId(null);
    setCreateCardForIndex(null);
    setIsCreateCardDrawerOpen(false);
  }, [open]);

  const [submitImport, { loading: submitting }] = useMutation<{
    submitBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(SUBMIT_BILL_IMPORT);

  const [confirmImport, { loading: confirming }] = useMutation<{
    confirmBillImport: { id: string; status: string; errorCode?: string | null; errorMessage?: string | null };
  }>(CONFIRM_BILL_IMPORT);

  const [createFinancialAccount, { loading: creatingTemplateCards }] = useMutation<{
    createFinancialAccount: FinancialAccount;
  }>(CREATE_FINANCIAL_ACCOUNT);

  // Poll job status while in processing step
  const { data: jobData, stopPolling, startPolling } = useQuery<{
    billImportJob: {
      id: string;
      status: string;
      errorCode?: string | null;
      errorMessage?: string | null;
      reviewPayload?: {
        bankName?: string;
        reviewMode?: 'CARD_BUCKET_MATCHING' | 'TX_CARD_ASSIGNMENT';
        statementPeriod?: {
          from?: string;
          to?: string;
        };
        cards: Array<{
          reviewCardId: string;
          lastFourDigits?: string | null;
          holderName?: string;
          computedTotal: number;
          declaredTotal: number;
          totalMatchesDeclared: boolean;
          matchedFinancialAccountId?: string;
          matchAmbiguous: boolean;
          closingDate?: string;
          dueDate?: string;
          matchCandidates: Array<{ id: string; name: string }>;
          transactions: Array<{
            date?: string;
            description: string;
            amount: number;
            creditDebitType: string;
            isBillPayment?: boolean;
            installment?: { current: number; total: number };
            categoryId?: string | null;
            categorySource?: TxCategorySource;
          }>;
        }>;
      };
    };
  }>(BILL_IMPORT_JOB, {
    variables: { jobId },
    skip: !open || !jobId,
    fetchPolicy: 'network-only',
    pollInterval: step === 'processing' ? POLL_INTERVAL_MS : 0,
  });

  const reviewCards = React.useMemo(
    () => jobData?.billImportJob?.reviewPayload?.cards ?? [],
    [jobData?.billImportJob?.reviewPayload?.cards],
  );
  const reviewMode = jobData?.billImportJob?.reviewPayload?.reviewMode ?? 'CARD_BUCKET_MATCHING';

  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(
    TRANSACTION_CATEGORIES,
    { skip: !open, fetchPolicy: 'cache-first' },
  );
  const allCategories = categoriesData?.transactionCategories ?? [];

  // React to job status changes during polling
  React.useEffect(() => {
    const job = jobData?.billImportJob;
    if (!job) return;
    if (!jobId || job.id !== jobId) return;
    if (step !== 'processing') return;

    const status = job.status;
    if (status === 'AWAITING_REVIEW') {
      stopPolling();
      // Only initialise resolutions when transitioning from processing — never overwrite later steps
      setStep((currentStep) => {
        if (currentStep !== 'processing') return currentStep;
        const cards = job.reviewPayload?.cards ?? [];
        setCardResolutions(
          cards.map((c) => ({
            reviewCardId: c.reviewCardId,
            financialAccountId: c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null),
          })),
        );
        const selections: TxSelections = {};
        cards.forEach((card) => {
          card.transactions.forEach((_, ti) => {
            selections[txSelectionKey(card.reviewCardId, ti)] = true;
          });
        });
        setTxSelections(selections);
        const shouldUseFlatAssignmentMode = job.reviewPayload?.reviewMode === 'TX_CARD_ASSIGNMENT';
        if (shouldUseFlatAssignmentMode) {
          const flatSelections: FlatTxSelections = {};
          const flatInclusions: FlatTxInclusions = {};
          cards[0]?.transactions.forEach((_, txIndex) => {
            flatSelections[txIndex] = false;
            flatInclusions[txIndex] = true;
          });
          setDraftCards([]);
          setFlatTxAssignments({});
          setFlatTxSelections(flatSelections);
          setFlatTxInclusions(flatInclusions);
          setSharedCardTemplate((prev) => ({
            ...prev,
            subtype: prev.subtype || 'CREDIT_CARD',
            closingDay: String(getDayOfMonthFromDateInput(cards[0]?.closingDate) ?? prev.closingDay),
            dueDay: String(getDayOfMonthFromDateInput(cards[0]?.dueDate) ?? prev.dueDay),
          }));
          setTemplateError(null);
        } else {
          setDraftCards([]);
          setFlatTxAssignments({});
          setFlatTxSelections({});
          setFlatTxInclusions({});
        }
        setBillDateOverrides({
          closingDate: cards.find((card) => !!card.closingDate)?.closingDate ?? job.reviewPayload?.statementPeriod?.to ?? '',
          dueDate: cards.find((card) => !!card.dueDate)?.dueDate ?? '',
        });

        const initialCategorySelections: TxCategorySelections = {};
        if (shouldUseFlatAssignmentMode) {
          cards[0]?.transactions?.forEach((tx: any, txIndex: number) => {
            initialCategorySelections[`flat-${txIndex}`] = {
              categoryId: tx.categoryId ?? null,
              categorySource: tx.categorySource ?? null,
            };
          });
        } else {
          cards.forEach((card: any) => {
            card.transactions.forEach((tx: any, ti: number) => {
              initialCategorySelections[txSelectionKey(card.reviewCardId, ti)] = {
                categoryId: tx.categoryId ?? null,
                categorySource: tx.categorySource ?? null,
              };
            });
          });
        }
        setTxCategorySelections(initialCategorySelections);
        return 'card-matching';
      });
    } else if (status === 'FAILED') {
      stopPolling();
      setErrorMessage(resolveFriendlyImportErrorMessage(t, job.errorCode, job.errorMessage));
      setStep('error');
    }
  }, [jobData, jobId, step, stopPolling, t]);

  const { data: accountsData, loading: accountsLoading } = useQuery<{ financialAccounts: FinancialAccount[] }>(
    FINANCIAL_ACCOUNTS,
    { skip: !open, errorPolicy: 'all' },
  );
  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    skip: !open,
    errorPolicy: 'all',
  });

  const cardAccountOptions = React.useMemo<CardAccountOption[]>(() => {
    const existing = (accountsData?.financialAccounts ?? [])
      .filter((a) => a.type === 'CREDIT')
      .map((a) => {
        const issuerId = a.issuer?.id ?? null;
        const issuerName = a.issuer?.name ?? a.issuerName ?? null;
        return {
          id: a.id,
          name: a.name,
          issuerId,
          issuerName,
          issuerKey: toIssuerKey(issuerId, issuerName),
          maskedNumber: a.cardData?.maskedNumber ?? null,
        };
      });

    const mergedInline = inlineCreatedAccounts.filter(
      (ia) => !existing.some((a) => a.id === ia.id),
    );

    return [...existing, ...mergedInline];
  }, [accountsData?.financialAccounts, inlineCreatedAccounts]);

  const cardAccountOptionsById = React.useMemo(
    () => new Map(cardAccountOptions.map((account) => [account.id, account])),
    [cardAccountOptions],
  );

  const issuerOptions = React.useMemo(
    () =>
      (issuersData?.issuers ?? []).map((issuer) => ({
        id: issuer.id,
        name: issuer.name,
      })),
    [issuersData?.issuers],
  );

  const allIssuerOptions = React.useMemo(() => {
    const options = (issuersData?.issuers || []).map((issuer) => ({
      label: issuer.name,
      value: issuer.id,
      logo: issuer.logoAsset?.publicUrl ?? null,
      compe: issuer.compeCode ?? null,
    }));

    return [
      ...options,
      {
        label: t('financeTransactions.createAccount.step2.fields.customIssuer'),
        value: 'custom',
        logo: null,
        compe: null,
      },
    ];
  }, [issuersData?.issuers, t]);

  const cardSubtypeOptions = React.useMemo(
    () =>
      CardSubtypeValues.map((value) => ({
        value,
        label: t(`financeTransactions.createAccount.step2.enums.cardSubtype.${value}`),
      })),
    [t],
  );

  const cardNetworkOptions = React.useMemo(
    () =>
      CardNetworkValues.map((value) => ({
        value,
        label: t(`financeTransactions.createAccount.step2.enums.cardNetwork.${value}`),
      })),
    [t],
  );

  const cardLevelOptions = React.useMemo(
    () =>
      CardLevelValues.map((value) => ({
        value,
        label: t(`financeTransactions.createAccount.step2.enums.cardLevel.${value}`),
      })),
    [t],
  );

  const flatSourceCard =
    reviewMode === 'TX_CARD_ASSIGNMENT' && reviewCards.length > 0
      ? reviewCards[0]
      : null;

  const isFlatAssignmentMode = !!flatSourceCard;

  const selectedIssuerKey = React.useMemo(() => {
    for (const resolution of cardResolutions) {
      if (!resolution?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(resolution.financialAccountId);
      if (selected?.issuerKey) return selected.issuerKey;
    }
    for (const draft of draftCards) {
      if (!draft?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(draft.financialAccountId);
      if (selected?.issuerKey) return selected.issuerKey;
    }
    if (sharedCardTemplate.issuerId && sharedCardTemplate.issuerId !== 'custom') {
      return toIssuerKey(sharedCardTemplate.issuerId, undefined);
    }
    if (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim()) {
      return toIssuerKey(undefined, sharedCardTemplate.issuerName);
    }
    return null;
  }, [cardResolutions, draftCards, cardAccountOptionsById, sharedCardTemplate]);

  const selectedIssuerDefaults = (() => {
    for (const resolution of cardResolutions) {
      if (!resolution?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(resolution.financialAccountId);
      if (!selected) continue;

      if (selected.issuerId) {
        return { issuerId: selected.issuerId, issuerName: undefined as string | undefined };
      }
      if (selected.issuerName) {
        return { issuerId: undefined as string | undefined, issuerName: selected.issuerName };
      }
    }
    for (const draft of draftCards) {
      if (!draft?.financialAccountId) continue;
      const selected = cardAccountOptionsById.get(draft.financialAccountId);
      if (!selected) continue;

      if (selected.issuerId) {
        return { issuerId: selected.issuerId, issuerName: undefined as string | undefined };
      }
      if (selected.issuerName) {
        return { issuerId: undefined as string | undefined, issuerName: selected.issuerName };
      }
    }
    if (sharedCardTemplate.issuerId && sharedCardTemplate.issuerId !== 'custom') {
      return { issuerId: sharedCardTemplate.issuerId, issuerName: undefined as string | undefined };
    }
    if (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim()) {
      return { issuerId: undefined as string | undefined, issuerName: sharedCardTemplate.issuerName.trim() };
    }
    return { issuerId: undefined as string | undefined, issuerName: undefined as string | undefined };
  })();

  React.useEffect(() => {
    if (step !== 'card-matching' || !selectedIssuerKey) return;

    setCardResolutions((prev) => {
      let changed = false;
      const next = prev.map((resolution) => {
        if (!resolution.financialAccountId) return resolution;
        const selected = cardAccountOptionsById.get(resolution.financialAccountId);
        if (selected?.issuerKey === selectedIssuerKey) return resolution;
        changed = true;
        return { ...resolution, financialAccountId: null };
      });

      return changed ? next : prev;
    });
  }, [step, selectedIssuerKey, cardAccountOptionsById]);

  const getAvailableOptionsForCard = useCallback(
    (card: (typeof reviewCards)[number]) => {
      const baseOptions: CardAccountOption[] = card.matchAmbiguous
        ? card.matchCandidates.map((candidate) => {
            const known = cardAccountOptionsById.get(candidate.id);
            return known ?? {
              id: candidate.id,
              name: candidate.name,
              issuerId: null,
              issuerName: null,
              issuerKey: null,
              maskedNumber: null,
            };
          })
        : cardAccountOptions;

      if (!selectedIssuerKey) return baseOptions;
      return baseOptions.filter((option) => option.issuerKey === selectedIssuerKey);
    },
    [cardAccountOptions, cardAccountOptionsById, selectedIssuerKey],
  );

  React.useEffect(() => {
    if (step !== 'card-matching' || !selectedIssuerKey || draftCards.length === 0) return;

    setDraftCards((prev) => {
      let changed = false;
      const next = prev.map((draft) => {
        if (!draft.financialAccountId) return draft;
        const selected = cardAccountOptionsById.get(draft.financialAccountId);
        if (selected?.issuerKey === selectedIssuerKey) return draft;
        changed = true;
        return { ...draft, financialAccountId: null };
      });

      return changed ? next : prev;
    });
  }, [step, selectedIssuerKey, draftCards.length, cardAccountOptionsById]);

  const duplicateDraftLastFourDigits = React.useMemo(() => {
    const counts = new Map<string, number>();
    draftCards.forEach((draft) => {
      const normalized = draft.lastFourDigits.trim();
      if (normalized.length !== 4) return;
      counts.set(normalized, (counts.get(normalized) ?? 0) + 1);
    });
    return new Set(
      Array.from(counts.entries())
        .filter(([, count]) => count > 1)
        .map(([lastFour]) => lastFour),
    );
  }, [draftCards]);

  const allFlatTransactionsAssigned =
    !!flatSourceCard &&
    flatSourceCard.transactions.length > 0 &&
    flatSourceCard.transactions.every((_, txIndex) => !!flatTxAssignments[txIndex]);

  const allDraftBucketsReady =
    draftCards.length > 0 &&
    draftCards.every(
      (draft) =>
        draft.lastFourDigits.trim().length === 4 || (draft.allowMissingLastFour && draft.lastFourDigits.trim().length === 0),
    ) &&
    duplicateDraftLastFourDigits.size === 0;

  const selectedTemplateIssuerLabel = React.useMemo(() => {
    if (sharedCardTemplate.issuerId === 'custom') {
      return sharedCardTemplate.issuerName.trim();
    }
    return issuerOptions.find((issuer) => issuer.id === sharedCardTemplate.issuerId)?.name ?? '';
  }, [sharedCardTemplate.issuerId, sharedCardTemplate.issuerName, issuerOptions]);

  const templateRequiresBillingCycle = sharedCardTemplate.subtype === 'CREDIT_CARD';
  const templateHasIssuer =
    (sharedCardTemplate.issuerId === 'custom' && sharedCardTemplate.issuerName.trim().length > 0) ||
    (sharedCardTemplate.issuerId !== '' && sharedCardTemplate.issuerId !== 'custom');
  const templateReadyForCreation =
    templateHasIssuer &&
    !!sharedCardTemplate.subtype &&
    (!templateRequiresBillingCycle || (!!sharedCardTemplate.closingDay && !!sharedCardTemplate.dueDay));

  const templatePreviewName = buildDraftCardName(
    sharedCardTemplate,
    selectedTemplateIssuerLabel || 'Cartao',
    'XXXX',
  );

  const handleTemplateFieldChange = useCallback(
    (field: keyof SharedCardTemplate, value: string) => {
      setSharedCardTemplate((prev) => ({ ...prev, [field]: value }));
      setTemplateError(null);
    },
    [],
  );

  const findExactAccountMatches = useCallback(
    (lastFourDigits: string) => {
      const exactMatches = cardAccountOptions.filter((option) => option.maskedNumber?.endsWith(lastFourDigits));
      if (!selectedIssuerKey) return exactMatches;
      return exactMatches.filter((option) => option.issuerKey === selectedIssuerKey);
    },
    [cardAccountOptions, selectedIssuerKey],
  );

  const draftBucketSummaries = React.useMemo<DraftBucketSummary[]>(() => {
    if (!flatSourceCard) return [];
    return draftCards.map((draft) => {
      const assignedTransactions = flatSourceCard.transactions.filter(
        (_, txIndex) => flatTxAssignments[txIndex] === draft.draftCardId,
      );
      const total = assignedTransactions.reduce((sum, tx) => sum + (tx.creditDebitType === 'CREDIT' ? -tx.amount : tx.amount), 0);
      return {
        draftCardId: draft.draftCardId,
        label: draft.lastFourDigits.trim() ? `•••• ${draft.lastFourDigits.trim()}` : t('financeTransactions.billImport.cardMatchingStep.unassignedLastFour'),
        count: assignedTransactions.length,
        total,
      };
    });
  }, [draftCards, flatSourceCard, flatTxAssignments, t]);

  const materializeFlatAssignmentBuckets = useCallback(async (): Promise<boolean> => {
    const draftsToResolve = draftCards.filter(
      (draft) => !draft.financialAccountId && draftBucketSummaries.some((bucket) => bucket.draftCardId === draft.draftCardId && bucket.count > 0),
    );
    if (draftsToResolve.length === 0) {
      return true;
    }

    if (!templateReadyForCreation) {
      setTemplateError(t('financeTransactions.billImport.cardMatchingStep.templateInvalid'));
      return false;
    }

    if (duplicateDraftLastFourDigits.size > 0) {
      setTemplateError(t('financeTransactions.billImport.cardMatchingStep.duplicateLastFour'));
      return false;
    }

    setTemplateError(null);

    try {
      const createdAccounts: Array<{ draftCardId: string; account: FinancialAccount }> = [];
      const resolvedMatches: Array<{ draftCardId: string; financialAccountId: string }> = [];

      for (const draft of draftsToResolve) {
        const trimmedLastFour = draft.lastFourDigits.trim();
        if (trimmedLastFour.length === 4) {
          const exactMatches = findExactAccountMatches(trimmedLastFour);
          if (exactMatches.length === 1) {
            resolvedMatches.push({ draftCardId: draft.draftCardId, financialAccountId: exactMatches[0]!.id });
            continue;
          }
          if (exactMatches.length > 1) {
            setTemplateError(
              t('financeTransactions.billImport.cardMatchingStep.ambiguousExistingCard', { lastFourDigits: trimmedLastFour }),
            );
            return false;
          }
        }

        const input: Record<string, unknown> = {
          type:
            sharedCardTemplate.subtype === 'CREDIT_CARD' || sharedCardTemplate.subtype === 'CHARGE_CARD'
              ? 'CREDIT'
              : 'PAYMENT',
          subtype: sharedCardTemplate.subtype,
          name: buildDraftCardName(
            sharedCardTemplate,
            selectedTemplateIssuerLabel || 'Cartao',
            trimmedLastFour || t('financeTransactions.billImport.cardMatchingStep.noLastFourPlaceholder'),
          ),
          currencyCode: 'BRL',
          themeColor: DEFAULT_ACCOUNT_COLOR,
          includeInNetWorth: true,
          includeInDashboard: true,
          ...(sharedCardTemplate.issuerId === 'custom'
            ? { issuerName: sharedCardTemplate.issuerName.trim() }
            : { issuerId: sharedCardTemplate.issuerId }),
          cardData: {
            cardNetwork: sharedCardTemplate.cardNetwork || undefined,
            cardLevel:
              sharedCardTemplate.subtype === 'CREDIT_CARD' && sharedCardTemplate.cardLevel
                ? sharedCardTemplate.cardLevel
                : undefined,
            maskedNumber: trimmedLastFour || undefined,
            creditLimit: sharedCardTemplate.creditLimit ? Number(sharedCardTemplate.creditLimit) : undefined,
            closingDay: sharedCardTemplate.closingDay ? Number(sharedCardTemplate.closingDay) : undefined,
            dueDay: sharedCardTemplate.dueDay ? Number(sharedCardTemplate.dueDay) : undefined,
          },
        };

        const response = await createFinancialAccount({ variables: { input } });
        const created = response.data?.createFinancialAccount;
        if (!created) {
          throw new Error('createFinancialAccount returned no account');
        }
        createdAccounts.push({ draftCardId: draft.draftCardId, account: created });
      }

      if (createdAccounts.length > 0) {
        setInlineCreatedAccounts((prev) => {
          const merged = [...prev];
          createdAccounts.forEach(({ account }) => {
            if (merged.some((item) => item.id === account.id)) return;
            const issuerId = account.issuer?.id ?? null;
            const issuerName = account.issuer?.name ?? account.issuerName ?? null;
            merged.push({
              id: account.id,
              name: account.name,
              issuerId,
              issuerName,
              issuerKey: toIssuerKey(issuerId, issuerName),
              maskedNumber: account.cardData?.maskedNumber ?? null,
            });
          });
          return merged;
        });
      }

      setDraftCards((prev) =>
        prev.map((draft) => {
          const resolved = resolvedMatches.find((item) => item.draftCardId === draft.draftCardId);
          if (resolved) {
            return { ...draft, financialAccountId: resolved.financialAccountId };
          }
          const created = createdAccounts.find((item) => item.draftCardId === draft.draftCardId);
          if (created) {
            return { ...draft, financialAccountId: created.account.id };
          }
          return draft;
        }),
      );

      return true;
    } catch (error) {
      setTemplateError(extractErrorMessage(error, t('financeTransactions.billImport.cardMatchingStep.templateCreateFailed')));
      return false;
    }
  }, [
    createFinancialAccount,
    draftBucketSummaries,
    draftCards,
    duplicateDraftLastFourDigits.size,
    findExactAccountMatches,
    selectedTemplateIssuerLabel,
    sharedCardTemplate,
    t,
    templateReadyForCreation,
  ]);

  const assignSelectedTransactionsToBucket = useCallback(
    (lastFourDigits: string | null) => {
      if (!flatSourceCard) return;
      const selectedTransactionIndexes = flatSourceCard.transactions
        .map((_, txIndex) => txIndex)
        .filter((txIndex) => flatTxSelections[txIndex]);
      if (selectedTransactionIndexes.length === 0) return;

      const normalizedLastFour = (lastFourDigits ?? '').replace(/\D/g, '').slice(0, 4);
      if (normalizedLastFour.length !== 4 && lastFourDigits !== null) return;

      const bucketKey = normalizedLastFour || '__NO_LAST4__';
      const existingDraft = draftCards.find((draft) =>
        normalizedLastFour
          ? draft.lastFourDigits.trim() === normalizedLastFour
          : draft.allowMissingLastFour && draft.lastFourDigits.trim().length === 0,
      );

      let nextDraftCardId = existingDraft?.draftCardId;
      let nextDraftCards = draftCards;

      if (!nextDraftCardId) {
        nextDraftCardId = `draft-${crypto.randomUUID()}`;
        nextDraftCards = [
          ...draftCards,
          {
            draftCardId: nextDraftCardId,
            lastFourDigits: normalizedLastFour,
            financialAccountId:
              normalizedLastFour.length === 4 && findExactAccountMatches(normalizedLastFour).length === 1
                ? findExactAccountMatches(normalizedLastFour)[0]!.id
                : null,
            allowMissingLastFour: bucketKey === '__NO_LAST4__',
          },
        ];
        setDraftCards(nextDraftCards);
      }

      setFlatTxAssignments((prev) => {
        const next = { ...prev };
        selectedTransactionIndexes.forEach((txIndex) => {
          next[txIndex] = nextDraftCardId!;
        });
        return next;
      });
      setFlatTxSelections((prev) => {
        const next = { ...prev };
        selectedTransactionIndexes.forEach((txIndex) => {
          next[txIndex] = false;
        });
        return next;
      });
      if (normalizedLastFour.length === 4) {
        setAssignmentLastFour(normalizedLastFour);
      }
      setTemplateError(null);
    },
    [draftCards, findExactAccountMatches, flatSourceCard, flatTxSelections],
  );

  const handleFlatContinue = useCallback(async () => {
    const ok = await materializeFlatAssignmentBuckets();
    if (ok) {
      setStep('tx-review');
    }
  }, [materializeFlatAssignmentBuckets]);

  const handleClose = useCallback(() => {
    stopPolling();
    onClose();
  }, [onClose, stopPolling]);

  const resetToUploadStep = useCallback(() => {
    stopPolling();
    setStep('upload');
    setFileError(null);
    setJobId(null);
    setErrorMessage(null);
    setCardResolutions([]);
    setTxSelections({});
    setTxCategorySelections({});
    setDraftCards([]);
    setFlatTxAssignments({});
    setFlatTxSelections({});
    setFlatTxInclusions({});
    setTemplateError(null);
    setCreateDraftCardId(null);
    setCreateCardForIndex(null);
    setBillDateOverrides({ closingDate: '', dueDate: '' });
  }, [stopPolling]);

  const validateFile = (f: File): string | null => {
    const ext = getExtension(f.name);
    if (!ACCEPTED_EXTENSIONS.includes(ext)) {
      return t('financeTransactions.import.uploadStep.unsupportedType');
    }
    if (f.size > MAX_FILE_BYTES) {
      return t('financeTransactions.import.uploadStep.fileTooLarge');
    }
    return null;
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (!f) return;
    setPdfPassword('');
    setIsEncryptedPdf(false);
    const err = validateFile(f);
    if (err) {
      setFileError(err);
      setFile(null);
      setIsEncryptedPdf(false);
      return;
    }

    try {
      const ext = getExtension(f.name);
      const encrypted = ext === '.pdf' ? await detectEncryptedPdf(f) : false;
      setIsEncryptedPdf(encrypted);
      if (!encrypted) setPdfPassword('');
      setFileError(null);
      setFile(f);
    } catch (error) {
      setFileError(extractErrorMessage(error, 'Failed to read file'));
      setFile(null);
      setIsEncryptedPdf(false);
      setPdfPassword('');
    }
  };

  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    const f = e.dataTransfer.files?.[0];
    if (!f) return;
    setPdfPassword('');
    setIsEncryptedPdf(false);
    const err = validateFile(f);
    if (err) {
      setFileError(err);
      setFile(null);
      setIsEncryptedPdf(false);
      return;
    }
    try {
      const ext = getExtension(f.name);
      const encrypted = ext === '.pdf' ? await detectEncryptedPdf(f) : false;
      setIsEncryptedPdf(encrypted);
      if (!encrypted) setPdfPassword('');
      setFileError(null);
      setFile(f);
    } catch (error) {
      setFileError(extractErrorMessage(error, 'Failed to read file'));
      setFile(null);
      setIsEncryptedPdf(false);
      setPdfPassword('');
    }
  };

  const handleSubmitFile = async () => {
    if (!file) return;
    if (isEncryptedPdf && !pdfPassword.trim()) {
      setFileError(t('financeTransactions.billImport.uploadStep.passwordRequired'));
      return;
    }
    try {
      const base64 = await fileToBase64(file);
      const result = await submitImport({
        variables: {
          input: {
            fileName: file.name,
            fileBase64: base64,
            importType: 'CREDIT_CARD_BILL',
            password: isEncryptedPdf ? pdfPassword.trim() : undefined,
          },
        },
      });
      const id = result.data?.submitBillImport?.id;
      if (id) {
        setJobId(id);
        startPolling(POLL_INTERVAL_MS);
        setStep('processing');
      } else {
        setErrorMessage(resolveFriendlyImportErrorMessage(t));
        setStep('error');
      }
    } catch (err) {
      if (extractErrorCode(err) === 'PDF_PASSWORD_REQUIRED') {
        setIsEncryptedPdf(true);
        setPdfPassword('');
        setFileError(t('financeTransactions.billImport.uploadStep.passwordInvalid'));
        setStep('upload');
        return;
      }
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      setErrorMessage(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
      setStep('error');
    }
  };

  const hasDraftBucketsNeedingCreation = React.useMemo(() => {
    if (!isFlatAssignmentMode || !flatSourceCard) return false;
    return draftCards.some((draft) => {
      if (draft.financialAccountId) return false;
      const bucket = draftBucketSummaries.find((summary) => summary.draftCardId === draft.draftCardId);
      return !!bucket && bucket.count > 0;
    });
  }, [draftBucketSummaries, draftCards, flatSourceCard, isFlatAssignmentMode]);

  const allCardsResolved = isFlatAssignmentMode
    ? allDraftBucketsReady &&
      allFlatTransactionsAssigned &&
      (!hasDraftBucketsNeedingCreation || templateReadyForCreation)
    : cardResolutions.length > 0 && cardResolutions.every((r) => r.financialAccountId !== null);

  const handleConfirm = async () => {
    if (!jobId || !allCardsResolved) return;
    const cards = reviewCards;
    try {
      const confirmCards = isFlatAssignmentMode && flatSourceCard
        ? draftCards
            .map((draft) => ({
              reviewCardId: flatSourceCard.reviewCardId,
              financialAccountId: draft.financialAccountId,
              transactions: flatSourceCard.transactions
                .map((tx, txIndex) => ({ tx, txIndex }))
                .filter(({ txIndex }) => flatTxAssignments[txIndex] === draft.draftCardId)
                .map(({ tx, txIndex }) => {
                  const categoryKey = `flat-${txIndex}`;
                  const categoryState = txCategorySelections[categoryKey];

                  return {
                    date: tx.date ?? '',
                    description: tx.description,
                    amount: tx.amount,
                    creditDebitType: tx.creditDebitType,
                    include: flatTxInclusions[txIndex] ?? true,
                    isBillPayment: !!tx.isBillPayment,
                    categoryId: categoryState?.categoryId ?? undefined,
                    categorySource: categoryState?.categorySource ?? undefined,
                    installment: tx.installment
                      ? { current: tx.installment.current, total: tx.installment.total }
                      : null,
                  };
                }),
            }))
            .filter((draft) => !!draft.financialAccountId && draft.transactions.length > 0)
        : cards.map((card, ci) => ({
            reviewCardId: card.reviewCardId,
            financialAccountId: cardResolutions[ci]!.financialAccountId,
            transactions: card.transactions.map((tx, ti) => {
              const categoryKey = txSelectionKey(card.reviewCardId, ti);
              const categoryState = txCategorySelections[categoryKey];
              return {
                date: tx.date ?? '',
                description: tx.description,
                amount: tx.amount,
                creditDebitType: tx.creditDebitType,
                include: txSelections[categoryKey] ?? true,
                isBillPayment: !!tx.isBillPayment,
                categoryId: categoryState?.categoryId ?? undefined,
                categorySource: categoryState?.categorySource ?? undefined,
                installment: tx.installment
                  ? {
                      current: tx.installment.current,
                      total: tx.installment.total,
                    }
                  : null,
              };
            }),
          }));
      const result = await confirmImport({
        variables: {
          input: {
            jobId,
            closingDate: billDateOverrides.closingDate || undefined,
            dueDate: billDateOverrides.dueDate || undefined,
            cards: confirmCards,
          },
        },
      });
      const status = result.data?.confirmBillImport?.status;
      if (status === 'DONE' || status === 'COMPLETED') {
        setStep('result');
        onImportCompleted?.();
      } else {
        setErrorMessage(
          resolveFriendlyImportErrorMessage(
            t,
            result.data?.confirmBillImport?.errorCode,
            result.data?.confirmBillImport?.errorMessage,
          ),
        );
        setStep('error');
      }
    } catch (err) {
      const defaultMessage = resolveFriendlyImportErrorMessage(t);
      setErrorMessage(extractErrorCode(err) ? extractErrorMessage(err, defaultMessage) : defaultMessage);
      setStep('error');
    }
  };

  // ─── Step renderers ────────────────────────────────────────────────────────

  const renderUpload = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.uploadStep.prompt')}
      </Typography>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t('financeTransactions.billImport.uploadStep.recommendation')}
      </Alert>
      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_TYPES}
        onChange={handleFileChange}
        style={{ display: 'none' }}
      />
      <Box
        onDrop={handleDrop}
        onDragOver={(e) => e.preventDefault()}
        onClick={() => fileInputRef.current?.click()}
        sx={{
          border: '3px dashed',
          borderColor: fileError ? 'error.main' : 'divider',
          borderRadius: 2,
          p: 4,
          textAlign: 'center',
          cursor: 'pointer',
          bgcolor: file ? 'action.selected' : 'transparent',
          transition: 'all 0.2s',
          '&:hover': {
            bgcolor: (theme) => alpha(theme.palette.primary.main, 0.08),
            borderColor: fileError ? 'error.main' : 'primary.dark',
          },
        }}
      >
        <UploadIcon sx={{ fontSize: 48, color: 'primary.main', mb: 1, display: 'block', mx: 'auto' }} />
        <Typography variant="body2" color="primary.main" fontWeight="medium" sx={{ mb: 0.5 }}>
          {t('financeTransactions.billImport.uploadStep.dragDrop')}
        </Typography>
        <Typography variant="caption" color="text.disabled">
          {t('financeTransactions.billImport.uploadStep.acceptedHint', { maxMb: MAX_FILE_BYTES / (1024 * 1024) })}
        </Typography>
      </Box>

      {file && (
        <Box sx={{ mt: 2, p: 2, bgcolor: (t) => alpha(t.palette.primary.main, 0.04), borderRadius: 1, border: '1px solid', borderColor: (t) => alpha(t.palette.primary.main, 0.2) }}>
          <Stack direction="row" alignItems="center" spacing={1}>
            <FileIcon color="primary" fontSize="small" />
            <Typography variant="body2" fontWeight="medium">{file.name}</Typography>
          </Stack>
        </Box>
      )}

      {file && isEncryptedPdf && (
        <Stack spacing={1.25} sx={{ mt: 2 }}>
          <Alert severity="warning">
            {t('financeTransactions.billImport.uploadStep.encryptedDetected')}
          </Alert>
          <TextField
            fullWidth
            type="password"
            size="small"
            autoComplete="new-password"
            name="pdf-password"
            label={t('financeTransactions.billImport.uploadStep.passwordLabel')}
            value={pdfPassword}
            onChange={(event) => {
              setPdfPassword(event.target.value);
              if (fileError) setFileError(null);
            }}
          />
        </Stack>
      )}

      {fileError && <Alert severity="error" sx={{ mt: 2 }}>{fileError}</Alert>}

      <Divider sx={{ my: 3 }} />
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined" onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
        <Button
          variant="contained"
          onClick={handleSubmitFile}
          disabled={!file || submitting || (isEncryptedPdf && !pdfPassword.trim())}
          startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : null}
        >
          {submitting ? t('financeTransactions.billImport.uploadStep.submitting') : t('financeTransactions.billImport.uploadStep.submit')}
        </Button>
      </Stack>
    </Box>
  );

  const renderProcessing = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.billImport.processingStep.title')}</Typography>
      <LinearProgress sx={{ mb: 2 }} />
      <Typography variant="body2" color="text.secondary">
        {t('financeTransactions.billImport.processingStep.description')}
      </Typography>
      {jobData?.billImportJob?.status && (
        <Typography variant="caption" color="text.disabled" display="block" sx={{ mt: 1 }}>
          {t('financeTransactions.billImport.processingStep.status', { status: statusLabel(jobData.billImportJob.status) })}
        </Typography>
      )}
    </Box>
  );

  const renderCardMatching = () => {
    const cards = reviewCards;
    if (isFlatAssignmentMode && flatSourceCard) {
      const selectedTransactionIndexes = flatSourceCard.transactions
        .map((_, txIndex) => txIndex)
        .filter((txIndex) => flatTxSelections[txIndex]);

      const assignedCount = Object.values(flatTxAssignments).filter(Boolean).length;
      const flatAssignmentProgress =
        flatSourceCard.transactions.length > 0
          ? Math.round((assignedCount / flatSourceCard.transactions.length) * 100)
          : 0;

      const issuerLabel =
        sharedCardTemplate.issuerId === 'custom'
          ? sharedCardTemplate.issuerName.trim()
          : issuerOptions.find((issuer) => issuer.id === sharedCardTemplate.issuerId)?.name ?? '';
      const issuerCardsForSelection = selectedIssuerKey
        ? cardAccountOptions
            .filter((opt) => opt.issuerKey === selectedIssuerKey)
            .filter((opt) => (opt.maskedNumber ?? '').trim().length >= 4)
        : [];
      const issuerCardLastFourOptions = Array.from(
        new Set(
          issuerCardsForSelection
            .map((opt) => (opt.maskedNumber ?? '').replace(/\D/g, '').slice(-4))
            .filter((lastFour) => lastFour.length === 4),
        ),
      ).sort();
      const issuerHasExistingCards = issuerCardLastFourOptions.length > 0;
      const issuerSelected = templateHasIssuer;

      return (
        <Box sx={{ p: 1 }}>
          <Alert severity="info" sx={{ mb: 2 }}>
            {t('financeTransactions.billImport.cardMatchingStep.flatAssignmentBanner')}
          </Alert>

          <Stack spacing={3} sx={{ pb: selectedTransactionIndexes.length > 0 ? 11 : 0 }}>
            <Stack spacing={1.25}>
              <Stack direction="row" spacing={1.25} alignItems="center">
                <Box
                  sx={{
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    bgcolor: 'primary.main',
                    color: 'common.white',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                  }}
                >
                  <Typography variant="caption" fontWeight={700}>1</Typography>
                </Box>
                <Typography variant="subtitle2" fontWeight={700}>
                  {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerTitle')}
                </Typography>
              </Stack>

              <Paper variant="outlined" sx={{ p: 2.5 }}>
                <Stack spacing={2}>
                  <Autocomplete
                    options={allIssuerOptions}
                    loading={issuersLoading}
                    disabled={issuersLoading}
                    value={allIssuerOptions.find((o) => o.value === sharedCardTemplate.issuerId) || null}
                    onChange={(_, newValue) => {
                      const nextIssuerId = newValue ? newValue.value : '';
                      handleTemplateFieldChange('issuerId', nextIssuerId);
                      if (nextIssuerId !== 'custom') {
                        handleTemplateFieldChange('issuerName', '');
                      }
                      setDraftCards([]);
                      setFlatTxAssignments({});
                      setAssignmentLastFour('');
                      setTemplateError(null);
                    }}
                    isOptionEqualToValue={(option, value) => option.value === value.value}
                    noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                    getOptionLabel={(option) => option.label || ''}
                    renderOption={(props, option) => {
                      const { key, ...otherProps } = props;
                      return (
                        <Box
                          component="li"
                          key={key}
                          {...otherProps}
                          sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}
                        >
                          {option.logo ? (
                            <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
                          ) : (
                            <Avatar sx={{ width: 40, height: 40, borderRadius: 1.5, bgcolor: 'action.disabledBackground' }}>
                              <BankIcon sx={{ fontSize: 24 }} />
                            </Avatar>
                          )}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body1" fontWeight="600">
                              {option.label}
                            </Typography>
                            {option.compe && (
                              <Typography variant="body2" color="text.secondary">
                                ({option.compe})
                              </Typography>
                            )}
                          </Box>
                        </Box>
                      );
                    }}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        size="small"
                        label={t('financeTransactions.createAccount.step2.fields.issuer')}
                        helperText={!templateHasIssuer ? t('financeTransactions.billImport.cardMatchingStep.templateIssuerHint') : undefined}
                        InputProps={{
                          ...params.InputProps,
                          endAdornment: (
                            <React.Fragment>
                              {issuersLoading ? <CircularProgress color="inherit" size={20} /> : null}
                              {params.InputProps.endAdornment}
                            </React.Fragment>
                          ),
                        }}
                      />
                    )}
                  />

                  {sharedCardTemplate.issuerId === 'custom' && (
                    <TextField
                      size="small"
                      label={t('financeTransactions.createAccount.step2.fields.institutionName')}
                      value={sharedCardTemplate.issuerName}
                      onChange={(e) => {
                        handleTemplateFieldChange('issuerName', e.target.value);
                        setDraftCards([]);
                        setFlatTxAssignments({});
                        setAssignmentLastFour('');
                        setTemplateError(null);
                      }}
                    />
                  )}

                  {issuerSelected ? (
                    <Paper
                      variant="outlined"
                      sx={(theme) => ({
                        p: 1.5,
                        borderRadius: 2,
                        bgcolor: alpha(theme.palette.primary.main, 0.06),
                        borderColor: alpha(theme.palette.primary.main, 0.18),
                      })}
                    >
                      <Stack direction="row" spacing={1.5} alignItems="flex-start">
                        <Avatar sx={{ bgcolor: alpha('#1976d2', 0.12), color: 'primary.main', width: 36, height: 36 }}>
                          <CardIcon fontSize="small" />
                        </Avatar>
                        <Box sx={{ flex: 1, minWidth: 0 }}>
                          <Typography variant="body2" fontWeight={700}>
                            {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerSelectedTitle', { issuer: issuerLabel })}
                          </Typography>
                          {accountsLoading ? (
                            <Typography variant="body2" color="text.secondary">
                              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.loadingCards')}
                            </Typography>
                          ) : issuerHasExistingCards ? (
                            <Typography variant="body2" color="text.secondary">
                              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerHasCardsMessage', {
                                count: issuerCardsForSelection.length,
                              })}
                            </Typography>
                          ) : (
                            <Typography variant="body2" color="text.secondary">
                              {t('financeTransactions.billImport.cardMatchingStep.flatSteps.issuerNoCardsMessage')}
                            </Typography>
                          )}
                        </Box>
                        <Box
                          sx={{
                            width: 28,
                            height: 28,
                            borderRadius: '50%',
                            bgcolor: 'success.main',
                            color: 'common.white',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            mt: 0.25,
                          }}
                        >
                          <CheckIcon fontSize="small" />
                        </Box>
                      </Stack>
                    </Paper>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      {t('financeTransactions.billImport.cardMatchingStep.flatSteps.selectIssuerFirst')}
                    </Typography>
                  )}
                </Stack>
              </Paper>
            </Stack>

            {!issuerHasExistingCards && (
              <Box sx={{ opacity: issuerSelected ? 1 : 0.35, pointerEvents: issuerSelected ? 'auto' : 'none' }}>
                <Stack spacing={1.25}>
                  <Stack direction="row" spacing={1.25} alignItems="center">
                    <Box
                      sx={{
                        width: 28,
                        height: 28,
                        borderRadius: '50%',
                        bgcolor: issuerSelected ? 'primary.main' : 'grey.200',
                        color: issuerSelected ? 'common.white' : 'text.disabled',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontWeight: 700,
                      }}
                    >
                      <Typography variant="caption" fontWeight={700}>2</Typography>
                    </Box>
                    <Typography variant="subtitle2" fontWeight={700} color={issuerSelected ? 'text.primary' : 'text.disabled'}>
                      {t('financeTransactions.billImport.cardMatchingStep.flatSteps.newCardDetailsTitle')}
                    </Typography>
                  </Stack>

                  <Paper variant="outlined" sx={{ p: 2.5 }}>
                    <Stack spacing={2}>
                      {templateError && <Alert severity="warning">{templateError}</Alert>}

                      <FormControl fullWidth size="small">
                        <InputLabel>{t('financeTransactions.createAccount.step2.fields.subtype')}</InputLabel>
                        <Select
                          label={t('financeTransactions.createAccount.step2.fields.subtype')}
                          value={sharedCardTemplate.subtype}
                          onChange={(e) => handleTemplateFieldChange('subtype', e.target.value as string)}
                        >
                          {cardSubtypeOptions.map((option) => (
                            <SelectMenuItem key={option.value} value={option.value}>
                              {option.label}
                            </SelectMenuItem>
                          ))}
                        </Select>
                      </FormControl>

                      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                        <FormControl fullWidth size="small">
                          <InputLabel>{t('financeTransactions.createAccount.step2.fields.network')}</InputLabel>
                          <Select
                            label={t('financeTransactions.createAccount.step2.fields.network')}
                            value={sharedCardTemplate.cardNetwork}
                            onChange={(e) => handleTemplateFieldChange('cardNetwork', e.target.value as string)}
                          >
                            <SelectMenuItem value="">
                              <em>{t('financeTransactions.billImport.cardMatchingStep.optionalField')}</em>
                            </SelectMenuItem>
                            {cardNetworkOptions.map((option) => (
                              <SelectMenuItem key={option.value} value={option.value}>
                                {option.label}
                              </SelectMenuItem>
                            ))}
                          </Select>
                        </FormControl>

                        <FormControl fullWidth size="small" disabled={sharedCardTemplate.subtype !== 'CREDIT_CARD'}>
                          <InputLabel>{t('financeTransactions.createAccount.step2.fields.level')}</InputLabel>
                          <Select
                            label={t('financeTransactions.createAccount.step2.fields.level')}
                            value={sharedCardTemplate.cardLevel}
                            onChange={(e) => handleTemplateFieldChange('cardLevel', e.target.value as string)}
                          >
                            <SelectMenuItem value="">
                              <em>{t('financeTransactions.billImport.cardMatchingStep.optionalField')}</em>
                            </SelectMenuItem>
                            {cardLevelOptions.map((option) => (
                              <SelectMenuItem key={option.value} value={option.value}>
                                {option.label}
                              </SelectMenuItem>
                            ))}
                          </Select>
                        </FormControl>
                      </Stack>

                      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                        <TextField
                          fullWidth
                          size="small"
                          type="number"
                          label={t('financeTransactions.createAccount.step2.fields.closingDay')}
                          value={sharedCardTemplate.closingDay}
                          onChange={(e) => handleTemplateFieldChange('closingDay', e.target.value.replace(/\D/g, '').slice(0, 2))}
                          inputProps={{ min: 1, max: 31 }}
                          disabled={!templateRequiresBillingCycle}
                        />
                        <TextField
                          fullWidth
                          size="small"
                          type="number"
                          label={t('financeTransactions.createAccount.step2.fields.dueDay')}
                          value={sharedCardTemplate.dueDay}
                          onChange={(e) => handleTemplateFieldChange('dueDay', e.target.value.replace(/\D/g, '').slice(0, 2))}
                          inputProps={{ min: 1, max: 31 }}
                          disabled={!templateRequiresBillingCycle}
                        />
                        <NumericFormat
                          customInput={TextField}
                          fullWidth
                          size="small"
                          label={t('financeTransactions.createAccount.step2.fields.creditLimit')}
                          value={sharedCardTemplate.creditLimit}
                          thousandSeparator={moneyLocale === 'pt-BR' ? '.' : ','}
                          decimalSeparator={moneyLocale === 'pt-BR' ? ',' : '.'}
                          decimalScale={2}
                          fixedDecimalScale
                          prefix={moneyLocale === 'pt-BR' ? 'R$ ' : '$ '}
                          allowNegative={false}
                          valueIsNumericString
                          onValueChange={(values) => handleTemplateFieldChange('creditLimit', values.value)}
                        />
                      </Stack>

                      <TextField
                        size="small"
                        label={t('financeTransactions.billImport.cardMatchingStep.descriptionBase')}
                        value={sharedCardTemplate.descriptionBase}
                        onChange={(e) => handleTemplateFieldChange('descriptionBase', e.target.value)}
                        helperText={t('financeTransactions.billImport.cardMatchingStep.descriptionBaseHint')}
                      />

                      <Alert severity="success" variant="outlined">
                        {t('financeTransactions.billImport.cardMatchingStep.previewLabel', { name: templatePreviewName })}
                      </Alert>
                    </Stack>
                  </Paper>
                </Stack>
              </Box>
            )}

            <Box sx={{ opacity: issuerSelected ? 1 : 0.35, pointerEvents: issuerSelected ? 'auto' : 'none' }}>
              <Stack spacing={1.25}>
                <Stack direction="row" spacing={1.25} alignItems="center">
                  <Box
                    sx={{
                      width: 28,
                      height: 28,
                      borderRadius: '50%',
                      bgcolor: issuerSelected ? 'primary.main' : 'grey.200',
                      color: issuerSelected ? 'common.white' : 'text.disabled',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                    }}
                  >
                    <Typography variant="caption" fontWeight={700}>{issuerHasExistingCards ? 2 : 3}</Typography>
                  </Box>
                  <Typography variant="subtitle2" fontWeight={700} color={issuerSelected ? 'text.primary' : 'text.disabled'}>
                    {t('financeTransactions.billImport.cardMatchingStep.flatSteps.assignTransactionsTitle')}
                  </Typography>
                </Stack>

                <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 0.75 }}>
                  <Stack direction="row" spacing={1.25} alignItems="center" sx={{ minWidth: 0 }}>
                    <LinearProgress
                      variant="determinate"
                      value={flatAssignmentProgress}
                      sx={(theme) => ({
                        width: 180,
                        height: 6,
                        borderRadius: 999,
                        bgcolor: theme.palette.action.hover,
                        '& .MuiLinearProgress-bar': { borderRadius: 999 },
                      })}
                    />
                    <Typography variant="body2" fontWeight={600} color="primary.main" noWrap>
                      {t('financeTransactions.billImport.cardMatchingStep.assignmentProgressShort', {
                        assigned: assignedCount,
                        total: flatSourceCard.transactions.length,
                      })}
                    </Typography>
                  </Stack>
                  <Button
                    size="small"
                    variant="text"
                    onClick={() => {
                      const nextSelections: FlatTxSelections = {};
                      flatSourceCard.transactions.forEach((_, txIndex) => {
                        nextSelections[txIndex] = true;
                      });
                      setFlatTxSelections(nextSelections);
                    }}
                    sx={{ fontWeight: 700 }}
                  >
                    {t('financeTransactions.billImport.cardMatchingStep.selectAll')}
                  </Button>
                </Stack>

                <Paper variant="outlined">
                  <List disablePadding dense>
                    {flatSourceCard.transactions.map((tx, txIndex) => {
                      const assignedDraftId = flatTxAssignments[txIndex];
                      const assignedDraft = draftCards.find((draft) => draft.draftCardId === assignedDraftId);
                      const selected = flatTxSelections[txIndex] ?? false;
                      const isDebit = tx.creditDebitType === 'DEBIT';
                      const trimmedLastFour = assignedDraft?.lastFourDigits.trim() ?? '';
                      const hasLastFour = trimmedLastFour.length === 4;

                      return (
                        <React.Fragment key={`flat-tx-${txIndex}`}>
                          {txIndex > 0 && <Divider />}
                          <ListItem
                            disableGutters
                            sx={{
                              py: 1,
                              px: 1.25,
                              alignItems: 'flex-start',
                            }}
                          >
                            <Checkbox
                              checked={selected}
                              onChange={(e) => setFlatTxSelections((prev) => ({ ...prev, [txIndex]: e.target.checked }))}
                              size="small"
                              sx={{ mt: 0.5, mr: 0.75 }}
                            />
                            <Box sx={{ flex: 1, minWidth: 0, pt: 0.25 }}>
                              <Typography variant="body2" fontWeight="medium" noWrap>
                                {tx.description}
                              </Typography>
                              <Typography variant="caption" color="text.secondary">
                                {formatTxDisplayDate(tx.date, i18n.language)}
                              </Typography>
                            </Box>
                            <Box sx={{ pl: 1, minWidth: 120, textAlign: 'right' }}>
                              <Typography variant="body2" fontWeight="medium" color={isDebit ? 'error.main' : 'text.primary'}>
                                {tx.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                              </Typography>
                              <Chip
                                size="small"
                                label={
                                  assignedDraft
                                    ? (
                                        hasLastFour
                                          ? t('financeTransactions.billImport.cardMatchingStep.lastFourChip', { lastFour: trimmedLastFour })
                                          : t('financeTransactions.billImport.cardMatchingStep.unassignedLastFour')
                                      )
                                    : t('financeTransactions.billImport.cardMatchingStep.status.pending')
                                }
                                color={assignedDraft && hasLastFour ? 'success' : 'default'}
                                variant={assignedDraft ? 'outlined' : 'filled'}
                                sx={{ mt: 0.75, fontSize: 11 }}
                              />
                            </Box>
                          </ListItem>
                        </React.Fragment>
                      );
                    })}
                  </List>
                </Paper>
              </Stack>
            </Box>
          </Stack>

          {selectedTransactionIndexes.length > 0 && (
            <Box sx={{ position: 'sticky', bottom: 16, mt: 1, zIndex: 1 }}>
              <Paper
                elevation={10}
                sx={{
                  p: 1.25,
                  borderRadius: 2,
                  bgcolor: 'background.paper',
                  border: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Stack direction="row" spacing={1.25} alignItems="center">
                  <Chip
                    size="small"
                    label={t('financeTransactions.billImport.cardMatchingStep.selectedCount', { count: selectedTransactionIndexes.length })}
                    sx={(theme) => ({
                      fontWeight: 600,
                      bgcolor: alpha(theme.palette.primary.main, 0.12),
                      color: theme.palette.primary.main,
                      px: 0.75,
                      height: 28,
                      '& .MuiChip-label': { px: 1.25 },
                    })}
                  />
                  <Box sx={{ flex: 1 }} />
                  {issuerHasExistingCards ? (
                    <Autocomplete
                      freeSolo
                      clearOnBlur={false}
                      options={issuerCardLastFourOptions}
                      value={assignmentLastFour.trim() ? assignmentLastFour.trim() : null}
                      inputValue={assignmentLastFour}
                      onChange={(_, next) => {
                        const normalized = String(next ?? '').replace(/\D/g, '').slice(0, 4);
                        setAssignmentLastFour(normalized);
                      }}
                      onInputChange={(_, nextInputValue) => {
                        setAssignmentLastFour(nextInputValue.replace(/\D/g, '').slice(0, 4));
                      }}
                      sx={{ width: 210 }}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          size="small"
                          placeholder={t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel')}
                          inputProps={{
                            ...params.inputProps,
                            inputMode: 'numeric',
                            maxLength: 4,
                            'aria-label': t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel'),
                          }}
                          sx={{
                            '& .MuiOutlinedInput-root': { bgcolor: 'background.default' },
                          }}
                        />
                      )}
                    />
                  ) : (
                    <TextField
                      size="small"
                      placeholder={t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel')}
                      value={assignmentLastFour}
                      onChange={(e) => setAssignmentLastFour(e.target.value.replace(/\D/g, '').slice(0, 4))}
                      inputProps={{
                        maxLength: 4,
                        inputMode: 'numeric',
                        'aria-label': t('financeTransactions.billImport.cardMatchingStep.floatingLastFourLabel'),
                      }}
                      sx={{
                        width: 170,
                        '& .MuiOutlinedInput-root': { bgcolor: 'background.default' },
                      }}
                    />
                  )}
                  <Button
                    variant="contained"
                    disabled={assignmentLastFour.trim().length !== 4}
                    onClick={() => assignSelectedTransactionsToBucket(assignmentLastFour)}
                    sx={{ borderRadius: 2, px: 2.25 }}
                  >
                    {t('financeTransactions.billImport.cardMatchingStep.floatingApply')}
                  </Button>
                </Stack>
              </Paper>
            </Box>
          )}
        </Box>
      );
    }

    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.cardMatchingStep.description')}
        </Typography>

        {cards.map((card, ci) => {
          const resolution = cardResolutions[ci];
          const isResolved = !!resolution?.financialAccountId;
          const needsUserInput = !isResolved;
          const availableOptions = getAvailableOptionsForCard(card);

          return (
            <Paper variant="outlined" key={card.reviewCardId} sx={{ mb: 2, p: 2 }}>
              <Stack direction="row" alignItems="center" spacing={1.5} sx={{ mb: 1.5 }}>
                <Avatar sx={{ bgcolor: 'primary.light', width: 36, height: 36 }}>
                  <CardIcon fontSize="small" />
                </Avatar>
                <Box sx={{ flex: 1 }}>
                  <Typography variant="body1" fontWeight="medium">
                    {getReviewCardLabel(card, ci, t)}
                  </Typography>
                  {card.holderName && (
                    <Typography variant="caption" color="text.secondary">{card.holderName}</Typography>
                  )}
                </Box>
                <Chip
                  size="small"
                  icon={isResolved ? <CheckIcon /> : <UnmatchedIcon />}
                  label={
                    isResolved
                      ? t('financeTransactions.billImport.cardMatchingStep.status.associated')
                      : t('financeTransactions.billImport.cardMatchingStep.status.pending')
                  }
                  color={isResolved ? 'success' : 'warning'}
                  variant="outlined"
                />
              </Stack>

              {card.matchAmbiguous && (
                <Alert severity="warning" sx={{ mb: 1.5 }}>
                  {t('financeTransactions.billImport.cardMatchingStep.ambiguousWarning', { lastFourDigits: card.lastFourDigits ?? '----' })}
                </Alert>
              )}

              <FormControl fullWidth size="small">
                <InputLabel>{t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}</InputLabel>
                <Select
                  label={t('financeTransactions.billImport.cardMatchingStep.cardAccountLabel')}
                  value={resolution?.financialAccountId ?? ''}
                  onChange={(e) => {
                    const val = e.target.value as string;
                    setCardResolutions((prev) => {
                      if (!prev[ci]) return prev;
                      const selectedAccount = val ? cardAccountOptionsById.get(val) : undefined;
                      if (val && selectedIssuerKey && selectedAccount?.issuerKey !== selectedIssuerKey) {
                        return prev;
                      }
                      const updated = [...prev];
                      updated[ci] = { ...prev[ci]!, financialAccountId: val || null };
                      return updated;
                    });
                  }}
                >
                  <SelectMenuItem value="" disabled>
                    <em>{t('financeTransactions.billImport.cardMatchingStep.cardAccountPlaceholder')}</em>
                  </SelectMenuItem>
                  {availableOptions.map((opt) => (
                    <SelectMenuItem key={opt.id} value={opt.id}>
                      {opt.name}
                    </SelectMenuItem>
                  ))}
                </Select>
              </FormControl>

              {needsUserInput && !card.matchAmbiguous && (
                <Button
                  size="small"
                  startIcon={<AddIcon />}
                  sx={{ mt: 1 }}
                  onClick={() => {
                    setCreateCardForIndex(ci);
                    setIsCreateCardDrawerOpen(true);
                  }}
                >
                  {t('financeTransactions.billImport.cardMatchingStep.createNewCard')}
                </Button>
              )}

              <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>
                {t('financeTransactions.billImport.cardMatchingStep.txSummary', {
                  count: card.transactions.length,
                  total: card.computedTotal.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }),
                })}
              </Typography>
            </Paper>
          );
        })}

        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end">
          <Button variant="outlined" onClick={handleClose}>{t('financeTransactions.actions.cancel')}</Button>
          <Button
            variant="contained"
            disabled={!allCardsResolved}
            onClick={() => setStep('tx-review')}
          >
            {t('financeTransactions.billImport.cardMatchingStep.reviewTransactions')}
          </Button>
        </Stack>
      </Box>
    );
  };

  const renderTxReview = () => {
    const includedCount = isFlatAssignmentMode
      ? Object.entries(flatTxAssignments).filter(([txIndex, draftCardId]) => !!draftCardId && (flatTxInclusions[Number(txIndex)] ?? true)).length
      : Object.values(txSelections).filter(Boolean).length;
    const hasBillClosingDate = billDateOverrides.closingDate.trim().length > 0;
    const hasBillDueDate = billDateOverrides.dueDate.trim().length > 0;
    const closingDateValue = hasBillClosingDate ? dayjs(billDateOverrides.closingDate) : null;
    const dueDateValue = hasBillDueDate ? dayjs(billDateOverrides.dueDate) : null;
    const dueDateBeforeClosingDate = !!closingDateValue && !!dueDateValue && dueDateValue.isBefore(closingDateValue, 'day');

    const cards = isFlatAssignmentMode && flatSourceCard
      ? draftCards.map((draft) => ({
          reviewCardId: draft.draftCardId,
          lastFourDigits: draft.lastFourDigits,
          transactions: flatSourceCard.transactions
            .map((tx, txIndex) => ({ ...tx, __sourceTxIndex: txIndex }))
            .filter((tx) => flatTxAssignments[tx.__sourceTxIndex] === draft.draftCardId),
          financialAccountId: draft.financialAccountId,
        }))
      : reviewCards.map((card, ci) => ({
          ...card,
          financialAccountId: cardResolutions[ci]?.financialAccountId ?? null,
        }));

    return (
      <Box sx={{ p: 1 }}>
        <Typography variant="body1" color="text.secondary" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.txReviewStep.description')}
        </Typography>

        <Paper variant="outlined" sx={{ p: 2, mb: 2.5 }}>
          <Stack spacing={1.5}>
            <Box>
              <Typography variant="subtitle2">
                {t('financeTransactions.billImport.txReviewStep.billDatesTitle')}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t('financeTransactions.billImport.txReviewStep.billDatesDescription')}
              </Typography>
            </Box>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
              <DateTimePicker
                showTime={false}
                showDate
                size="small"
                label={t('financeTransactions.billImport.txReviewStep.closingDateLabel')}
                value={billDateOverrides.closingDate ? dayjs(billDateOverrides.closingDate) : null}
                onChange={(v) => setBillDateOverrides((prev) => ({ ...prev, closingDate: v ? v.format('YYYY-MM-DD') : '' }))}
                required
                helperText={t('financeTransactions.billImport.txReviewStep.closingDateHint')}
              />
              <DateTimePicker
                showTime={false}
                showDate
                size="small"
                label={t('financeTransactions.billImport.txReviewStep.dueDateLabel')}
                value={billDateOverrides.dueDate ? dayjs(billDateOverrides.dueDate) : null}
                onChange={(v) => setBillDateOverrides((prev) => ({ ...prev, dueDate: v ? v.format('YYYY-MM-DD') : '' }))}
                minDateTime={closingDateValue ?? undefined}
                required
                error={dueDateBeforeClosingDate}
                errorMessage={dueDateBeforeClosingDate ? t('financeTransactions.billImport.txReviewStep.dueDateHint') : undefined}
                helperText={!dueDateBeforeClosingDate ? t('financeTransactions.billImport.txReviewStep.dueDateHint') : undefined}
              />
            </Stack>
          </Stack>
        </Paper>

        {cards.map((card, ci) => {
          const account = cardAccountOptions.find((a) => a.id === card.financialAccountId);
          return (
            <Box key={card.reviewCardId} sx={{ mb: 3 }}>
              <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                <CardIcon fontSize="small" color="action" />
                <Typography variant="subtitle2">
                  {getReviewCardLabel(card, ci, t)}
                  {account && (
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                      — {account.name}
                    </Typography>
                  )}
                </Typography>
              </Stack>

              <Paper variant="outlined">
                <List disablePadding dense>
                  {card.transactions.map((tx: any, ti: number) => {
                    const key = isFlatAssignmentMode ? `flat-${tx.__sourceTxIndex}` : txSelectionKey(card.reviewCardId, ti);
                    const included = isFlatAssignmentMode
                      ? (flatTxInclusions[tx.__sourceTxIndex] ?? true)
                      : (txSelections[key] ?? true);
                    const isDebit = tx.creditDebitType === 'DEBIT';
                    const isBillPayment = !!tx.isBillPayment;
                    const categoryState =
                      txCategorySelections[key] ?? { categoryId: tx.categoryId ?? null, categorySource: tx.categorySource ?? null };
                    const showSuggested = !isBillPayment && categoryState.categorySource === CategorySource.Rule && categoryState.categoryId;
                    const selectedCategoryId = categoryState.categoryId;

                    return (
                      <React.Fragment key={key}>
                        {ti > 0 && <Divider />}
                        <ListItem
                          sx={{ opacity: included ? 1 : 0.4, py: 0.75 }}
                          secondaryAction={
                            <Checkbox
                              edge="end"
                              checked={included}
                              onChange={(e) =>
                                isFlatAssignmentMode
                                  ? setFlatTxInclusions((prev) => ({ ...prev, [tx.__sourceTxIndex]: e.target.checked }))
                                  : setTxSelections((prev) => ({ ...prev, [key]: e.target.checked }))
                              }
                              size="small"
                            />
                          }
                        >
                          <ListItemAvatar sx={{ minWidth: 36 }}>
                            {isBillPayment ? (
                              <TransferIcon fontSize="small" color="info" />
                            ) : (
                              <Box
                                sx={{
                                  width: 8,
                                  height: 8,
                                  borderRadius: '50%',
                                  bgcolor: isDebit ? 'error.main' : 'success.main',
                                  mt: 0.5,
                                }}
                              />
                            )}
                          </ListItemAvatar>
                          <ListItemText
                            primary={
                              <Stack direction="row" alignItems="center" spacing={0.5}>
                                <Typography variant="body2" noWrap sx={{ flex: 1 }}>
                                  {tx.description}
                                </Typography>
                                {isBillPayment && (
                                  <Chip label={t('financeTransactions.billImport.txReviewStep.paymentChip')} size="small" color="info" variant="outlined" sx={{ fontSize: 10 }} />
                                )}
                                {!isBillPayment && (
                                  <Stack direction="row" spacing={0.5} alignItems="center">
                                    <CategorySelect
                                      value={selectedCategoryId}
                                      onChange={(categoryId) => {
                                        setTxCategorySelections((prev) => ({
                                          ...prev,
                                          [key]: {
                                            categoryId,
                                            categorySource: categoryId ? CategorySource.Manual : null,
                                          },
                                        }));
                                      }}
                                      categories={allCategories}
                                      creditDebitType={tx.creditDebitType}
                                    />
                                    {showSuggested && (
                                      <Chip
                                        label={t('financeTransactions.billImport.txReviewStep.suggestedBadge')}
                                        size="small"
                                        color="success"
                                        variant="filled"
                                        sx={{ fontSize: 10 }}
                                      />
                                    )}
                                  </Stack>
                                )}
                                {tx.installment && (
                                  <Chip
                                    label={`${tx.installment.current}/${tx.installment.total}`}
                                    size="small"
                                    variant="outlined"
                                    sx={{ fontSize: 10 }}
                                  />
                                )}
                              </Stack>
                            }
                            secondary={
                              <Stack direction="row" spacing={1} alignItems="center">
                                <Typography variant="caption" color="text.secondary">
                                  {formatTxDisplayDate(tx.date, i18n.language)}
                                </Typography>
                                <Typography
                                  variant="caption"
                                  fontWeight="medium"
                                  color={isDebit ? 'error.main' : 'success.main'}
                                >
                                  {isDebit ? '- ' : '+ '}
                                  {tx.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}
                                </Typography>
                              </Stack>
                            }
                          />
                        </ListItem>
                      </React.Fragment>
                    );
                  })}
                </List>
              </Paper>
            </Box>
          );
        })}

        <Divider sx={{ my: 3 }} />
        <Stack direction="row" spacing={2} justifyContent="flex-end">
          <Button variant="outlined" onClick={() => setStep('card-matching')}>{t('financeTransactions.billImport.common.back')}</Button>
          <Button
            variant="contained"
            onClick={handleConfirm}
            disabled={confirming || includedCount === 0 || !hasBillClosingDate || !hasBillDueDate || dueDateBeforeClosingDate}
            startIcon={confirming ? <CircularProgress size={18} color="inherit" /> : null}
          >
            {confirming
              ? t('financeTransactions.billImport.txReviewStep.confirming')
              : t('financeTransactions.billImport.txReviewStep.confirmButton', { count: includedCount })}
          </Button>
        </Stack>
      </Box>
    );
  };

  const renderResult = () => {
    const total = isFlatAssignmentMode && flatSourceCard
      ? flatSourceCard.transactions.length
      : reviewCards.reduce((sum, c) => sum + c.transactions.length, 0);
    const included = isFlatAssignmentMode
      ? Object.entries(flatTxAssignments).filter(([txIndex, draftCardId]) => !!draftCardId && (flatTxInclusions[Number(txIndex)] ?? true)).length
      : Object.values(txSelections).filter(Boolean).length;

    return (
      <Box sx={{ p: 1 }}>
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('financeTransactions.billImport.resultStep.success', { included, total })}
        </Alert>
        <Button variant="contained" fullWidth onClick={handleClose}>
          {t('financeTransactions.billImport.common.close')}
        </Button>
      </Box>
    );
  };

  const renderError = () => (
    <Box sx={{ p: 1 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('financeTransactions.billImport.errorStep.title')}</Typography>
      <Alert severity="error" sx={{ mb: 2 }}>
        {errorMessage ?? t('financeTransactions.billImport.errorStep.generic')}
      </Alert>
      <Stack direction="row" spacing={2}>
        <Button variant="outlined" onClick={resetToUploadStep}>
          {t('financeTransactions.billImport.errorStep.retry')}
        </Button>
        <Button variant="contained" onClick={handleClose}>{t('financeTransactions.billImport.common.close')}</Button>
      </Stack>
    </Box>
  );

  const stepTitles: Record<BillStep, string> = {
    upload: t('financeTransactions.billImport.steps.uploadTitle'),
    processing: t('financeTransactions.billImport.steps.processingTitle'),
    'card-matching': isFlatAssignmentMode
      ? t('financeTransactions.billImport.steps.assignmentTitle')
      : t('financeTransactions.billImport.steps.cardMatchingTitle'),
    'tx-review': t('financeTransactions.billImport.steps.txReviewTitle'),
    result: t('financeTransactions.billImport.steps.resultTitle'),
    error: t('financeTransactions.billImport.steps.errorTitle'),
  };

  const moneyLocale = i18n.language?.startsWith('en') ? 'en-US' : 'pt-BR';

  if (!open) return null;

  const body =
    step === 'upload' ? renderUpload() :
    step === 'processing' ? renderProcessing() :
    step === 'card-matching' ? renderCardMatching() :
    step === 'tx-review' ? renderTxReview() :
    step === 'result' ? renderResult() :
    renderError();

  const footer =
    step === 'card-matching' && isFlatAssignmentMode ? (
      <Stack direction="row" spacing={2} justifyContent="flex-end">
        <Button variant="outlined" onClick={handleClose}>
          {t('financeTransactions.actions.cancel')}
        </Button>
        <Button
          variant="contained"
          disabled={!allCardsResolved || creatingTemplateCards}
          startIcon={creatingTemplateCards ? <CircularProgress size={16} color="inherit" /> : null}
          onClick={handleFlatContinue}
        >
          {t('financeTransactions.billImport.cardMatchingStep.reviewTransactions')}
        </Button>
      </Stack>
    ) : null;

  const createCardLastFour =
    createDraftCardId !== null
      ? (draftCards.find((draft) => draft.draftCardId === createDraftCardId)?.lastFourDigits || undefined)
      : createCardForIndex !== null
        ? (reviewCards[createCardForIndex]?.lastFourDigits ?? undefined)
        : undefined;
  const createCardClosingDay =
    createDraftCardId !== null
      ? (sharedCardTemplate.closingDay ? Number(sharedCardTemplate.closingDay) : getDayOfMonthFromDateInput(flatSourceCard?.closingDate))
      : createCardForIndex !== null
        ? getDayOfMonthFromDateInput(reviewCards[createCardForIndex]?.closingDate)
        : undefined;
  const createCardDueDay =
    createDraftCardId !== null
      ? (sharedCardTemplate.dueDay ? Number(sharedCardTemplate.dueDay) : getDayOfMonthFromDateInput(flatSourceCard?.dueDate))
      : createCardForIndex !== null
        ? getDayOfMonthFromDateInput(reviewCards[createCardForIndex]?.dueDate)
        : undefined;

  return (
    <>
      <Drawer open={open} onClose={handleClose} anchor="right" size="md">
        <Drawer.Header title={stepTitles[step]} showCloseButton />
        <Drawer.Body>{body}</Drawer.Body>
        {footer && <Drawer.Footer>{footer}</Drawer.Footer>}
      </Drawer>

      <CreateFinancialAccountDrawer
        open={isCreateCardDrawerOpen}
        initialType="CARD"
        initialMaskedNumber={createCardLastFour}
        initialClosingDay={createCardClosingDay}
        initialDueDay={createCardDueDay}
        initialIssuerId={selectedIssuerDefaults.issuerId}
        initialIssuerName={selectedIssuerDefaults.issuerName}
        onClose={() => {
          setIsCreateCardDrawerOpen(false);
          setCreateCardForIndex(null);
          setCreateDraftCardId(null);
        }}
        onCreated={(account) => {
          const createdAccountOption: CardAccountOption = {
            id: account.id,
            name: account.name,
            issuerId: account.issuerId ?? null,
            issuerName: account.issuerName ?? null,
            issuerKey: toIssuerKey(account.issuerId, account.issuerName),
            maskedNumber: createCardLastFour ?? null,
          };

          setInlineCreatedAccounts((prev) => {
            const withoutCurrent = prev.filter((existing) => existing.id !== account.id);
            return [...withoutCurrent, createdAccountOption];
          });

          const canAssociateCreatedCard =
            !selectedIssuerKey || createdAccountOption.issuerKey === selectedIssuerKey;

          if (createDraftCardId !== null) {
            setDraftCards((prev) =>
              prev.map((draft) =>
                draft.draftCardId === createDraftCardId
                  ? {
                      ...draft,
                      financialAccountId: canAssociateCreatedCard ? account.id : null,
                      lastFourDigits: draft.lastFourDigits || createCardLastFour || '',
                    }
                  : draft,
              ),
            );
          } else if (createCardForIndex !== null) {
            setCardResolutions((prev) => {
              const cards = reviewCards;
              const base = cards.map((c, idx) => {
                const existing = prev[idx];
                return {
                  reviewCardId: c.reviewCardId,
                  financialAccountId:
                    existing?.financialAccountId ?? (c.matchAmbiguous ? null : (c.matchedFinancialAccountId ?? null)),
                };
              });

              if (!base[createCardForIndex]) return prev;
              base[createCardForIndex] = {
                ...base[createCardForIndex],
                financialAccountId: canAssociateCreatedCard ? account.id : null,
              };
              return base;
            });
          }
          setIsCreateCardDrawerOpen(false);
          setCreateCardForIndex(null);
          setCreateDraftCardId(null);
        }}
      />
    </>
  );
}
