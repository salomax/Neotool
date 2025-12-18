"use client";

import * as React from "react";
import { Snackbar, Alert, AlertColor } from "@mui/material";

type ToastPosition = {
  vertical: "top" | "bottom";
  horizontal: "left" | "center" | "right";
};

type Toast = {
  id: number;
  message: string;
  severity: AlertColor;
  autoHideDuration?: number;
};

type ToastContextType = {
  show: (
    message: string,
    severity?: AlertColor,
    autoHideDuration?: number,
  ) => void;
  success: (message: string, autoHideDuration?: number) => void;
  error: (message: string, autoHideDuration?: number) => void;
  info: (message: string, autoHideDuration?: number) => void;
  warning: (message: string, autoHideDuration?: number) => void;
};

type ToastProviderProps = {
  children: React.ReactNode;
  position?: ToastPosition;
};

const ToastContext = React.createContext<ToastContextType | null>(null);

export const useToast = () => {
  const ctx = React.useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within <ToastProvider />");
  return ctx;
};

export const ToastProvider: React.FC<ToastProviderProps> = ({
  children,
  position = { vertical: "top", horizontal: "right" },
}) => {
  const [queue, setQueue] = React.useState<Toast[]>([]);
  const [current, setCurrent] = React.useState<Toast | null>(null);

  const process = React.useCallback(() => {
    if (!current && queue.length > 0) {
      const nextToast = queue[0];
      if (nextToast) {
        setCurrent(nextToast);
        setQueue((q) => q.slice(1));
      }
    }
  }, [current, queue]);

  React.useEffect(() => {
    process();
  }, [process, queue, current]);

  const enqueue = (t: Omit<Toast, "id">) => {
    setQueue((q) => [...q, { ...t, id: Date.now() + Math.random() }]);
  };

  const ctx: ToastContextType = {
    show: (m, s = "info", d = 3000) =>
      enqueue({ message: m, severity: s, autoHideDuration: d }),
    success: (m, d = 3000) =>
      enqueue({ message: m, severity: "success", autoHideDuration: d }),
    error: (m, d = 4000) =>
      enqueue({ message: m, severity: "error", autoHideDuration: d }),
    info: (m, d = 3000) =>
      enqueue({ message: m, severity: "info", autoHideDuration: d }),
    warning: (m, d = 3500) =>
      enqueue({ message: m, severity: "warning", autoHideDuration: d }),
  };

  return (
    <ToastContext.Provider value={ctx}>
      {children}
      <Snackbar
        key={current?.id}
        open={!!current}
        autoHideDuration={current?.autoHideDuration ?? 3000}
        onClose={() => setCurrent(null)}
        anchorOrigin={position}
      >
        <Alert
          onClose={() => setCurrent(null)}
          severity={current?.severity ?? "info"}
          elevation={6}
          variant="filled"
        >
          {current?.message}
        </Alert>
      </Snackbar>
    </ToastContext.Provider>
  );
};