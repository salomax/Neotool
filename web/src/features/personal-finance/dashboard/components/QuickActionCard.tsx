import React from 'react';
import {
  alpha,
  Box,
  ButtonBase,
  Typography,
  useTheme,
} from '@mui/material';
import Link from 'next/link';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';

interface QuickActionCardProps {
  href: string;
  title: string;
  description?: string;
  accentColor: string;
  icon: React.ReactNode;
  isMobile?: boolean;
}

export const QuickActionCard: React.FC<QuickActionCardProps> = ({
  href,
  title,
  description,
  accentColor,
  icon,
  isMobile,
}) => {
  const theme = useTheme();

  return (
    <ButtonBase
      component={Link}
      href={href}
      sx={{
        flex: 1,
        minWidth: isMobile ? '100%' : 300,
        borderRadius: `${theme.custom.radius.button}px`,
        textAlign: 'left',
        '&:focus-visible .quick-action-card': {
          outline: `2px solid ${alpha(accentColor, 0.5)}`,
          outlineOffset: 2,
        },
      }}
    >
      <Box
        className="quick-action-card"
        sx={{
          width: '100%',
          p: 2,
          borderRadius: `${theme.custom.radius.button}px`,
          border: `1px solid ${alpha(accentColor, .3)}`,
          bgcolor: 'background.paper',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 2,
          position: 'relative',
          overflow: 'hidden',
          boxShadow: 'none',
          transition: 'transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out, border-color 0.2s ease-in-out',
          '&::after': {
            content: '""',
            position: 'absolute',
            inset: 0,
            background: `radial-gradient(850px circle at 80% 100%, ${alpha(accentColor, 0.12)}, transparent 55%)`,
            opacity: 1,
            transition: 'opacity 0.7s',
            pointerEvents: 'none',
            zIndex: 0,
          },
          '& .quick-action-content': {
            position: 'relative',
            zIndex: 1,
            minWidth: 0,
          },
          '& .quick-action-arrow': {
            position: 'relative',
            zIndex: 1,
            transition: 'transform 0.2s ease-in-out, color 0.2s ease-in-out',
          },
          '& .quick-action-bg-icon': {
            position: 'absolute',
            right: 12,
            bottom: -44,
            opacity: 0.3,
            transition: 'transform 0.7s, opacity 0.7s',
            color: alpha(accentColor, 0.22),
            transform: 'translate(0, 0) scale(.8)',
            zIndex: 0,
            pointerEvents: 'none',
          },
          '&:hover': {
            transform: 'translateY(-2px)',
            boxShadow: theme.custom.shadow(4, accentColor),
            borderColor: accentColor,
            '& .quick-action-arrow': {
              transform: 'translateX(4px)',
              color: accentColor,
            },
            '& .quick-action-bg-icon': {
              transform: 'translate(8px, 10px) scale(1.4)',
              opacity: 0.12,
            },
          },
          '&:active': {
            transform: 'translateY(-1px)',
          },
        }}
      >
        <Box className="quick-action-content" sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" fontWeight={700} noWrap>
              {title}
            </Typography>
            {description ? (
              <Typography variant="body2" color="text.secondary">
                {description}
              </Typography>
            ) : null}
          </Box>
        </Box>
        <Box className="quick-action-arrow" sx={{ color: alpha(theme.palette.text.primary, 0.35), flex: '0 0 auto' }}>
          <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
        </Box>
        <Box className="quick-action-bg-icon">
          {icon}
        </Box>
      </Box>
    </ButtonBase>
  );
};
