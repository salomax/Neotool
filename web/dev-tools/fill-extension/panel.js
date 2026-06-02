// ── Neotool Dev Helper Panel ──────────────────────────────────────────────────────────

const PANEL_ID = 'devfill-panel';
const BTN_ID = 'devfill-toggle';

const s = {
  btn: `position:fixed;bottom:24px;right:24px;z-index:2147483647;
    width:42px;height:42px;border-radius:50%;border:none;
    background:#6366f1;color:white;font-size:20px;cursor:pointer;
    box-shadow:0 4px 12px rgba(0,0,0,0.35);display:flex;align-items:center;justify-content:center;`,
  panel: `position:fixed;bottom:76px;right:24px;z-index:2147483646;
    width:280px;max-height:420px;border-radius:12px;overflow:hidden;
    background:#1e1e2e;color:#cdd6f4;font-family:system-ui,sans-serif;font-size:13px;
    box-shadow:0 8px 32px rgba(0,0,0,0.5);display:flex;flex-direction:column;`,
  header: `display:flex;align-items:center;gap:8px;padding:10px 14px;
    background:#181825;border-bottom:1px solid #313244;flex-shrink:0;`,
  breadcrumb: `flex:1;font-size:11px;color:#a6adc8;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;`,
  back: `background:none;border:none;color:#89b4fa;cursor:pointer;font-size:18px;padding:0 4px;line-height:1;`,
  list: `overflow-y:auto;flex:1;padding:6px 0;`,
  item: `display:flex;align-items:center;gap:8px;width:100%;padding:9px 14px;
    background:none;border:none;color:#cdd6f4;cursor:pointer;text-align:left;font-size:13px;
    border-radius:0;transition:background 0.1s;`,
  itemHover: `background:#313244;`,
  chevron: `margin-left:auto;color:#585b70;font-size:11px;`,
  status: `padding:8px 14px;font-size:11px;color:#a6adc8;border-top:1px solid #313244;flex-shrink:0;min-height:28px;`,
};

let panelEl = null;
let stack = []; // navigation stack: array of { label, items }
let statusTimeout = null;

function getActionsForCurrentPage() {
  const path = location.pathname;
  return (window.__devActions || []).filter(node => node.match?.test(path));
}

function createPanel() {
  const el = document.createElement('div');
  el.id = PANEL_ID;
  el.style.cssText = s.panel;
  el.innerHTML = `
    <div id="devfill-header" style="${s.header}">
      <button id="devfill-back" style="${s.back}" title="Voltar">‹</button>
      <span id="devfill-breadcrumb" style="${s.breadcrumb}">Neotool Dev Helper</span>
    </div>
    <div id="devfill-list" style="${s.list}"></div>
    <div id="devfill-status" style="${s.status}"></div>
  `;
  document.body.appendChild(el);
  document.getElementById('devfill-back').addEventListener('click', navigateBack);
  return el;
}

function renderList(items) {
  const list = document.getElementById('devfill-list');
  list.innerHTML = '';
  if (!items.length) {
    list.innerHTML = `<div style="padding:16px 14px;color:#585b70;">Nenhuma action para esta página.</div>`;
    return;
  }
  items.forEach(item => {
    const btn = document.createElement('button');
    btn.style.cssText = s.item;
    btn.innerHTML = `<span style="flex:1">${item.label}</span>${item.children ? `<span style="${s.chevron}">›</span>` : ''}`;
    btn.addEventListener('mouseenter', () => btn.style.background = '#313244');
    btn.addEventListener('mouseleave', () => btn.style.background = 'none');
    btn.addEventListener('click', () => {
      if (item.children) {
        stack.push({ label: item.label, items: item.children });
        renderCurrentLevel();
      } else if (item.run) {
        runAction(item);
      }
    });
    list.appendChild(btn);
  });
}

function renderCurrentLevel() {
  const backBtn = document.getElementById('devfill-back');
  const breadcrumb = document.getElementById('devfill-breadcrumb');

  if (stack.length === 0) {
    const rootItems = getActionsForCurrentPage();
    backBtn.style.visibility = 'hidden';
    breadcrumb.textContent = 'Neotool Dev Helper';
    renderList(rootItems);
  } else {
    backBtn.style.visibility = 'visible';
    breadcrumb.textContent = stack.map(s => s.label).join(' › ');
    renderList(stack[stack.length - 1].items);
  }
}

function navigateBack() {
  stack.pop();
  renderCurrentLevel();
}

function setStatus(msg, isError = false) {
  const el = document.getElementById('devfill-status');
  if (!el) return;
  el.textContent = msg;
  el.style.color = isError ? '#f38ba8' : '#a6e3a1';
  clearTimeout(statusTimeout);
  statusTimeout = setTimeout(() => { if (el) el.textContent = ''; el.style.color = '#a6adc8'; }, 4000);
}

async function runAction(item) {
  setStatus(`⏳ ${item.label}...`);
  try {
    await item.run();
    setStatus(`✓ ${item.label}`);
  } catch (e) {
    setStatus(`✗ ${e.message}`, true);
    console.error('[devfill]', e);
  }
}

function togglePanel() {
  if (!panelEl) {
    panelEl = createPanel();
    stack = [];
    renderCurrentLevel();
    panelEl.style.display = 'flex';
  } else if (panelEl.style.display === 'none') {
    stack = [];
    renderCurrentLevel();
    panelEl.style.display = 'flex';
  } else {
    panelEl.style.display = 'none';
  }
}

function initToggleButton() {
  if (document.getElementById(BTN_ID)) return;
  const btn = document.createElement('button');
  btn.id = BTN_ID;
  btn.title = 'Neotool Dev Helper';
  btn.style.cssText = s.btn;
  const logo = document.createElement('img');
  logo.src = `${location.origin}/images/logos/invistus-logo-white-transparent.svg`;
  logo.alt = 'Invistus';
  logo.style.width = '22px';
  logo.style.height = '22px';
  logo.style.objectFit = 'contain';
  btn.appendChild(logo);
  btn.addEventListener('click', togglePanel);
  document.body.appendChild(btn);
}

// ── Init ──────────────────────────────────────────────────────────────────────

initToggleButton();

// Re-render panel on SPA navigation
let lastPath = location.pathname;
new MutationObserver(() => {
  if (location.pathname === lastPath) return;
  lastPath = location.pathname;
  if (panelEl) {
    stack = [];
    renderCurrentLevel();
  }
}).observe(document.body, { childList: true, subtree: true });
