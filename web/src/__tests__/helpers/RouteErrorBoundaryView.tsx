"use client";
import React from "react";
export default function RouteErrorBoundaryView({
  error,
  reset,
}: {
  error: Error;
  reset: () => void;
}) {
  return (
    <div style={{ padding: 24 }}>
      <h3 data-testid="e2e-error-title">Something went wrong</h3>
      <pre data-testid="e2e-error-message">{error.message}</pre>
      <button onClick={reset} data-testid="e2e-error-reset">
        Try again
      </button>
    </div>
  );
}
