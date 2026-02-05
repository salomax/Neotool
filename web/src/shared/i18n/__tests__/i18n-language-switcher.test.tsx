import React, { act } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import "@/shared/i18n/config";
import LanguageSwitcher from "@/shared/i18n/LanguageSwitcher";

describe("LanguageSwitcher", () => {
  it("switches between en and pt using data-testid", async () => {
    const { default: i18n } = await import("@/shared/i18n/config");
    await i18n.changeLanguage("en");
    render(<LanguageSwitcher />);
    const current = screen.getByTestId("lang-current");
    expect(current).toHaveTextContent("en");
    
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
