'use client';

import { useEffect } from 'react';

type TransactionsImportedDetail = {
  source: 'file' | 'bill';
  financialAccountId?: string | null;
};

const target = new EventTarget();
const EVENT_TRANSACTIONS_IMPORTED = 'finance-transactions:imported';

export function publishTransactionsImported(detail: TransactionsImportedDetail) {
  target.dispatchEvent(new CustomEvent<TransactionsImportedDetail>(EVENT_TRANSACTIONS_IMPORTED, { detail }));
}

export function addTransactionsImportedListener(
  listener: (detail: TransactionsImportedDetail) => void,
) {
  const handler = (e: Event) => {
    const ce = e as CustomEvent<TransactionsImportedDetail>;
    listener(ce.detail);
  };
  target.addEventListener(EVENT_TRANSACTIONS_IMPORTED, handler as any);
  return () => target.removeEventListener(EVENT_TRANSACTIONS_IMPORTED, handler as any);
}

export function useOnTransactionsImported(
  fn: (detail: TransactionsImportedDetail) => void,
) {
  useEffect(() => addTransactionsImportedListener(fn), [fn]);
}
