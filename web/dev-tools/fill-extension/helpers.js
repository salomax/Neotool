// ── Shared fill helpers ───────────────────────────────────────────────────────

window.__devFill = window.__devFill || {};

window.__devFill.sleep = function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
};

window.__devFill.waitFor = function waitFor(fn, timeout = 5000, label = '') {
  return new Promise((resolve, reject) => {
    const start = Date.now();
    const id = setInterval(() => {
      const result = fn();
      if (result) { clearInterval(id); resolve(result); return; }
      if (Date.now() - start > timeout) {
        clearInterval(id);
        reject(new Error(`[devFill] timeout: ${label || fn.toString().slice(0, 60)}`));
      }
    }, 50);
  });
};

window.__devFill.getInput = function getInput(testId) {
  return document.querySelector(`[data-testid="${testId}"] input, input[data-testid="${testId}"]`);
};

window.__devFill.setNativeValue = function setNativeValue(el, value) {
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(el, value);
  el.dispatchEvent(new Event('input', { bubbles: true }));
  el.dispatchEvent(new Event('change', { bubbles: true }));
};

window.__devFill.fillField = async function fillField(testId, value) {
  const { waitFor, getInput, setNativeValue, sleep } = window.__devFill;
  const input = await waitFor(() => getInput(testId), 5000, testId);
  input.focus();
  setNativeValue(input, value);
  input.blur();
  await sleep(80);
};

window.__devFill.fillDateField = async function fillDateField(testId, isoDate) {
  const { waitFor, sleep } = window.__devFill;
  const [year, month, day] = isoDate.split('-');
  const formatted = `${day}/${month}/${year}`;
  const container = await waitFor(() => document.querySelector(`[data-testid="${testId}"]`), 5000, testId);
  const input = container.querySelector('input');
  input.focus();
  await sleep(80);
  input.dispatchEvent(new KeyboardEvent('keydown', { key: 'a', ctrlKey: true, bubbles: true }));
  await sleep(30);
  for (const ch of formatted) {
    document.execCommand('insertText', false, ch);
    await sleep(40);
  }
  input.blur();
};

window.__devFill.selectMuiField = async function selectMuiField(testId, value) {
  const { waitFor, sleep } = window.__devFill;
  const container = await waitFor(() => document.querySelector(`[data-testid="${testId}"]`), 5000, testId);
  const input = container.querySelector('input[type="hidden"]') || container.querySelector('input');
  const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;
  setter.call(input, value);
  input.dispatchEvent(new Event('change', { bubbles: true }));
  await sleep(150);
};

window.__devFill.daysOffset = function daysOffset(days) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  return d.toISOString().slice(0, 10);
};
