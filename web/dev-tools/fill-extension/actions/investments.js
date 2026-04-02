// ── Investment actions ────────────────────────────────────────────────────────

async function openFixedIncomeDrawer() {
  const { waitFor, sleep, getInput } = window.__devFill;

  if (!getInput('record-asset-name')) {
    const menuBtn = await waitFor(() => document.querySelector('[data-testid="record-holding-top-btn"]'), 5000, 'record-holding-top-btn');
    menuBtn.click();
    await sleep(300);
    const option = await waitFor(() => document.querySelector('[data-testid="record-holding-option-fixed-income"]'), 5000, 'fixed-income option');
    option.click();
    await sleep(500);

    if (!getInput('record-asset-name')) {
      const accountBtn = await waitFor(() => document.querySelector('[data-testid^="record-select-account-"]'), 5000, 'account selector');
      accountBtn.click();
      await sleep(400);
    }
  }
}

async function fillFixedIncome({ assetName, assetType, issuerName, quantity = 1, grossValue, indexer, indexerPercentage = 105, preFixedRate = 12, tradeDaysOffset = -5, maturityDaysOffset = 180 }) {
  const { fillField, fillDateField, selectMuiField, daysOffset } = window.__devFill;

  await openFixedIncomeDrawer();
  await fillField('record-asset-name', assetName);
  await selectMuiField('record-asset-type', assetType);
  if (assetType !== 'TREASURY') await fillField('record-issuer-name', issuerName);
  await fillField('record-quantity', String(quantity));
  await fillField('record-gross-value', String(grossValue));
  await selectMuiField('record-indexer', indexer);
  if (indexer === 'PRE_FIXADO') {
    await fillField('record-pre-fixed-rate', String(preFixedRate));
  } else {
    await fillField('record-indexer-percentage', String(indexerPercentage));
  }
  await fillDateField('record-trade-date', daysOffset(tradeDaysOffset));
  await fillDateField('record-maturity-date', daysOffset(maturityDaysOffset));
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
      ],
    },
  ],
});
