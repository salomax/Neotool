import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import RouteErrorBoundaryView from "@/__tests__/RouteErrorBoundaryView";

describe("RouteErrorBoundaryView", () => {
  it("renders error info and calls reset on click", () => {
    const reset = vi.fn();
    render(<RouteErrorBoundaryView error={new Error("boom")} reset={reset} />);
    expect(screen.getByTestId("e2e-error-title")).toBeInTheDocument();
    expect(screen.getByTestId("e2e-error-message")).toHaveTextContent("boom");
    fireEvent.click(screen.getByTestId("e2e-error-reset"));
    expect(reset).toHaveBeenCalledTimes(1);
  });
});
