import { expect, test, type Page } from "@playwright/test";

const BASE_LABEL = "CDB, LC ou Debênture";
const COMMITTED_LABEL = "CDB, LC ou Debênture Compromissada (Sem IOF)";
const EXEMPT_LABEL = "LCI/LCA, CRI/CRA ou Debênture incentivada";

type ComparisonInput = {
  productGroup: string;
  principal: number;
  startDate: string;
  endDate: string;
  rateModel: string;
  contractedRateBps: number;
  cdiAnnualRateBps: number;
  ipcaAnnualRateBps: number;
};

type ComparisonResponse = {
  summary: Record<string, unknown>;
  rows: Array<Record<string, unknown>>;
  performanceSeries: Array<Record<string, unknown>>;
  methodology: Record<string, unknown>;
};

function buildComparisonResponse({
  baseProductGroup = "TAXABLE_IR",
  dayCount = { calendarDays: 10, businessDays: 8 },
  includeCommitted = true,
}: {
  baseProductGroup?: "TAXABLE_IR" | "TAXABLE_IR_NO_IOF" | "EXEMPT_IR";
  dayCount?: { calendarDays: number; businessDays: number };
  includeCommitted?: boolean;
} = {}): ComparisonResponse {
  const baseRow = {
    label: "base",
    productGroup: baseProductGroup,
    rateModel: "CDI",
    equivalentRateBps: 10000,
    contractedRateDisplay: "100% CDI",
    gross: 10100,
    ir: baseProductGroup === "EXEMPT_IR" ? 0 : 10,
    net: baseProductGroup === "EXEMPT_IR" ? 10100 : 10090,
    realGainPercent: baseProductGroup === "EXEMPT_IR" ? 1.0 : 0.9,
    isExempt: baseProductGroup === "EXEMPT_IR",
    isBase: true,
  };

  const comparisonRows = [
    ...(baseProductGroup === "TAXABLE_IR"
      ? []
      : [
          {
            label: "taxable",
            productGroup: "TAXABLE_IR",
            rateModel: "CDI",
            equivalentRateBps: 10000,
            contractedRateDisplay: "100% CDI",
            gross: 10100,
            ir: 10,
            net: 10090,
            realGainPercent: 0.9,
            isExempt: false,
            isBase: false,
          },
        ]),
    ...(includeCommitted
      ? [
          {
            label: "committed",
            productGroup: "TAXABLE_IR_NO_IOF",
            rateModel: "CDI",
            equivalentRateBps: 9800,
            contractedRateDisplay: "98% CDI",
            gross: 10100,
            ir: 8,
            net: 10092,
            realGainPercent: 0.92,
            isExempt: false,
            isBase: false,
          },
        ]
      : []),
    ...(baseProductGroup === "EXEMPT_IR"
      ? []
      : [
          {
            label: "exempt",
            productGroup: "EXEMPT_IR",
            rateModel: "CDI",
            equivalentRateBps: 9700,
            contractedRateDisplay: "97% CDI",
            gross: 10100,
            ir: 0,
            net: 10100,
            realGainPercent: 1.0,
            isExempt: true,
            isBase: false,
          },
        ]),
  ];

  const performanceSeriesGroups = [
    baseProductGroup,
    ...comparisonRows.map((row) => row.productGroup as string),
  ].filter((group, index, all) => all.indexOf(group) === index);

  return {
    summary: {
      baseProduct: baseProductGroup === "EXEMPT_IR" ? "LCI" : "CDB",
      baseProductGroup,
      baseRateModel: "CDI",
      baseEquivalentRateBps: 10000,
      baseRateDisplay: "100% CDI",
      principal: 10000,
      gross: baseRow.gross,
      ir: baseRow.ir,
      net: baseRow.net,
      realGainPercent: baseRow.realGainPercent,
    },
    rows: [baseRow, ...comparisonRows],
    performanceSeries: performanceSeriesGroups.map((productGroup, index) => ({
      productGroup,
      points: [{ date: "2024-01-01", netValue: 10000 + index }],
    })),
    methodology: {
      dayCount: {
        accrualConvention: "BUSINESS_252",
        calendarDays: dayCount.calendarDays,
        businessDays: dayCount.businessDays,
      },
      tax: {
        appliesOnProfitOnly: true,
        activeBracket: {
          fromCalendarDays: 1,
          toCalendarDays: 180,
          rateDecimal: 0.225,
        },
        bracketTable: [],
      },
    },
  };
}

async function mountPageWithMocks(
  page: Page,
  {
    response = buildComparisonResponse(),
    captureInput,
  }: {
    response?: ComparisonResponse;
    captureInput?: (input: ComparisonInput) => void;
  } = {}
) {
  await page.route("**/graphql", async (route) => {
    const request = route.request();
    const payload = request.postDataJSON() as {
      query?: string;
      variables?: { input?: ComparisonInput };
    };
    const query = payload?.query ?? "";

    if (query.includes("economicIndicators")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            economicIndicators: [],
          },
        }),
      });
      return;
    }

    if (query.includes("fixedIncomeComparison")) {
      captureInput?.(payload.variables?.input as ComparisonInput);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: {
            fixedIncomeComparison: response,
          },
        }),
      });
      return;
    }

    await route.continue();
  });

  await page.goto("/investments/fixed-income-comparison");
}

async function submitSimulation(page: Page) {
  await page.getByRole("button", { name: "Simular" }).click();
}

test("shows committed lane in table and chart when IOF scenario returns it", async ({ page }) => {
  await mountPageWithMocks(page, {
    response: buildComparisonResponse({ includeCommitted: true }),
  });

  await expect(page.getByText("Com IOF")).toBeVisible();

  await submitSimulation(page);

  await expect(page.getByRole("cell", { name: COMMITTED_LABEL })).toBeVisible();
  await expect(
    page.getByText(`Esta é a taxa que ${COMMITTED_LABEL}`, { exact: false })
  ).toBeVisible();
});

test("hides committed lane from table and chart when long-period scenario omits it", async ({ page }) => {
  await mountPageWithMocks(page, {
    response: buildComparisonResponse({
      includeCommitted: false,
      dayCount: { calendarDays: 360, businessDays: 252 },
    }),
  });

  await submitSimulation(page);

  await expect(page.getByRole("cell", { name: COMMITTED_LABEL })).toHaveCount(0);
  await expect(page.getByText(COMMITTED_LABEL, { exact: true })).toHaveCount(0);
  await expect(page.getByRole("cell", { name: BASE_LABEL })).toBeVisible();
  await expect(page.getByRole("cell", { name: EXEMPT_LABEL })).toBeVisible();
});

test("submits TAXABLE_IR_NO_IOF when IOF checkbox is unchecked", async ({ page }) => {
  let capturedInput: ComparisonInput | undefined;

  await mountPageWithMocks(page, {
    response: buildComparisonResponse({
      baseProductGroup: "TAXABLE_IR_NO_IOF",
      includeCommitted: false,
    }),
    captureInput: (input) => {
      capturedInput = input;
    },
  });

  await page.getByRole("checkbox", { name: /Com IOF/i }).uncheck();
  await submitSimulation(page);

  expect(capturedInput).toMatchObject({
    productGroup: "TAXABLE_IR_NO_IOF",
    rateModel: "CDI",
    contractedRateBps: 10000,
  });
});

test("submits formatted dates from the standard datepicker fields", async ({ page }) => {
  let capturedInput: ComparisonInput | undefined;

  await mountPageWithMocks(page, {
    response: buildComparisonResponse(),
    captureInput: (input) => {
      capturedInput = input;
    },
  });

  await page.getByRole("textbox", { name: "Data Início" }).fill("01/07/2026");
  await page.getByRole("textbox", { name: "Data Início" }).blur();
  await page.getByRole("textbox", { name: "Vencimento" }).fill("15/07/2026");
  await page.getByRole("textbox", { name: "Vencimento" }).blur();
  await submitSimulation(page);

  expect(capturedInput).toMatchObject({
    startDate: "2026-07-01",
    endDate: "2026-07-15",
  });
});

test("keeps taxable IOF toggle out of the way for exempt product flow", async ({ page }) => {
  let capturedInput: ComparisonInput | undefined;

  await mountPageWithMocks(page, {
    response: buildComparisonResponse({
      baseProductGroup: "EXEMPT_IR",
      includeCommitted: false,
    }),
    captureInput: (input) => {
      capturedInput = input;
    },
  });

  await page.getByRole("button", { name: EXEMPT_LABEL }).click();
  await expect(page.getByText("Com IOF")).toHaveCount(0);

  await submitSimulation(page);

  expect(capturedInput).toMatchObject({
    productGroup: "EXEMPT_IR",
  });
});
