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
import { LogoMark } from '@/shared/ui/brand/LogoMark';

import HomeRoundedIcon from '@mui/icons-material/HomeRounded';
import DesignServicesRoundedIcon from '@mui/icons-material/DesignServicesRounded';
import CodeRoundedIcon from '@mui/icons-material/CodeRounded';
import MenuBookRoundedIcon from '@mui/icons-material/MenuBookRounded';
import SettingsRoundedIcon from '@mui/icons-material/SettingsRounded';

type NavItem = { href: string; label: string; icon: React.ElementType };

const NAV_TOP: NavItem[] = [
  { href: '/', label: 'Home', icon: HomeRoundedIcon },
  { href: '/design-system', label: 'Design System', icon: DesignServicesRoundedIcon },
  { href: '/examples', label: 'Examples', icon: CodeRoundedIcon },
  { href: '/documentation', label: 'Documentation', icon: MenuBookRoundedIcon },
];

export const RAIL_W = 84;

export function SidebarRail() {
  const theme = useTheme();
  const pathname = usePathname();

  return (
    <Box
      component="aside"
      sx={{
        position: 'fixed',
        left: 0,
        top: 0,
        height: '100vh',
        width: RAIL_W,
        bgcolor: theme.palette.primary.main,
        color: theme.palette.primary.contrastText,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        py: 2,
        gap: 1,
        boxShadow: '2px 0 12px rgba(0,0,0,0.20), inset -1px 0 0 rgba(255,255,255,0.10)',
        zIndex: (t) => t.zIndex.drawer + 1,
      }}
    >
      {/* Logo */}
      <Box
        sx={{
          width: 48,
          height: 48,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          bgcolor: alpha(theme.palette.primary.contrastText, 0.12),
          mt: 0.5,
        }}
      >
        <LogoMark variant="white" width={32} height={30} />
      </Box>

      <Divider sx={{ my: 2, width: '56%', borderColor: alpha(theme.palette.primary.contrastText, 0.25) }} />

      {/* Navegação principal */}
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
                  color: theme.palette.primary.contrastText,
                  opacity: active ? 1 : 0.85,
                  '&:hover': { bgcolor: alpha(theme.palette.common.black, 0.18), opacity: 1 },
                  bgcolor: active ? alpha(theme.palette.common.black, 0.15) : 'transparent',
                  width: 48,
                  height: 48,
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
                      bgcolor: theme.palette.primary.contrastText,
                    }}
                  />
                )}
                <Icon fontSize="medium" />
              </IconButton>
            </Tooltip>
          );
        })}
      </Stack>

      <Box sx={{ flexGrow: 1 }} />

      {/* Rodapé */}
      <Stack spacing={1.6} sx={{ mb: 1 }}>
        <Tooltip title="Settings" placement="right">
          <IconButton
            LinkComponent={Link}
            href="/settings"
            aria-label="Settings"
            size="large"
            sx={{
              color: theme.palette.primary.contrastText,
              '&:hover': { bgcolor: alpha(theme.palette.common.black, 0.18) },
              width: 48,
              height: 48,
            }}
          >
            <SettingsRoundedIcon fontSize="medium" />
          </IconButton>
        </Tooltip>
      </Stack>
    </Box>
  );
}
