import React, { act } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import LanguageSwitcher from "@/shared/i18n/LanguageSwitcher";

describe("LanguageSwitcher", () => {
  it("switches between en and pt using data-testid", async () => {
    render(<LanguageSwitcher />);
    const current = screen.getByTestId("lang-current");

    await act(async () => {
      await userEvent.click(screen.getByTestId("lang-pt"));
    });
    await waitFor(() => {
      expect(current).toHaveTextContent("pt");
    });

    await act(async () => {
      await userEvent.click(screen.getByTestId("lang-en"));
    });
    await waitFor(() => {
      expect(current).toHaveTextContent("en");
    });
  });
});
