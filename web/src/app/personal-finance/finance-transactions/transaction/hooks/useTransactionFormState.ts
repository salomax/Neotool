import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@apollo/client/react';
import { useTheme } from '@mui/material';
import dayjs from 'dayjs';
import {
  type FinancialAccount,
  type TransactionCategory,
  CreditDebitType,
  TransactionType,
  CategoryApplicableTo,
  type Transaction,
} from '@/lib/graphql/types/__generated__/graphql';
import {
  FINANCIAL_ACCOUNTS,
  TRANSACTION_CATEGORIES,
  TRANSACTION_TAGS,
} from '@/lib/graphql/operations/banking/queries';
import { getFinanceMinAllowedDate } from '../../constants/dateWindow';
import type { CreditDebitTypeExtended } from '../TypeSelector';
import type { TransactionCreateDraft } from '../transactionFormShared';

interface UseTransactionFormStateProps {
  isOpen: boolean;
  initialValues?: Transaction;
  initialDraft?: TransactionCreateDraft;
  fixedCreditDebitType?: CreditDebitTypeExtended;
  selectedAccountId: string | null;
  selectedMonth?: number;
  selectedYear?: number;
}

export interface TransactionFormState {
  creditDebitType: CreditDebitTypeExtended;
  setCreditDebitType: (v: CreditDebitTypeExtended) => void;
  amount: number | undefined;
  setAmount: (v: number | undefined) => void;
  description: string;
  setDescription: (v: string) => void;
  date: dayjs.Dayjs | null;
  setDate: (v: dayjs.Dayjs | null) => void;
  defaultCreateDate: dayjs.Dayjs;
  accountId: string;
  setAccountId: (v: string) => void;
  categoryId: string | null;
  setCategoryId: (v: string | null) => void;
  merchantId: string | null;
  setMerchantId: (v: string | null) => void;
  tags: string[];
  setTags: (v: string[]) => void;
  accounts: FinancialAccount[];
  filteredCategories: TransactionCategory[];
  availableTags: string[];
  isBalanceAdjustmentEdit: boolean;
  isDateOutsideWindow: boolean;
  isDateInFuture: boolean;
  isDateInvalidForSave: boolean;
  inputBottomBorderSx: object;
  gridTemplateColumns: string;
  amountGridColumn: string;
  colSpan: number;
  minAllowedDate: dayjs.Dayjs;
  maxAllowedDate: dayjs.Dayjs;
}

export function useTransactionFormState({
  isOpen,
  initialValues,
  initialDraft,
  fixedCreditDebitType,
  selectedAccountId,
  selectedMonth,
  selectedYear,
}: UseTransactionFormStateProps): TransactionFormState {
  const theme = useTheme();

  const minAllowedDate = useMemo(() => getFinanceMinAllowedDate(), []);
  const maxAllowedDate = useMemo(() => dayjs().startOf('day'), []);

  const defaultCreateDate = useMemo(() => {
    if (typeof selectedMonth !== 'number' || typeof selectedYear !== 'number') return dayjs();
    if (selectedMonth < 1 || selectedMonth > 12) return dayjs();
    return dayjs().year(selectedYear).month(selectedMonth - 1).date(1).startOf('day');
  }, [selectedMonth, selectedYear]);

  const [creditDebitType, setCreditDebitType] = useState<CreditDebitTypeExtended>(
    fixedCreditDebitType ??
      (initialValues?.type === TransactionType.Transfer
        ? (initialValues.creditDebitType === CreditDebitType.Credit ? 'TRANSFER_IN' : 'TRANSFER_OUT')
        : (initialValues?.creditDebitType || initialDraft?.creditDebitType || CreditDebitType.Debit)),
  );
  const [amount, setAmount] = useState<number | undefined>(
    initialValues?.amount ? Math.abs(initialValues.amount) : initialDraft?.amount,
  );
  const [description, setDescription] = useState(initialValues?.description || initialDraft?.description || '');
  const [date, setDate] = useState<dayjs.Dayjs | null>(
    initialValues?.date
      ? dayjs(initialValues.date as string)
      : initialDraft?.date
        ? dayjs(initialDraft.date)
        : defaultCreateDate,
  );
  const [accountId, setAccountId] = useState<string>(
    initialValues?.financialAccountId || initialDraft?.accountId || selectedAccountId || '',
  );
  const [categoryId, setCategoryId] = useState<string | null>(
    initialValues?.type === TransactionType.BillPayment
      ? null
      : (initialValues?.category?.id ?? initialDraft?.categoryId ?? null),
  );
  const [merchantId, setMerchantId] = useState<string | null>(initialValues?.merchant?.id ?? initialDraft?.merchantId ?? null);
  const [tags, setTags] = useState<string[]>(initialValues?.tags || initialDraft?.tags || []);

  const { data: accountsData } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS);
  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(TRANSACTION_CATEGORIES);
  const { data: tagsData } = useQuery<{ transactionTags: { tag: string }[] }>(TRANSACTION_TAGS);

  const isBalanceAdjustmentEdit =
    Boolean(initialValues) && initialValues?.type === TransactionType.BalanceAdjustment;

  const isDateOutsideWindow = Boolean(date && date.isBefore(minAllowedDate, 'day'));
  const isDateInFuture = Boolean(date && date.isAfter(maxAllowedDate, 'day'));
  const isDateInvalidForSave =
    isDateOutsideWindow || isDateInFuture || Boolean(date && !date.isValid());

  const accounts = useMemo(
    () => accountsData?.financialAccounts ?? [],
    [accountsData?.financialAccounts],
  );

  const filteredCategories = useMemo(
    () =>
      (categoriesData?.transactionCategories ?? []).filter((cat) => {
        if (creditDebitType === 'TRANSFER_IN' || creditDebitType === 'TRANSFER_OUT') {
          return cat.applicableTo === CategoryApplicableTo.Transfer || cat.applicableTo === CategoryApplicableTo.Any;
        } else if (creditDebitType === CreditDebitType.Credit) {
          return cat.applicableTo === CategoryApplicableTo.Income || cat.applicableTo === CategoryApplicableTo.Any;
        } else {
          return cat.applicableTo === CategoryApplicableTo.Expense || cat.applicableTo === CategoryApplicableTo.Any;
        }
      }),
    [categoriesData?.transactionCategories, creditDebitType],
  );

  const availableTags = useMemo(
    () => tagsData?.transactionTags?.map((tg) => tg.tag) ?? [],
    [tagsData?.transactionTags],
  );

  const inputBottomBorderSx = useMemo(
    () => ({
      borderBottom: `1px solid ${theme.palette.divider}`,
      transition: 'border-color 0.2s, border-bottom-width 0.2s',
      '&.Mui-focused': {
        borderBottom: `2px solid ${theme.palette.primary.main}`,
      },
    }),
    [theme],
  );

  const gridTemplateColumns = useMemo(() => {
    if (isBalanceAdjustmentEdit) {
      return selectedAccountId
        ? '68px minmax(180px, 1.6fr) minmax(100px, 1fr) 52px'
        : '68px minmax(180px, 1.6fr) minmax(140px, 0.8fr) minmax(100px, 1fr) 52px';
    }
    return selectedAccountId
      ? '68px minmax(180px, 1.6fr) minmax(0, .8fr) minmax(auto, .8fr) minmax(120px, 1fr) 52px'
      : '68px minmax(180px, 1.6fr) minmax(0, .8fr) minmax(auto, .8fr) minmax(140px, 0.8fr) minmax(120px, 1fr) 52px';
  }, [isBalanceAdjustmentEdit, selectedAccountId]);

  const amountGridColumn = isBalanceAdjustmentEdit
    ? (selectedAccountId ? '3 / 4' : '4 / 5')
    : (selectedAccountId ? '5 / 6' : '6 / 7');

  const colSpan = selectedAccountId ? 7 : 8;

  // Reset/init form when opened or initialValues change
  useEffect(() => {
    if (!isOpen) return;
    if (initialValues) {
      setCreditDebitType(
        fixedCreditDebitType ??
        initialValues.type === TransactionType.Transfer
          ? (initialValues.creditDebitType === CreditDebitType.Credit ? 'TRANSFER_IN' : 'TRANSFER_OUT')
          : initialValues.creditDebitType,
      );
      setAmount(Math.abs(initialValues.amount));
      setDescription(initialValues.description);
      setDate(dayjs(initialValues.date as string));
      setAccountId(initialValues.financialAccountId);
      setCategoryId(initialValues.type === TransactionType.BillPayment ? null : (initialValues.category?.id ?? null));
      setMerchantId(initialValues.merchant?.id ?? null);
      setTags(initialValues.tags || []);
    } else {
      setCreditDebitType(fixedCreditDebitType ?? (initialDraft?.creditDebitType || CreditDebitType.Debit));
      setAmount(initialDraft?.amount);
      setDescription(initialDraft?.description || '');
      setDate(initialDraft?.date ? dayjs(initialDraft.date) : defaultCreateDate);
      setCategoryId(initialDraft?.categoryId ?? null);
      setMerchantId(initialDraft?.merchantId ?? null);
      setTags(initialDraft?.tags || []);
      if (initialDraft?.accountId) {
        setAccountId(initialDraft.accountId);
      } else if (selectedAccountId) {
        setAccountId(selectedAccountId);
      } else if (accounts.length === 1) {
        setAccountId(accounts[0]?.id ?? '');
      } else {
        setAccountId('');
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, initialValues, initialDraft, fixedCreditDebitType]);

  // Keep accountId in sync with selectedAccountId while form is open (new transactions only)
  useEffect(() => {
    if (!isOpen || initialValues) return;
    if (initialDraft?.accountId) {
      setAccountId(initialDraft.accountId);
    } else if (selectedAccountId) {
      setAccountId(selectedAccountId);
    } else if (accounts.length === 1) {
      setAccountId(accounts[0]?.id ?? '');
    }
  }, [isOpen, initialValues, initialDraft?.accountId, selectedAccountId, accounts]);

  return {
    creditDebitType,
    setCreditDebitType,
    amount,
    setAmount,
    description,
    setDescription,
    date,
    setDate,
    defaultCreateDate,
    accountId,
    setAccountId,
    categoryId,
    setCategoryId,
    merchantId,
    setMerchantId,
    tags,
    setTags,
    accounts,
    filteredCategories,
    availableTags,
    isBalanceAdjustmentEdit,
    isDateOutsideWindow,
    isDateInFuture,
    isDateInvalidForSave,
    inputBottomBorderSx,
    gridTemplateColumns,
    amountGridColumn,
    colSpan,
    minAllowedDate,
    maxAllowedDate,
  };
}
