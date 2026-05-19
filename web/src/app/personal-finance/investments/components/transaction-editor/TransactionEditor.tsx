'use client';

import * as React from 'react';
import { TransactionEditorRouter, type TransactionEditorProps } from './TransactionEditorRouter';

export type { TransactionEditorProps };

export const TransactionEditor = React.memo(function TransactionEditor(props: TransactionEditorProps) {
  if (!props.open || !props.intent) return null;
  return <TransactionEditorRouter {...props} />;
});
