export type LandingFeatureLayout = "highlight" | "split";
export type LandingFeatureImageSide = "left" | "right";
export type LandingFeatureAccent = "primary" | "success" | "info" | "secondary";
export type LandingFeatureVisualIcon =
  | "dashboard"
  | "automation"
  | "recurring"
  | "wishlist"
  | "institutions"
  | "indicators"
  | "fixedIncome";

export interface LandingFeatureItem {
  title: string;
  description: string;
}

export interface LandingFeatureSection {
  id: string;
  layout: LandingFeatureLayout;
  href: string;
  eyebrow: string;
  title: string;
  description: string;
  cta: string;
  accent: LandingFeatureAccent;
  icon: LandingFeatureVisualIcon;
  imageSide?: LandingFeatureImageSide;
  highlights: string[];
  items?: LandingFeatureItem[];
}

interface LandingFeatureCatalog {
  header: {
    title: string;
    subtitle: string;
  };
  sections: LandingFeatureSection[];
}

type Translate = (key: string) => string;

function item(t: Translate, key: string): LandingFeatureItem {
  return {
    title: t(`${key}.title`),
    description: t(`${key}.description`),
  };
}

export function buildLandingFeatureCatalog(t: Translate): LandingFeatureCatalog {
  return {
    header: {
      title: t("features.title"),
      subtitle: t("features.subtitle"),
    },
    sections: [
      {
        id: "personal-finance",
        layout: "highlight",
        href: "/personal-finance",
        eyebrow: t("features.personalFinance.eyebrow"),
        title: t("features.personalFinance.title"),
        description: t("features.personalFinance.description"),
        cta: t("features.personalFinance.cta"),
        accent: "primary",
        icon: "dashboard",
        highlights: [
          t("features.personalFinance.dashboard.title"),
          t("features.personalFinance.transactions.title"),
          t("features.personalFinance.accounts.title"),
          t("features.personalFinance.summary.title"),
        ],
        items: [
          item(t, "features.personalFinance.dashboard"),
          item(t, "features.personalFinance.transactions"),
          item(t, "features.personalFinance.accounts"),
          item(t, "features.personalFinance.summary"),
          item(t, "features.personalFinance.categorization"),
          item(t, "features.personalFinance.tags"),
        ],
      },
      {
        id: "personal-finance-automation",
        layout: "split",
        href: "/personal-finance/finance-transactions",
        eyebrow: t("features.personalFinance.automation.eyebrow"),
        title: t("features.personalFinance.automation.title"),
        description: t("features.personalFinance.automation.description"),
        cta: t("features.personalFinance.automation.cta"),
        accent: "success",
        icon: "automation",
        imageSide: "left",
        highlights: [
          t("features.personalFinance.imports.title"),
          t("features.personalFinance.installments.title"),
          t("features.personalFinance.documentsNfce.title"),
        ],
        items: [
          item(t, "features.personalFinance.automation"),
          item(t, "features.personalFinance.imports"),
          item(t, "features.personalFinance.installments"),
          item(t, "features.personalFinance.documentsNfce"),
        ],
      },
      {
        id: "recurring-payments",
        layout: "split",
        href: "/personal-finance/recurring-payments",
        eyebrow: t("features.recurringPayments.eyebrow"),
        title: t("features.recurringPayments.title"),
        description: t("features.recurringPayments.description"),
        cta: t("features.recurringPayments.cta"),
        accent: "info",
        icon: "recurring",
        imageSide: "right",
        highlights: [
          t("features.recurringPayments.highlights.pauseResume"),
          t("features.recurringPayments.highlights.reminders"),
          t("features.recurringPayments.highlights.confirmations"),
        ],
      },
      {
        id: "wishlist",
        layout: "split",
        href: "/personal-finance/purchase-wishlist",
        eyebrow: t("features.wishlist.eyebrow"),
        title: t("features.wishlist.title"),
        description: t("features.wishlist.description"),
        cta: t("features.wishlist.cta"),
        accent: "secondary",
        icon: "wishlist",
        imageSide: "left",
        highlights: [
          t("features.wishlist.highlights.priorities"),
          t("features.wishlist.highlights.reorder"),
          t("features.wishlist.highlights.purchaseFlow"),
        ],
      },
      {
        id: "institutions",
        layout: "highlight",
        href: "/financial-data/bacen-institutions",
        eyebrow: t("features.institutions.tag"),
        title: t("features.institutions.title"),
        description: t("features.institutions.description"),
        cta: t("features.institutions.cta"),
        accent: "primary",
        icon: "institutions",
        highlights: [
          t("features.institutions.tags.balance_results"),
          t("features.institutions.tags.basileia"),
          t("features.institutions.tags.net_profit"),
          t("features.institutions.tags.ratings"),
        ],
      },
      {
        id: "indicators",
        layout: "split",
        href: "/financial-data/national-indicators",
        eyebrow: t("features.indicators.tag"),
        title: t("features.indicators.title"),
        description: t("features.indicators.description"),
        cta: t("features.indicators.cta"),
        accent: "info",
        icon: "indicators",
        imageSide: "right",
        highlights: [
          t("features.indicators.tags.ipca"),
          t("features.indicators.tags.selic"),
          t("features.indicators.tags.pib"),
        ],
      },
      {
        id: "fixed-income-comparison",
        layout: "split",
        href: "/investments/fixed-income-comparison",
        eyebrow: t("features.fixed_income_comparison.tag"),
        title: t("features.fixed_income_comparison.title"),
        description: t("features.fixed_income_comparison.description"),
        cta: t("features.fixed_income_comparison.cta"),
        accent: "success",
        icon: "fixedIncome",
        imageSide: "left",
        highlights: [
          t("features.fixed_income_comparison.tags.cdb"),
          t("features.fixed_income_comparison.tags.lci"),
          t("features.fixed_income_comparison.tags.lca"),
        ],
      },
    ],
  };
}
