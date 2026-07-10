'use client';
import * as React from 'react';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import { alpha, useTheme } from '@mui/material/styles';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useTranslation } from 'react-i18next';
import { LogoMark } from '@/shared/ui/brand/LogoMark';
import { useAuthorization } from '@/shared/providers';
import { useFeatureFlagEnabled } from '@/shared/hooks/useFeatureFlag';

import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import InsertChartIcon from '@mui/icons-material/InsertChart';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import RepeatIcon from '@mui/icons-material/Repeat';

type NavItem = { href: string; label: string; icon: React.ElementType };

export const RAIL_W = 84;

function getActiveHref(pathname: string, items: NavItem[]): string | null {
  let bestMatch: string | null = null;
  let bestMatchLength = -1;

  for (const item of items) {
    const href = item.href;
    const matches = pathname === href || (href !== '/' && pathname.startsWith(`${href}/`));

    if (!matches) continue;
    if (href.length <= bestMatchLength) continue;

    bestMatch = href;
    bestMatchLength = href.length;
  }

  return bestMatch;
}

export function SidebarRail() {
  const theme = useTheme();
  const pathname = usePathname();
  const { t } = useTranslation('common');
  const { hasAny } = useAuthorization();
  const personalFinanceEnabled = useFeatureFlagEnabled('personal-finance-dashboard-enabled');
  const pricingEnabled = useFeatureFlagEnabled('pricing-enabled');
  const purchaseWishlistEnabled = useFeatureFlagEnabled('purchase-wishlist-enabled');

  // Check if user has any authorization management permissions
  const hasAuthorizationAccess = hasAny([
    'security:user:view',
    'security:user:save',
    'security:user:delete',
    'security:role:view',
    'security:role:save',
    'security:role:delete',
    'security:group:view',
    'security:group:save',
    'security:group:delete',
  ]);

  const NAV_TOP: NavItem[] = [
    ...(personalFinanceEnabled ? [{
      href: '/personal-finance',
      label: t('routes.personalFinance'),
      icon: InsertChartIcon,
    }, {
      href: '/personal-finance/finance-transactions',
      label: 'Movimentações Financeiras',
      icon: SwapHorizIcon,
    }, {
      href: '/personal-finance/investments',
      label: 'Carteira de Investimentos',
      icon: SavingsOutlinedIcon,
    }, ...(pricingEnabled ? [{
      href: '/personal-finance/prices-consumption',
      label: 'Preços & Consumo',
      icon: ShoppingCartOutlinedIcon,
    }] : []), {
      href: '/personal-finance/recurring-payments',
      label: 'Pagamentos recorrentes',
      icon: RepeatIcon,
    }, ...(purchaseWishlistEnabled ? [{
      href: '/personal-finance/purchase-wishlist',
      label: t('routes.purchaseWishlist'),
      icon: ShoppingCartOutlinedIcon,
    }] : [])] : []),
    {
      href: '/financial-data',
      label: t('routes.financialData'),
      icon: AccountBalanceIcon,
    },
    {
      href: '/investments',
      label: t('routes.investments'),
      icon: TrendingUpIcon,
    },
  ];

  // Only show Settings if user has authorization permissions
  const NAV_BOTTOM: NavItem[] = hasAuthorizationAccess
    ? [{ href: '/settings', label: t('routes.settings'), icon: SettingsRoundedIcon }]
    : [];

  const activeHref = getActiveHref(pathname ?? '', [...NAV_TOP, ...NAV_BOTTOM]);

  const sidebarBg = (theme as any).custom?.palette?.sidebarBg || '#ffffff';
  const sidebarIcon = (theme as any).custom?.palette?.sidebarIcon || '#728096';
  // Use 'lg' radius (12px) from tokens if available, otherwise fallback to 12
  const borderRadius = (theme as any).custom?.radius?.lg ? `${(theme as any).custom.radius.lg}px` : '12px';

  return (
    <Box
      component="aside"
      sx={{
        position: 'fixed',
        left: 0,
        top: 0,
        bottom: 0,
        width: RAIL_W,
        bgcolor: sidebarBg,
        color: sidebarIcon,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        py: 1.5,
        gap: 0,
        zIndex: (t) => t.zIndex.drawer - 1,
        borderRight: '1px solid',
        borderColor: 'divider',
      }}
    >
      {/* Logo */}
      <Tooltip title="Home" placement="right">
        <Box
          component={Link}
          href="/"
          data-testid="sidebar-rail-home-link"
          sx={{
            width: 48,
            height: 48,
            borderRadius: borderRadius,
            display: 'grid',
            placeItems: 'center',
            mt: 0.5,
            cursor: 'pointer',
            bgcolor: 'transparent',
          }}
          aria-label="Go to home page"
        >
          <LogoMark 
            variant={theme.palette.mode === 'dark' ? 'white' : 'blue'}
            width={40} 
            height={40} 
          />
        </Box>
      </Tooltip>

      <Divider sx={{ my: 2, width: '56%', borderColor: alpha(sidebarIcon, 0.25) }} />

      {/* Top navigation items */}
      <Stack spacing={{ xs: 1.2, sm: 1.6 }} sx={{ mt: 0.5 }}>
        {NAV_TOP.map((item) => {
          const Icon = item.icon as any;
          const active = item.href === activeHref;

          return (
            <Tooltip key={item.href} title={item.label} placement="right">
              <IconButton
                LinkComponent={Link}
                href={item.href}
                aria-label={item.label}
                size="large"
                sx={{
                  position: 'relative',
                  color: active ? 'primary.main' : sidebarIcon,
                  opacity: active ? 1 : 0.85,
                  '&:hover': {
                    bgcolor: active
                      ? alpha(theme.palette.primary.main, 0.16)
                      : alpha(theme.palette.text.primary, 0.08),
                    opacity: 1,
                  },
                  bgcolor: active ? alpha(theme.palette.primary.main, 0.12) : 'transparent',
                  width: 48,
                  height: 48,
                  borderRadius: borderRadius,
                }}
              >
                {active && (
                  <Box
                    sx={{
                      position: 'absolute',
                      right: -((RAIL_W - 48) / 2) + 2,
                      width: 3,
                      height: 22,
                      borderRadius: 8,
                      bgcolor: 'primary.main',
                    }}
                  />
                )}
                <Icon fontSize="medium" />
              </IconButton>
            </Tooltip>
          );
        })}
      </Stack>

      {/* Spacer to push bottom items down */}
      <Box sx={{ flexGrow: 1 }} />

      {/* Bottom navigation items */}
      {NAV_BOTTOM.length > 0 && (
        <Stack spacing={{ xs: 1.2, sm: 1.6 }}>
          {NAV_BOTTOM.map((item) => {
            const Icon = item.icon as any;
            const active = item.href === activeHref;

            return (
              <Tooltip key={item.href} title={item.label} placement="right">
                <IconButton
                  LinkComponent={Link}
                  href={item.href}
                  aria-label={item.label}
                  size="large"
                  sx={{
                    position: 'relative',
                    color: active ? 'primary.main' : sidebarIcon,
                    opacity: active ? 1 : 0.85,
                    '&:hover': {
                      bgcolor: active
                        ? alpha(theme.palette.primary.main, 0.16)
                        : alpha(theme.palette.text.primary, 0.08),
                      opacity: 1,
                    },
                    bgcolor: active ? alpha(theme.palette.primary.main, 0.12) : 'transparent',
                    width: 48,
                    height: 48,
                    borderRadius: borderRadius,
                  }}
                >
                  {active && (
                    <Box
                      sx={{
                        position: 'absolute',
                        right: -((RAIL_W - 48) / 2) + 2,
                        width: 3,
                        height: 22,
                        borderRadius: 8,
                        bgcolor: 'primary.main',
                      }}
                    />
                  )}
                  <Icon fontSize="medium" />
                </IconButton>
              </Tooltip>
            );
          })}
        </Stack>
      )}
    </Box>
  );
}
