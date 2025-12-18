"use client";

import * as React from "react";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Chat } from "./Chat";

export interface ChatDrawerProps {
  open: boolean;
  onClose: () => void;
  anchor?: "left" | "right" | "top" | "bottom";
  width?: number | string;
}

/**
 * ChatDrawer component - Wraps Chat component in a Drawer
 * Provides a drawer-based chat interface for the AI assistant
 */
export const ChatDrawer: React.FC<ChatDrawerProps> = ({
  open,
  onClose,
  anchor = "right",
  width,
}) => {
  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor={anchor}
      size={width ? undefined : "sm"}
      width={width}
      sx={{
        "& .MuiDrawer-paper": {
          borderRadius: 0,
        },
      }}
    >
      <Drawer.Header title="AI Assistant" showCloseButton={true} />
      <Drawer.Body>
        <Chat />
      </Drawer.Body>
    </Drawer>
  );
};

