import { describe, expect, it } from "vitest";
import {
  presentHoldingDetailHeaderLabels,
  type HoldingDetailHeaderModel,
} from "../holdingDetailHeaderPresenter";

function baseModel(
  overrides: Partial<HoldingDetailHeaderModel> & {
    traits?: Partial<HoldingDetailHeaderModel["traits"]>;
  } = {},
): HoldingDetailHeaderModel {
  const { traits, ...rest } = overrides;
  return {
    variant: "fund",
    assetName: "Fundo Exemplo",
    account: { name: "Corretora de Valores - Nubank" },
    traits: {
      issuerLabel: "Nubank",
      assetTypeLabel: "Fundo de investimento",
      subtitleLabel: "Misto",
      ...traits,
    },
    metrics: {
      totalValueLabel: "Total",
      totalValue: 1000,
      currencyCode: "BRL",
      gainPercent: null,
      locale: "pt-BR",
    },
    ...rest,
  };
}

describe("presentHoldingDetailHeaderLabels", () => {
  it("puts fund category and CNPJ on the description line", () => {
    const labels = presentHoldingDetailHeaderLabels(
      baseModel({ traits: { fundCnpj: "27717359000130" } }),
    );

    expect(labels.metaLabel).toBe("Misto • 27.717.359/0001-30");
    expect(labels.accountLabel).toBe("Corretora de Valores - Nubank • Nubank");
  });

  it("omits blank fund CNPJ from the description line", () => {
    const labels = presentHoldingDetailHeaderLabels(
      baseModel({ traits: { fundCnpj: "   " } }),
    );

    expect(labels.metaLabel).toBe("Misto");
  });

  it("omits redundant fund asset type from the description line", () => {
    const labels = presentHoldingDetailHeaderLabels(
      baseModel({
        traits: {
          assetTypeLabel: "Fundo de investimento",
          subtitleLabel: "Misto",
          fundCnpj: "27717359000130",
        },
      }),
    );

    expect(labels.metaLabel).toBe("Misto • 27.717.359/0001-30");
    expect(labels.metaLabel).not.toContain("Fundo de investimento");
  });

  it("keeps issuer on the description line for equity holdings", () => {
    const labels = presentHoldingDetailHeaderLabels(
      baseModel({
        variant: "equity",
        account: { name: "Conta XP" },
        traits: {
          issuerLabel: "Petrobras",
          assetTypeLabel: "Ação",
          subtitleLabel: "PETR4",
          fundCnpj: "11222333000181",
        },
      }),
    );

    expect(labels.metaLabel).toBe("Petrobras • Ação • PETR4");
    expect(labels.accountLabel).toBe("Conta XP");
  });
});
