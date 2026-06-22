"use client";

import React from "react";
import { Box } from "@mui/material";
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
  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="bottom"
      variant="temporary"
    >
      <Drawer.Body sx={{ maxHeight: "85vh", overflow: "auto", pt: 1 }}>
        <Box
          data-testid="mobile-sidebar-sheet-handle"
          aria-hidden
          sx={{
            width: 40,
            height: 4,
            borderRadius: 999,
            bgcolor: "divider",
            mx: "auto",
            mb: 2,
          }}
        />
        {children}
      </Drawer.Body>
    </Drawer>
  );
}

export default MobileSidebarSheet;
