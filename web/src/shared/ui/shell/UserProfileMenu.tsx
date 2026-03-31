"use client";

import * as React from "react";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import LogoutIcon from "@mui/icons-material/Logout";
import PersonIcon from "@mui/icons-material/Person";
import { getInitials } from "@/shared/utils/user";

export type UserProfileMenuUser = {
  displayName?: string | null;
  email?: string | null;
  avatarUrl?: string | null;
};

export function UserProfileMenu({
  user,
  onSignOut,
  onOpenProfile,
  t,
}: {
  user: UserProfileMenuUser | null;
  onSignOut: () => void;
  onOpenProfile: () => void;
  t: (key: string) => string;
}) {
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const open = Boolean(anchorEl);
  const displayName =
    user?.displayName?.trim() || user?.email || t("appHeader.defaultUser");
  const email = user?.email || "";

  const handleMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleMenuClose = () => {
    setAnchorEl(null);
  };

  const handleSignOut = () => {
    handleMenuClose();
    onSignOut();
  };

  const handleOpenProfile = () => {
    handleMenuClose();
    onOpenProfile();
  };

  return (
    <>
      <Tooltip title={t("appHeader.account")}>
        <IconButton
          aria-label="Account menu"
          onClick={handleMenuOpen}
          aria-controls={open ? "profile-menu" : undefined}
          aria-haspopup="true"
          aria-expanded={open ? "true" : undefined}
          sx={(theme) => ({
            p: 0,
            borderRadius: `${theme.custom.radius.sm}px`,
            overflow: "hidden",
          })}
        >
          <Avatar
            variant="rounded"
            sx={(theme) => ({
              width: 32,
              height: 32,
              borderRadius: `${theme.custom.radius.md}px`,
            })}
            src={user?.avatarUrl || undefined}
          >
            {user ? (
              getInitials({ displayName: user.displayName, email: user.email ?? "" })
            ) : (
              <PersonIcon />
            )}
          </Avatar>
        </IconButton>
      </Tooltip>
      <Menu
        id="profile-menu"
        anchorEl={anchorEl}
        open={open}
        onClose={handleMenuClose}
        onClick={handleMenuClose}
        transformOrigin={{ horizontal: "right", vertical: "top" }}
        anchorOrigin={{ horizontal: "right", vertical: "bottom" }}
        PaperProps={{
          sx: {
            minWidth: 280,
            // py: 0.5,
          },
        }}
      >
        <Box sx={{ px: 2, py: 1, borderBottom: 1, borderColor: "divider" }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
            {displayName}
          </Typography>
          {email && displayName !== email && (
            <Typography variant="body2" color="text.secondary">
              {email}
            </Typography>
          )}
        </Box>
        <MenuItem
          divider
          onClick={handleOpenProfile}
          sx={{ py: 2, alignItems: "flex-start" }}
        >
          <PersonIcon fontSize="small" sx={{ mr: 1.5, mt: 0.25 }} />
          <Box sx={{ display: "flex", flexDirection: "column" }}>
            <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>
              {t("routes.profile")}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t("appHeader.profile.subtitle")}
            </Typography>
          </Box>
        </MenuItem>
        <MenuItem onClick={handleSignOut}>
          <LogoutIcon fontSize="small" sx={{ mr: 1.5 }} />
          {t("appHeader.signOut")}
        </MenuItem>
      </Menu>
    </>
  );
}
