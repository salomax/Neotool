"use client";

import React from "react";
import {
  ErrorBoundary as ReactErrorBoundary,
  type FallbackProps,
} from "react-error-boundary";

function DefaultFallback({ error, resetErrorBoundary }: FallbackProps) {
  const message =
    error instanceof Error ? error.message : String(error ?? "Unknown error");
  return (
    <div role="alert" style={{ padding: 16 }}>
      <h2>Algo deu errado</h2>
      <pre style={{ whiteSpace: "pre-wrap" }}>{message}</pre>
      <button onClick={() => resetErrorBoundary()}>Tentar novamente</button>
    </div>
  );
}

export function ErrorBoundary({ children }: { children: React.ReactNode }) {
  return (
    <ReactErrorBoundary
      FallbackComponent={DefaultFallback}
      onReset={() => {
        if (typeof window !== "undefined") window.location.reload();
      }}
    >
      {children}
    </ReactErrorBoundary>
  );
}
