// ── Investment actions ────────────────────────────────────────────────────────

async function openFixedIncomeDrawer() {
  const { waitFor, sleep, getInput } = window.__devFill;

  if (!getInput('record-asset-name')) {
    const legacyMenuBtn = document.querySelector('[data-testid="record-holding-top-btn"]');
    if (legacyMenuBtn) {
      legacyMenuBtn.click();
      await sleep(300);
      const option = await waitFor(() => document.querySelector('[data-testid="record-holding-option-fixed-income"]'), 5000, 'fixed-income option');
      option.click();
      await sleep(500);
    } else {
      await openGridRecordDrawerForClass('fixed-income', 1);
    }

    if (!getInput('record-asset-name')) {
      const accountBtn = await waitFor(() => document.querySelector('[data-testid^="record-select-account-"]'), 5000, 'account selector');
      accountBtn.click();
      await sleep(400);
    }
  }
}

async function fillFixedIncome({ assetName, assetType, issuerName, quantity = 1, grossValue, indexer, indexerPercentage = 105, preFixedRate = 12, tradeDaysOffset = -5, maturityDaysOffset = 180 }) {
  const { fillField, fillDateField, selectMuiField, daysOffset } = window.__devFill;
  const toPtDecimal = (value) => String(value).replace('.', ',');

  await openFixedIncomeDrawer();
  await fillField('record-asset-name', assetName);
  await selectMuiField('record-asset-type', assetType);
  if (assetType !== 'TREASURY') await fillField('record-issuer-name', issuerName);
  await fillField('record-quantity', String(quantity));
  await fillField('record-gross-value', String(grossValue));
  await selectMuiField('record-indexer', indexer);
  if (indexer === 'PRE_FIXADO') {
    await fillField('record-pre-fixed-rate', toPtDecimal(preFixedRate));
  } else {
    await fillField('record-indexer-percentage', String(indexerPercentage));
  }
  await fillDateField('record-trade-date', daysOffset(tradeDaysOffset));
  await fillDateField('record-maturity-date', daysOffset(maturityDaysOffset));
}

async function openFundDrawer() {
  const { waitFor, sleep, getInput } = window.__devFill;

  if (!getInput('record-fund-manual-name') && !document.querySelector('[data-testid="record-fund-autocomplete"]')) {
    const legacyMenuBtn = document.querySelector('[data-testid="record-holding-top-btn"]');
    if (legacyMenuBtn) {
      legacyMenuBtn.click();
      await sleep(300);
      const option = await waitFor(() => document.querySelector('[data-testid="record-holding-option-fund"]'), 5000, 'fund option');
      option.click();
      await sleep(500);
    } else {
      await openGridRecordDrawerForClass('funds', 2);
    }

    if (!getInput('record-fund-manual-name') && !document.querySelector('[data-testid="record-fund-autocomplete"]')) {
      const accountBtn = await waitFor(() => document.querySelector('[data-testid^="record-fund-select-account-"]'), 5000, 'fund account selector');
      accountBtn.click();
      await sleep(400);
    }
  }
}

async function openGridRecordDrawerForClass(classKey, classCardIndex) {
  const { waitFor, sleep } = window.__devFill;
  const tabId = `investment-grid-class-tab-${classKey}`;

  const classTab = document.getElementById(tabId);
  if (classTab) {
    classTab.click();
    await sleep(250);
  } else {
    const classCards = await waitFor(
      () => Array.from(document.querySelectorAll('[role="button"][aria-label]')).filter((el) => !el.closest('[role="tablist"]')),
      5000,
      'investment class cards',
    );
    const classCard = classCards[classCardIndex];
    if (!classCard) throw new Error(`[devFill] class card not found for ${classKey}`);
    classCard.click();
    await sleep(300);
  }

  const cta = await waitFor(() => document.querySelector('[data-testid="investment-grid-record-cta"]'), 5000, 'investment-grid-record-cta');
  cta.click();
  await sleep(400);
}

async function ensureFundManualMode() {
  const { waitFor, sleep } = window.__devFill;
  if (window.__devFill.getInput('record-fund-manual-name')) return;

  const manualTab = await waitFor(
    () => Array.from(document.querySelectorAll('[role="tab"]')).find((tab) => /Nao listado|Não listado/i.test((tab.textContent || '').trim())),
    5000,
    'fund manual tab',
  );
  manualTab.click();
  await sleep(200);
}

async function fillFundManual({
  fundName,
  fundCnpj = '',
  quantity = 100,
  unitPrice = 12.5,
  tradeDaysOffset = -3,
  currentGrossValue = 1300,
  valuedAtDaysOffset = 0,
}) {
  const { fillField, fillDateField, daysOffset } = window.__devFill;

  await openFundDrawer();
  await ensureFundManualMode();

  await fillField('record-fund-manual-name', fundName);
  if (fundCnpj) await fillField('record-fund-manual-cnpj', fundCnpj);
  await fillDateField('record-fund-trade-date', daysOffset(tradeDaysOffset));
  await fillField('record-fund-quantity', String(quantity));
  await fillField('record-fund-unit-price', String(unitPrice));
  await fillField('record-fund-manual-current-gross-value', String(currentGrossValue));
  await fillDateField('record-fund-manual-valued-at', daysOffset(valuedAtDaysOffset));
}

window.__devActions = window.__devActions || [];
window.__devActions.push({
  label: 'Auto-fill',
  match: /\/personal-finance\/investments/,
  children: [
    {
      label: 'Investimentos',
      children: [
        {
          label: 'Renda Fixa',
          children: [
            {
              label: 'CDB — CDI 105%',
              run: () => fillFixedIncome({ assetName: `CDB Dev ${Date.now()}`, assetType: 'CDB', issuerName: 'Banco Dev', grossValue: 1000, indexer: 'CDI', indexerPercentage: 105 }),
            },
            {
              label: 'LCI — IPCA 98%',
              run: () => fillFixedIncome({ assetName: `LCI Dev ${Date.now()}`, assetType: 'LCI', issuerName: 'Banco Dev', grossValue: 2000, indexer: 'IPCA', indexerPercentage: 98, maturityDaysOffset: 540 }),
            },
            {
              label: 'LCA — CDI 100%',
              run: () => fillFixedIncome({ assetName: `LCA Dev ${Date.now()}`, assetType: 'LCA', issuerName: 'Banco Dev', grossValue: 1500, indexer: 'CDI', indexerPercentage: 100, maturityDaysOffset: 360 }),
            },
            {
              label: 'Tesouro Prefixado 12,5%',
              run: () => fillFixedIncome({ assetName: `Tesouro Dev ${Date.now()}`, assetType: 'TREASURY', issuerName: 'Tesouro Nacional', grossValue: 1500, indexer: 'PRE_FIXADO', preFixedRate: 12.5, maturityDaysOffset: 365 }),
            },
            {
              label: 'CDB vencido (5 dias atrás)',
              run: () => fillFixedIncome({ assetName: `CDB Vencido Dev ${Date.now()}`, assetType: 'CDB', issuerName: 'Banco Dev', grossValue: 1200, indexer: 'CDI', indexerPercentage: 103, tradeDaysOffset: -120, maturityDaysOffset: -5 }),
            },
          ],
        },
        {
          label: 'Fundos',
          children: [
            {
              label: 'Fundo Multimercado (manual)',
              run: () => fillFundManual({
                fundName: `Fundo Multi Dev ${Date.now()}`,
                fundCnpj: '12.345.678/0001-90',
                quantity: 120.5,
                unitPrice: 10.4,
                currentGrossValue: 1350,
              }),
            },
            {
              label: 'Previdencia PGBL (manual)',
              run: () => fillFundManual({
                fundName: `PGBL Dev ${Date.now()}`,
                fundCnpj: '23.456.789/0001-01',
                quantity: 80,
                unitPrice: 18.75,
                currentGrossValue: 1620,
                tradeDaysOffset: -40,
              }),
            },
            {
              label: 'Previdencia VGBL (manual)',
              run: () => fillFundManual({
                fundName: `VGBL Dev ${Date.now()}`,
                fundCnpj: '34.567.890/0001-12',
                quantity: 65,
                unitPrice: 22.3,
                currentGrossValue: 1510,
                tradeDaysOffset: -75,
              }),
            },
          ],
        },
      ],
    },
  ],
});
