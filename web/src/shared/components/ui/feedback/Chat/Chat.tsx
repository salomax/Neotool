"use client";

import * as React from "react";
import { Box, TextField, IconButton } from "@mui/material";
import { SxProps, Theme } from "@mui/material/styles";
import SendIcon from "@mui/icons-material/Send";
import { ChatMessage, ChatMessageData } from "./ChatMessage";
import { useMutation } from "@apollo/client/react";
import {
  ChatDocument,
  type ChatMutation,
  type ChatMutationVariables,
} from "@/lib/graphql/operations/assistant/mutations.generated";

export interface ChatProps {
  sessionId?: string;
  onSessionChange?: (sessionId: string) => void;
  placeholder?: string;
  disabled?: boolean;
  onError?: (error: Error) => void;
  className?: string;
  sx?: SxProps<Theme>;
}

/**
 * Generate a unique session ID
 */
const generateSessionId = (): string => {
  return `chat-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
};

/**
 * Generate a unique message ID
 */
const generateMessageId = (): string => {
  return `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
};

/**
 * Chat component - Cursor-like AI chat interface
 * Provides a chat interface for interacting with the catalog assistant
 */
export const Chat: React.FC<ChatProps> = ({
  sessionId: providedSessionId,
  onSessionChange,
  placeholder = "Type your message...",
  disabled = false,
  onError,
  className,
  sx,
}) => {
  // Session management
  const [sessionId, setSessionId] = React.useState<string>(() => {
    return providedSessionId || generateSessionId();
  });

  // Messages state
  const [messages, setMessages] = React.useState<ChatMessageData[]>([]);
  const [isLoading, setIsLoading] = React.useState(false);

  // Input state
  const [inputValue, setInputValue] = React.useState("");
  const messagesContainerRef = React.useRef<HTMLDivElement>(null);

  // GraphQL mutation hook
  const [sendMessage] = useMutation<ChatMutation, ChatMutationVariables>(
    ChatDocument
  );

  // Notify parent of session changes
  React.useEffect(() => {
    if (onSessionChange) {
      onSessionChange(sessionId);
    }
  }, [sessionId, onSessionChange]);

  // Auto-scroll to show beginning of new messages at top
  React.useEffect(() => {
    if (messages.length > 0 && messagesContainerRef.current) {
      const container = messagesContainerRef.current;
      const lastMessage = container.lastElementChild;
      if (lastMessage) {
        // Scroll to show the beginning of the new message at the top
        lastMessage.scrollIntoView({ behavior: "smooth", block: "start" });
      }
    }
  }, [messages]);

  // Handle message submission
  const handleSubmit = async (e?: React.FormEvent | React.KeyboardEvent) => {
    e?.preventDefault();
    if (!inputValue.trim() || isLoading || disabled) return;

    const userMessage = inputValue.trim();
    setInputValue("");
    setIsLoading(true);

    // Add user message immediately
    const userMessageData: ChatMessageData = {
      id: generateMessageId(),
      role: "user",
      content: userMessage,
      timestamp: new Date(),
    };
    setMessages((prev) => [...prev, userMessageData]);

    try {
      const result = await sendMessage({
        variables: {
          input: {
            sessionId,
            message: userMessage,
          },
        },
      });

      // Add assistant response
      if (result.data?.chat?.response) {
        const assistantMessageData: ChatMessageData = {
          id: generateMessageId(),
          role: "assistant",
          content: result.data.chat.response,
          timestamp: new Date(),
        };
        setMessages((prev) => [...prev, assistantMessageData]);
      }
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : "An error occurred";
      onError?.(error as Error);
      setMessages((prev) => [
        ...prev,
        {
          id: generateMessageId(),
          role: "assistant",
          content: "Sorry, I encountered an error. Please try again.",
          timestamp: new Date(),
          error: true,
        },
      ]);
    } finally {
      setIsLoading(false);
    }
  };

  // Handle keyboard shortcuts
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
    // Shift+Enter allows new line (default behavior)
  };

  return (
    <Box
      className={className}
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        ...sx,
      }}
    >
      {/* Messages container */}
      <Box
        ref={messagesContainerRef}
        sx={{
          flex: 1,
          overflowY: "auto",
          p: 2,
          display: "flex",
          flexDirection: "column",
          gap: 0,
        }}
      >
        {messages.length === 0 && (
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
              color: "text.secondary",
            }}
          >
            <Box sx={{ textAlign: "center" }}>
              <Box sx={{ fontSize: 48, mb: 2 }}>💬</Box>
              <Box sx={{ typography: "body1" }}>
                Start a conversation with the AI assistant
              </Box>
            </Box>
          </Box>
        )}
        {messages.map((message) => (
          <ChatMessage key={message.id} message={message} />
        ))}
        {isLoading && (
          <ChatMessage
            message={{
              id: "loading",
              role: "assistant",
              content: "",
              loading: true,
            }}
          />
        )}
      </Box>

      {/* Input area */}
      <Box
        component="form"
        onSubmit={handleSubmit}
        sx={{
          p: 2,
          borderTop: "1px solid",
          borderColor: "divider",
          display: "flex",
          gap: 1,
          alignItems: "flex-end",
        }}
      >
        <TextField
          multiline
          maxRows={4}
          value={inputValue}
          onChange={(e) => setInputValue(e.target.value)}
          onKeyDown={handleKeyDown as any}
          placeholder={placeholder}
          disabled={disabled || isLoading}
          sx={{ flex: 1 }}
          inputProps={{
            "aria-label": "Chat message input",
          }}
        />
        <IconButton
          type="submit"
          disabled={!inputValue.trim() || isLoading || disabled}
          aria-label="Send message"
          color="primary"
        >
          <SendIcon />
        </IconButton>
      </Box>
    </Box>
  );
};
