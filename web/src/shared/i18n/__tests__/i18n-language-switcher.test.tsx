import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import "@/shared/i18n/config";
import LanguageSwitcher from "@/shared/i18n/LanguageSwitcher";

describe("LanguageSwitcher", () => {
  it("switches between en and pt using data-testid", async () => {
    render(<LanguageSwitcher />);
    const current = screen.getByTestId("lang-current");
    expect(current).toHaveTextContent("en");
    await userEvent.click(screen.getByTestId("lang-pt"));
    expect(current).toHaveTextContent("pt");
    await userEvent.click(screen.getByTestId("lang-en"));
    expect(current).toHaveTextContent("en");
  });
});
