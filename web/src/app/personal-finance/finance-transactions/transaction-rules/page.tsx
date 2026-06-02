'use client';

import React from 'react';
import { Box, Container, Stack, Typography, useTheme } from '@mui/material';
import { useSearchParams } from 'next/navigation';
import { Page } from '@/shared/components/ui/layout';
import { usePageTitle } from '@/shared/hooks/ui';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { TransactionRulesManagement } from './TransactionRulesManagement';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

export default function TransactionRulesPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  usePageTitle(t('financeTransactions.title'));
  const theme = useTheme();
  const searchParams = useSearchParams();

  const initialTabParam = searchParams.get('tab');
  const initialTab =
    initialTabParam === 'automatic' || initialTabParam === 'manual'
      ? (initialTabParam as 'automatic' | 'manual')
      : undefined;
  const initialEditRuleId = searchParams.get('editRuleId');

  const [helpOpen, setHelpOpen] = React.useState(false);

  return (
    <>
      <Page>
        <Container maxWidth="xl" sx={{ py: 4, height: '100%', display: 'flex', flexDirection: 'column' }}>
          <TransactionRulesManagement
            onOpenHelp={() => setHelpOpen(true)}
            initialTab={initialTab}
            initialEditRuleId={initialEditRuleId}
          />
        </Container>
      </Page>
      <Drawer open={helpOpen} onClose={() => setHelpOpen(false)} anchor="right" size="md">
        <Drawer.Header title={t('financeTransactions.transactionRules.helpDrawer.title')} showCloseButton={true} />
        <Drawer.Body>
          <Box>
            <Stack spacing={2.5}>
              <Typography variant="body2" color="text.secondary">
                {t('financeTransactions.transactionRules.helpDrawer.intro')}
              </Typography>

              <Stack spacing={3}>
                <Box
                  sx={{
                    p: 2,
                    borderRadius: `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`,
                    boxShadow: theme.shadows[3],
                    bgcolor: 'background.default',
                    color: 'text.primary',
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="flex-start">
                    <Box
                      sx={{
                        width: 28,
                        height: 28,
                        borderRadius: 999,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        bgcolor: 'primary.main',
                        color: 'primary.contrastText',
                        fontSize: '0.875rem',
                        fontWeight: 700,
                        mt: 0.25,
                      }}
                    >
                      1
                    </Box>
                    <Typography variant="body2" color="text.primary">
                      {t('financeTransactions.transactionRules.helpDrawer.items.item1')}
                    </Typography>
                  </Stack>
                </Box>

                <Box
                  sx={{
                    p: 2,
                    borderRadius: `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`,
                    boxShadow: theme.shadows[3],
                    bgcolor: 'background.default',
                    color: 'text.primary',
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="flex-start">
                    <Box
                      sx={{
                        width: 28,
                        height: 28,
                        borderRadius: 999,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        bgcolor: 'primary.main',
                        color: 'primary.contrastText',
                        fontSize: '0.875rem',
                        fontWeight: 700,
                        mt: 0.25,
                      }}
                    >
                      2
                    </Box>
                    <Typography variant="body2" color="text.primary">
                      {t('financeTransactions.transactionRules.helpDrawer.items.item2')}
                    </Typography>
                  </Stack>
                </Box>

                <Box
                  sx={{
                    p: 2,
                    borderRadius: `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`,
                    boxShadow: theme.shadows[3],
                    bgcolor: 'background.default',
                    color: 'text.primary',
                  }}
                >
                  <Stack direction="row" spacing={2} alignItems="flex-start">
                    <Box
                      sx={{
                        width: 28,
                        height: 28,
                        borderRadius: 999,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexShrink: 0,
                        bgcolor: 'primary.main',
                        color: 'primary.contrastText',
                        fontSize: '0.875rem',
                        fontWeight: 700,
                        mt: 0.25,
                      }}
                    >
                      3
                    </Box>
                    <Typography variant="body2" color="text.primary">
                      {t('financeTransactions.transactionRules.helpDrawer.items.item3')}
                    </Typography>
                  </Stack>
                </Box>
              </Stack>
            </Stack>
          </Box>
        </Drawer.Body>
      </Drawer>
    </>
  );
}
