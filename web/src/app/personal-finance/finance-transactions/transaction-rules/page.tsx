'use client';

import React from 'react';
import { Container } from '@mui/material';
import { Page } from '@/shared/components/ui/layout';
import { usePageTitle } from '@/shared/hooks/ui';
import { TransactionRulesManagement } from './TransactionRulesManagement';

export default function TransactionRulesPage() {
  usePageTitle('Regras de Movimentação');

  return (
    <Page>
      <Container maxWidth="xl" sx={{ py: 4, height: '100%', display: 'flex', flexDirection: 'column' }}>
        <TransactionRulesManagement />
      </Container>
    </Page>
  );
}
