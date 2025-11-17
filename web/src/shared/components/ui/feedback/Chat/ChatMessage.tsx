"use client";

import * as React from "react";
import { Box, Paper, Typography, CircularProgress } from "@mui/material";
import { SxProps, Theme } from "@mui/material/styles";

export interface ChatMessageData {
  id: string;
  role: "user" | "assistant";
  content: string;
  timestamp?: Date;
  loading?: boolean;
  error?: boolean;
}

export interface ChatMessageProps {
  message: ChatMessageData;
  sx?: SxProps<Theme>;
}

/**
 * Typing indicator component - animated dots
 */
const TypingIndicator: React.FC = () => {
  return (
    <Box sx={{ display: "flex", gap: 0.5, alignItems: "center", py: 0.5 }}>
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: "50%",
          bgcolor: "text.secondary",
          animation: "pulse 1.4s ease-in-out infinite",
          "@keyframes pulse": {
            "0%, 60%, 100%": {
              opacity: 0.3,
              transform: "scale(1)",
            },
            "30%": {
              opacity: 1,
              transform: "scale(1.2)",
            },
          },
        }}
      />
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: "50%",
          bgcolor: "text.secondary",
          animation: "pulse 1.4s ease-in-out 0.2s infinite",
          "@keyframes pulse": {
            "0%, 60%, 100%": {
              opacity: 0.3,
              transform: "scale(1)",
            },
            "30%": {
              opacity: 1,
              transform: "scale(1.2)",
            },
          },
        }}
      />
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: "50%",
          bgcolor: "text.secondary",
          animation: "pulse 1.4s ease-in-out 0.4s infinite",
          "@keyframes pulse": {
            "0%, 60%, 100%": {
              opacity: 0.3,
              transform: "scale(1)",
            },
            "30%": {
              opacity: 1,
              transform: "scale(1.2)",
            },
          },
        }}
      />
    </Box>
  );
};

/**
 * ChatMessage component - displays individual chat messages
 * User messages are right-aligned with primary color background
 * Assistant messages are left-aligned with paper background
 */
export const ChatMessage: React.FC<ChatMessageProps> = ({ message, sx }) => {
  const isUser = message.role === "user";
  const isError = message.error === true;

  if (message.loading) {
    return (
      <Box
        sx={{
          display: "flex",
          justifyContent: "flex-start",
          mb: 2,
          ...sx,
        }}
      >
        <Paper
          sx={{
            p: 2,
            maxWidth: "80%",
            bgcolor: "background.paper",
            border: "1px solid",
            borderColor: "divider",
          }}
        >
          <TypingIndicator />
        </Paper>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: "flex",
        justifyContent: isUser ? "flex-end" : "flex-start",
        mb: 2,
        ...sx,
      }}
    >
      <Paper
        sx={{
          p: 2,
          maxWidth: "80%",
          bgcolor: isUser
            ? "primary.main"
            : isError
            ? "error.light"
            : "background.paper",
          color: isUser ? "primary.contrastText" : "text.primary",
          border: isError ? "1px solid" : "none",
          borderColor: isError ? "error.main" : "transparent",
          borderRadius: 2,
          boxShadow: 1,
        }}
      >
        <Typography
          variant="body1"
          sx={{
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
            color: isUser ? "primary.contrastText" : "text.primary",
          }}
        >
          {message.content}
        </Typography>
        {message.timestamp && (
          <Typography
            variant="caption"
            sx={{
              display: "block",
              mt: 0.5,
              opacity: 0.7,
              color: isUser ? "primary.contrastText" : "text.secondary",
            }}
          >
            {message.timestamp.toLocaleTimeString([], {
              hour: "2-digit",
              minute: "2-digit",
            })}
          </Typography>
        )}
      </Paper>
    </Box>
  );
};

