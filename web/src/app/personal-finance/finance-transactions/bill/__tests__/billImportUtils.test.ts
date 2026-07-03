import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import {
  canResolveDueDateWithoutManualInput,
  getBillDateValidation,
  isBillDateConfirmDisabled,
  resolveFriendlyImportErrorMessage,
} from '../billImportUtils';import type { CardAccountOption } from '../billImportTypes';

function account(id: string, dueDay: number | null): CardAccountOption {
  return {
    id,
    name: `Card ${id}`,
    issuerId: null,
    issuerName: null,
    issuerKey: null,
    maskedNumber: null,
    isMultiCard: false,
    dueDay,
  };
}

describe('bill import due date validation', () => {
  const accountsById = new Map([
    ['with-due-day', account('with-due-day', 15)],
    ['without-due-day', account('without-due-day', null)],
  ]);

  it('blocks confirmation when due date is missing and no fallback due day exists', () => {
    const canResolve = canResolveDueDateWithoutManualInput(
      [{ importedDueDate: null, financialAccountId: 'without-due-day' }],
      accountsById,
    );
    const validation = getBillDateValidation(dayjs('2026-02-07'), null, canResolve);

    expect(canResolve).toBe(false);
    expect(validation.dueDateMissing).toBe(true);
    expect(isBillDateConfirmDisabled(validation)).toBe(true);
  });

  it('allows confirmation when due date is missing but account has dueDay', () => {
    const canResolve = canResolveDueDateWithoutManualInput(
      [{ importedDueDate: null, financialAccountId: 'with-due-day' }],
      accountsById,
    );
    const validation = getBillDateValidation(dayjs('2026-02-07'), null, canResolve);

    expect(canResolve).toBe(true);
    expect(validation.dueDateMissing).toBe(false);
    expect(isBillDateConfirmDisabled(validation)).toBe(false);
  });

  it('allows confirmation when imported due date exists without manual entry', () => {
    const canResolve = canResolveDueDateWithoutManualInput(
      [{ importedDueDate: '2026-02-15', financialAccountId: 'without-due-day' }],
      accountsById,
    );
    const validation = getBillDateValidation(dayjs('2026-02-07'), null, canResolve);

    expect(canResolve).toBe(true);
    expect(validation.dueDateMissing).toBe(false);
    expect(isBillDateConfirmDisabled(validation)).toBe(false);
  });

  it('requires every resolved card to have a due-date source in multi-card review', () => {
    const canResolve = canResolveDueDateWithoutManualInput(
      [
        { importedDueDate: null, financialAccountId: 'with-due-day' },
        { importedDueDate: null, financialAccountId: 'without-due-day' },
      ],
      accountsById,
    );

    expect(canResolve).toBe(false);
  });

  it('keeps existing due-date ordering validation when manual due date is provided', () => {
    const validation = getBillDateValidation(dayjs('2026-02-15'), dayjs('2026-02-07'), false);

    expect(validation.dueDateBeforeClosingDate).toBe(true);
    expect(isBillDateConfirmDisabled(validation)).toBe(true);
  });
});

describe('resolveFriendlyImportErrorMessage', () => {
  const t = (key: string) => key;

  it('returns format-not-supported message for IMPORT_FILE_FORMAT_NOT_SUPPORTED', () => {
    const message = resolveFriendlyImportErrorMessage(t, 'IMPORT_FILE_FORMAT_NOT_SUPPORTED');

    expect(message).not.toBe('financeTransactions.billImport.errorStep.generic');
    expect([
      'Import file format is not supported.',
      'O formato do arquivo de importação não é suportado.',
    ]).toContain(message);
  });

  it('falls back to generic bill import message for unknown codes', () => {
    expect(resolveFriendlyImportErrorMessage(t, 'UNKNOWN_ERROR')).toBe(
      'financeTransactions.billImport.errorStep.generic',
    );
  });
});
