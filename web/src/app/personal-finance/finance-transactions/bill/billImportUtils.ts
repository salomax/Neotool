import {
  ACCEPTED_EXTENSIONS,
  MAX_FILE_BYTES,
  type BillDateValidation,
  type CardAccountOption,
  type DueDateResolutionCard,
  type SharedCardTemplate,
} from './billImportTypes';
import dayjs, { type Dayjs } from 'dayjs';
import { parseDateOnly } from '@/shared/utils/date/dateOnly';
import i18n from '@/shared/i18n/config';

// ── Issuer key helpers ───────────────────────────────────────────────────────

export function toIssuerKey(issuerId?: string | null, issuerName?: string | null): string | null {
  if (issuerId) return `id:${issuerId}`;
  const normalizedName = issuerName?.trim().toLowerCase();
  return normalizedName ? `name:${normalizedName}` : null;
}

// ── Date helpers ─────────────────────────────────────────────────────────────

export function getDayOfMonthFromDateInput(date: string | null | undefined): number | undefined {
  const parsed = parseDateOnly(date);
  if (!parsed) return undefined;
  const day = parsed.getDate();
  return day >= 1 && day <= 31 ? day : undefined;
}

export function formatTxDisplayDate(date: string | null | undefined, locale?: string): string {
  if (!date) return '-';
  const dateObj = parseDateOnly(date);
  if (!dateObj) return date;
  return new Intl.DateTimeFormat(locale, { dateStyle: 'short' }).format(dateObj);
}

export function canResolveDueDateWithoutManualInput(
  cards: DueDateResolutionCard[],
  cardAccountOptionsById: Map<string, CardAccountOption>,
): boolean {
  if (cards.length === 0) return false;

  return cards.every((card) => {
    if (card.importedDueDate) return true;
    if (!card.financialAccountId) return false;

    const dueDay = cardAccountOptionsById.get(card.financialAccountId)?.dueDay;
    return dueDay != null && dueDay >= 1 && dueDay <= 31;
  });
}

export function getBillDateValidation(
  closingDateInput: Dayjs | null,
  dueDateInput: Dayjs | null,
  canResolveDueDateWithoutManualInput = false,
): BillDateValidation {
  const hasBillClosingDate = closingDateInput !== null;
  const closingDateInvalid = Boolean(closingDateInput && !closingDateInput.isValid());
  const dueDateInvalid = Boolean(dueDateInput && !dueDateInput.isValid());
  const closingDateValue = closingDateInput && closingDateInput.isValid() ? closingDateInput : null;
  const dueDateValue = dueDateInput && dueDateInput.isValid() ? dueDateInput : null;
  const dueDateBeforeClosingDate =
    !!closingDateValue && !!dueDateValue && dueDateValue.isBefore(closingDateValue, 'day');
  const hasManualDueDate = dueDateValue !== null;
  const dueDateMissing = !hasManualDueDate && !canResolveDueDateWithoutManualInput;

  return {
    hasBillClosingDate,
    closingDateInvalid,
    dueDateInvalid,
    dueDateBeforeClosingDate,
    dueDateMissing,
  };
}

export function isBillDateConfirmDisabled(validation: BillDateValidation): boolean {
  return (
    !validation.hasBillClosingDate ||
    validation.closingDateInvalid ||
    validation.dueDateInvalid ||
    validation.dueDateBeforeClosingDate ||
    validation.dueDateMissing
  );
}

export function getBillDateValidationFromOverrides(
  billDateOverrides: { closingDate: string; dueDate: string },
): BillDateValidation {
  return getBillDateValidation(
    billDateOverrides.closingDate ? dayjs(billDateOverrides.closingDate) : null,
    billDateOverrides.dueDate ? dayjs(billDateOverrides.dueDate) : null,
  );
}

// ── File helpers ─────────────────────────────────────────────────────────────

export function getExtension(name: string): string {
  const i = name.lastIndexOf('.');
  return i >= 0 ? name.slice(i).toLowerCase() : '';
}

export function fileToBase64(file: File): Promise<string> {
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

/** Detects PDF encryption by scanning head/tail chunks for `/Encrypt`. */
export async function detectEncryptedPdf(file: File): Promise<boolean> {
  const raw = new Uint8Array(await file.arrayBuffer());
  if (raw.length === 0) return false;
  const chunkSize = Math.min(raw.length, 256 * 1024);
  const decoder = new TextDecoder('latin1');
  const head = decoder.decode(raw.subarray(0, chunkSize));
  const tail = decoder.decode(raw.subarray(Math.max(0, raw.length - chunkSize)));
  return /\/Encrypt\b/.test(`${head}\n${tail}`);
}

export function validateFile(
  f: File,
  t: (key: string, params?: Record<string, unknown>) => string,
): string | null {
  const ext = getExtension(f.name);
  if (!ACCEPTED_EXTENSIONS.includes(ext as (typeof ACCEPTED_EXTENSIONS)[number])) {
    return t('financeTransactions.import.uploadStep.unsupportedType');
  }
  if (f.size > MAX_FILE_BYTES) {
    return t('financeTransactions.import.uploadStep.fileTooLarge', { maxMb: MAX_FILE_BYTES / (1024 * 1024) });
  }
  return null;
}

// ── Card label helpers ───────────────────────────────────────────────────────

export function txSelectionKey(reviewCardId: string, txIndex: number): string {
  return `${reviewCardId}-${txIndex}`;
}

export function getReviewCardLabel(
  card: { lastFourDigits?: string | null },
  index: number,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (card.lastFourDigits?.trim()) {
    return `•••• ${card.lastFourDigits}`;
  }
  return t('financeTransactions.billImport.cardMatchingStep.unnamedCard', { index: index + 1 });
}

export function resolveFriendlyImportErrorMessage(
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
  if (
    normalizedCode === 'import_file_format_not_supported' ||
    normalizedMessage.includes('import_file_format_not_supported')
  ) {
    const lng = (i18n.language || i18n.resolvedLanguage || 'en').toLowerCase().startsWith('en') ? 'en' : 'pt';
    return i18n.getFixedT(lng, 'common')('errors.IMPORT_FILE_FORMAT_NOT_SUPPORTED', {
      defaultValue: 'Import file format is not supported.',
    });
  }
  return t('financeTransactions.billImport.errorStep.generic');
}

// ── Card name helpers ────────────────────────────────────────────────────────

export function formatCardDescriptor(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export function buildDraftCardName(
  template: SharedCardTemplate,
  issuerLabel: string,
  lastFourDigitsPlaceholder: string,
  fallbackBaseName: string,
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
  const baseName = parts.join(' ').trim() || fallbackBaseName;
  return `${baseName} ${lastFourDigitsPlaceholder}`;
}

export function getMoneyLocale(language?: string): string {
  return language?.startsWith('en') ? 'en-US' : 'pt-BR';
}

export function buildCardAccountOption(account: {
  id: string;
  name: string;
  issuer?: { id?: string | null; name?: string | null } | null;
  issuerName?: string | null;
  issuerId?: string | null;
  cardData?: { maskedNumber?: string | null; isMultiCard?: boolean | null; dueDay?: number | null } | null;
}): CardAccountOption {
  const issuerId = account.issuer?.id ?? account.issuerId ?? null;
  const issuerName = account.issuer?.name ?? account.issuerName ?? null;
  const dueDay = account.cardData?.dueDay ?? null;
  return {
    id: account.id,
    name: account.name,
    issuerId,
    issuerName,
    issuerKey: toIssuerKey(issuerId, issuerName),
    maskedNumber: account.cardData?.maskedNumber ?? null,
    isMultiCard: account.cardData?.isMultiCard ?? false,
    dueDay: dueDay != null && dueDay >= 1 && dueDay <= 31 ? dueDay : null,
  };
}
