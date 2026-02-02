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

import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';

type NavItem = { href: string; label: string; icon: React.ElementType };

export const RAIL_W = 84;

export function SidebarRail() {
  const theme = useTheme();
  const pathname = usePathname();
  const { t } = useTranslation('common');
  const { hasAny } = useAuthorization();

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
    {
      href: '/financial-data',
      label: t('routes.financialData'),
      icon: AccountBalanceIcon,
    },
  ];

  // Only show Settings if user has authorization permissions
  const NAV_BOTTOM: NavItem[] = hasAuthorizationAccess
    ? [{ href: '/settings', label: t('routes.settings'), icon: SettingsRoundedIcon }]
    : [];

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
        height: '100vh',
        width: RAIL_W,
        bgcolor: sidebarBg,
        color: sidebarIcon,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        py: 2,
        gap: 1,
        zIndex: (t) => t.zIndex.drawer + 1,
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
            bgcolor: theme.palette.mode === 'dark' ? 'transparent' : 'primary.main',
            mt: 0.5,
            cursor: 'pointer',
            transition: 'background-color 0.2s',
            '&:hover': {
              bgcolor: theme.palette.mode === 'dark' ? 'transparent' : 'primary.dark',
            },
          }}
          aria-label="Go to home page"
        >
          <LogoMark 
            variant="white"
            width={32} 
            height={30} 
          />
        </Box>
      </Tooltip>

      <Divider sx={{ my: 2, width: '56%', borderColor: alpha(sidebarIcon, 0.25) }} />

      {/* Top navigation items */}
      <Stack spacing={{ xs: 1.2, sm: 1.6 }} sx={{ mt: 0.5 }}>
        {NAV_TOP.map((item) => {
          const Icon = item.icon as any;
          const active =
            pathname === item.href ||
            ((pathname ?? '').startsWith(item.href) && item.href !== '/');

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
            const active =
              pathname === item.href ||
              ((pathname ?? '').startsWith(item.href) && item.href !== '/');

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
