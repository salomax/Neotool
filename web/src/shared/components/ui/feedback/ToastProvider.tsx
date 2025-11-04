"use client";

import * as React from "react";
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import type { AlertColor } from '@mui/material/Alert';
import Slide from '@mui/material/Slide';
import IconButton from '@mui/material/IconButton';
import CloseIcon from "@mui/icons-material/Close";

type ToastInput =
  | string
  | {
      message: string;
      severity?: AlertColor; // 'success' | 'info' | 'warning' | 'error'
      autoHideDuration?: number;
    };

type Toast = {
  id: number;
  message: string;
  severity: AlertColor;
  autoHideDuration: number;
};

type ToastContextValue = {
  show: (_t: ToastInput) => void;
  success: (_msg: string, _ms?: number) => void;
  info: (_msg: string, _ms?: number) => void;
  warning: (_msg: string, _ms?: number) => void;
  error: (_msg: string, _ms?: number) => void;
};

const ToastCtx = React.createContext<ToastContextValue | null>(null);

export function useToast() {
  const ctx = React.useContext(ToastCtx);
  if (!ctx) throw new Error("useToast must be used within <ToastProvider />");
  return ctx;
}

function toToast(input: ToastInput, id: number): Toast {
  if (typeof input === "string")
    return { id, message: input, severity: "info", autoHideDuration: 3000 };
  return {
    id,
    message: input.message,
    severity: input.severity ?? "info",
    autoHideDuration: input.autoHideDuration ?? 3000,
  };
}

export const ToastProvider: React.FC<React.PropsWithChildren<{}>> = ({
  children,
}) => {
  const [toasts, setToasts] = React.useState<Toast[]>([]);

  const show = React.useCallback((input: ToastInput) => {
    setToasts((list) => [...list, toToast(input, Date.now() + Math.random())]);
  }, []);

  const api: ToastContextValue = React.useMemo(
    () => ({
      show,
      success: (msg, ms) =>
        show({ message: msg, severity: "success", ...(ms !== undefined && { autoHideDuration: ms }) }),
      info: (msg, ms) =>
        show({ message: msg, severity: "info", ...(ms !== undefined && { autoHideDuration: ms }) }),
      warning: (msg, ms) =>
        show({ message: msg, severity: "warning", ...(ms !== undefined && { autoHideDuration: ms }) }),
      error: (msg, ms) =>
        show({ message: msg, severity: "error", ...(ms !== undefined && { autoHideDuration: ms }) }),
    }),
    [show],
  );

  const handleClose = (id: number) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  };

  return (
    <ToastCtx.Provider value={api}>
      {children}
      {toasts.map((t) => (
        <Snackbar
          key={t.id}
          open
          anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
          autoHideDuration={t.autoHideDuration}
          onClose={() => handleClose(t.id)}
          TransitionComponent={(props: any) => <Slide {...props} direction="left" />}
        >
          <Alert
            severity={t.severity}
            onClose={() => handleClose(t.id)}
            variant="filled"
            iconMapping={{}}
            action={
              <IconButton
                size="small"
                onClick={() => handleClose(t.id)}
                color="inherit"
              >
                <CloseIcon fontSize="small" />
              </IconButton>
            }
          >
            {t.message}
          </Alert>
        </Snackbar>
      ))}
    </ToastCtx.Provider>
  );
};
