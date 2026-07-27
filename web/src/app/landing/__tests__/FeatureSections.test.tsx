import React from "react";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { createAppTheme } from "@/styles/themes/theme";
import { FeatureHighlightSection } from "../FeatureHighlightSection";
import { FeatureSplitSection } from "../FeatureSplitSection";
import type { LandingFeatureSection } from "../featureCatalog";

vi.mock("@mui/material/useMediaQuery", () => ({
  default: vi.fn(),
}));

const mockUseMediaQuery = vi.mocked(useMediaQuery);

function renderWithTheme(ui: React.ReactElement) {
  return render(<ThemeProvider theme={createAppTheme("light")}>{ui}</ThemeProvider>);
}

function buildSection(overrides: Partial<LandingFeatureSection> = {}): LandingFeatureSection {
  return {
    id: "test-section",
    layout: "split",
    href: "/target",
    eyebrow: "Eyebrow",
    title: "Section title",
    description: "Section description",
    cta: "Open section",
    accent: "primary",
    icon: "dashboard",
    imageSide: "left",
    highlights: ["Alpha", "Beta"],
    items: [
      { title: "Item one", description: "Item one description" },
      { title: "Item two", description: "Item two description" },
    ],
    ...overrides,
  };
}

describe.sequential("Landing feature sections", () => {
  beforeEach(() => {
    mockUseMediaQuery.mockReturnValue(true);
    global.IntersectionObserver = vi.fn() as unknown as typeof IntersectionObserver;
  });

  it("renders title, description, CTA, and item content for highlight sections", () => {
    const section = buildSection({ layout: "highlight" });

    renderWithTheme(<FeatureHighlightSection section={section} />);

    expect(screen.getByRole("heading", { level: 3, name: section.title })).toBeInTheDocument();
    expect(screen.getByText(section.description)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: section.cta })).toHaveAttribute("href", section.href);
    expect(screen.getByText("Item one")).toBeInTheDocument();
    expect(screen.getByText("Item two description")).toBeInTheDocument();
    expect(screen.getByTestId(`${section.id}-section`)).toHaveAttribute("data-revealed", "true");
  });

  it("renders the split layout with media first when imageSide is left", () => {
    const section = buildSection({ imageSide: "left" });

    renderWithTheme(<FeatureSplitSection section={section} />);

    const layout = screen.getByTestId(`${section.id}-layout`);
    const orderedChildren = within(layout)
      .getAllByTestId(new RegExp(`${section.id}-(media|content)`))
      .map((element) => element.getAttribute("data-testid"));

    expect(orderedChildren).toEqual([`${section.id}-media`, `${section.id}-content`]);
  });

  it("renders the split layout with content first when imageSide is right", () => {
    const section = buildSection({ imageSide: "right" });

    renderWithTheme(<FeatureSplitSection section={section} />);

    const layout = screen.getByTestId(`${section.id}-layout`);
    const orderedChildren = within(layout)
      .getAllByTestId(new RegExp(`${section.id}-(media|content)`))
      .map((element) => element.getAttribute("data-testid"));

    expect(orderedChildren).toEqual([`${section.id}-content`, `${section.id}-media`]);
  });
});
