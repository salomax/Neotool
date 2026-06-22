"use client";

import React from "react";
import { Box, IconButton } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import { useTranslation } from "react-i18next";
import { Drawer } from "./Drawer";

export interface MobileSidebarSheetProps {
  open: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

export function MobileSidebarSheet({
  open,
  onClose,
  children,
}: MobileSidebarSheetProps) {
  const { t } = useTranslation("common");

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="bottom"
      variant="temporary"
    >
      <Drawer.Body sx={{ maxHeight: "85vh", overflow: "auto", p: 0 }}>
        <Box
          sx={{
            position: "sticky",
            top: 0,
            zIndex: 1,
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            px: 2,
            pt: 1,
            pb: 0.5,
            bgcolor: "background.paper",
            borderBottom: "1px solid",
            borderColor: "divider",
          }}
        >
          <Box sx={{ width: 44, height: 44 }} />
          <Box
            data-testid="mobile-sidebar-sheet-handle"
            aria-hidden
            sx={{
              width: 40,
              height: 4,
              borderRadius: 999,
              bgcolor: "text.disabled",
              mx: "auto",
            }}
          />
          <IconButton
            aria-label={t("actions.close")}
            onClick={onClose}
            sx={{ width: 44, height: 44, color: "text.secondary" }}
          >
            <CloseIcon />
          </IconButton>
        </Box>
        {children}
      </Drawer.Body>
    </Drawer>
  );
}

export default MobileSidebarSheet;
