const API = 'http://localhost:8080/api';
const $ = (id) => document.getElementById(id);
const state = { page: 0, sortBy: 'id', sortDir: 'asc', products: [] };

// ---------- helpers ----------
async function api(path, options = {}) {
  const res = await fetch(API + path, { headers: { 'Content-Type': 'application/json' }, ...options });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const extra = data && data.validationErrors ? ': ' + Object.values(data.validationErrors).join(', ') : '';
    throw new Error((data && data.message ? data.message : 'Request failed') + extra);
  }
  return data;
}
function esc(v) {
  return String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
const money = (n) => '₹' + Number(n).toLocaleString('en-IN', { minimumFractionDigits: 2 });
const fmtDate = (d) => new Date(d).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
function toast(msg, ok = true) {
  const t = $('toast');
  t.textContent = msg;
  t.className = 'toast show ' + (ok ? 'ok' : 'err');
  setTimeout(() => (t.className = 'toast'), 3200);
}
function openModal(html) { $('modal').innerHTML = html; $('overlay').classList.remove('hidden'); }
function closeModal() { $('overlay').classList.add('hidden'); }
function pager(id, data, loader) {
  const el = $(id);
  el.innerHTML = `<button class="btn gray sm" ${data.page === 0 ? 'disabled' : ''}>◀ Prev</button>
    <span>Page ${data.totalPages === 0 ? 0 : data.page + 1} of ${data.totalPages} · ${data.totalElements} items</span>
    <button class="btn gray sm" ${data.last ? 'disabled' : ''}>Next ▶</button>`;
  const [prev, next] = el.querySelectorAll('button');
  prev.onclick = () => loader(data.page - 1);
  next.onclick = () => loader(data.page + 1);
}
const debounce = (fn, ms = 350) => { let t; return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); }; };

// ---------- stats ----------
async function loadStats() {
  try {
    const [all, low, alerts, moves] = await Promise.all([
      api('/products?size=1'), api('/products?lowStockOnly=true&size=1'),
      api('/reorder-alerts?resolved=false&size=1'), api('/stock-movements?size=1')]);
    countUp($('stTotal'), all.totalElements);
    countUp($('stLow'), low.totalElements);
    countUp($('stAlerts'), alerts.totalElements);
    countUp($('stMoves'), moves.totalElements);
  } catch (e) { toast('Cannot reach backend on ' + API, false); }
}

// ---------- products ----------
async function loadProducts(page = 0) {
  state.page = page;
  const q = new URLSearchParams({
    keyword: $('fKeyword').value, category: $('fCategory').value, lowStockOnly: $('fLow').checked,
    page, size: 8, sortBy: state.sortBy, sortDir: state.sortDir });
  try {
    const data = await api('/products?' + q);
    state.products = data.content;
    $('productBody').innerHTML = data.content.length ? data.content.map((p) => `
      <tr class="${p.lowStock ? 'lowrow' : ''}"><td><b>${esc(p.name)}</b></td><td>${esc(p.sku)}</td><td>${esc(p.category || '-')}</td>
      <td><b>${p.quantity}</b><div class="bar"><i style="width:${Math.min(100, p.quantity / Math.max(p.reorderLevel * 3, 1) * 100)}%"></i></div></td><td>${p.reorderLevel}</td><td>${money(p.price)}</td>
      <td><span class="badge ${p.lowStock ? 'b-low' : 'b-ok'}">${p.lowStock ? '⚠ Low' : '✔ OK'}</span></td>
      <td><button class="btn sm green" onclick="stockForm(${p.id})">Stock</button>
      <button class="btn sm blue" onclick="productForm(${p.id})">Edit</button>
      <button class="btn sm red" onclick="removeProduct(${p.id})">Delete</button></td></tr>`).join('')
      : '<tr><td colspan="8" class="empty">No products found</td></tr>';
    pager('productPager', data, loadProducts);
  } catch (e) { toast(e.message, false); }
}
function productForm(id) {
  const p = id ? state.products.find((x) => x.id === id) : {};
  openModal(`<h2>${id ? '✏️ Edit' : '➕ Add'} Product</h2>
    <form id="pForm">
      <label>Name</label><input name="name" value="${esc(p.name)}" required>
      <div class="row"><div><label>SKU</label><input name="sku" value="${esc(p.sku)}" required></div>
      <div><label>Category</label><input name="category" value="${esc(p.category)}"></div></div>
      <div class="row"><div><label>Quantity</label><input type="number" min="0" name="quantity" value="${p.quantity ?? 0}" required></div>
      <div><label>Reorder Level</label><input type="number" min="0" name="reorderLevel" value="${p.reorderLevel ?? 5}" required></div></div>
      <label>Price (₹)</label><input type="number" min="0" step="0.01" name="price" value="${p.price ?? ''}" required>
      <div class="actions"><button type="button" class="btn gray" onclick="closeModal()">Cancel</button>
      <button class="btn primary">Save</button></div></form>`);
  $('pForm').onsubmit = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    const body = { ...f, quantity: +f.quantity, reorderLevel: +f.reorderLevel, price: +f.price };
    try {
      await api(id ? '/products/' + id : '/products', { method: id ? 'PUT' : 'POST', body: JSON.stringify(body) });
      closeModal(); toast(id ? 'Product updated' : 'Product added'); refreshAll();
    } catch (err) { toast(err.message, false); }
  };
}
async function removeProduct(id) {
  if (!confirm('Delete this product and all its movements/alerts?')) return;
  try { await api('/products/' + id, { method: 'DELETE' }); toast('Product deleted'); refreshAll(); }
  catch (e) { toast(e.message, false); }
}
function stockForm(id) {
  const p = state.products.find((x) => x.id === id);
  openModal(`<h2>🔄 Stock Movement</h2><p style="color:#64748b">${esc(p.name)} — current stock: <b>${p.quantity}</b></p>
    <form id="sForm">
      <div class="row"><div><label>Type</label><select name="type"><option value="IN">IN (add stock)</option><option value="OUT">OUT (remove stock)</option></select></div>
      <div><label>Quantity</label><input type="number" min="1" name="quantity" value="1" required></div></div>
      <label>Note</label><textarea name="note" rows="2" maxlength="255"></textarea>
      <div class="actions"><button type="button" class="btn gray" onclick="closeModal()">Cancel</button>
      <button class="btn green">Record</button></div></form>`);
  $('sForm').onsubmit = async (e) => {
    e.preventDefault();
    const f = Object.fromEntries(new FormData(e.target));
    try {
      await api('/stock-movements', { method: 'POST', body: JSON.stringify({ productId: id, type: f.type, quantity: +f.quantity, note: f.note }) });
      closeModal(); toast('Stock updated'); refreshAll();
    } catch (err) { toast(err.message, false); }
  };
}

// ---------- movements ----------
async function loadMovements(page = 0) {
  try {
    const data = await api(`/stock-movements?page=${page}&size=10`);
    $('moveBody').innerHTML = data.content.length ? data.content.map((m) => `
      <tr><td>${fmtDate(m.createdAt)}</td><td><b>${esc(m.productName)}</b></td>
      <td><span class="badge ${m.type === 'IN' ? 'b-in' : 'b-out'}">${m.type === 'IN' ? '⬆ IN' : '⬇ OUT'}</span></td>
      <td>${m.quantity}</td><td>${esc(m.note || '-')}</td></tr>`).join('')
      : '<tr><td colspan="5" class="empty">No movements yet</td></tr>';
    pager('movePager', data, loadMovements);
  } catch (e) { toast(e.message, false); }
}

// ---------- alerts ----------
async function loadAlerts(page = 0) {
  const r = $('fResolved').value;
  try {
    const data = await api(`/reorder-alerts?page=${page}&size=10${r === '' ? '' : '&resolved=' + r}`);
    $('alertBody').innerHTML = data.content.length ? data.content.map((a) => `
      <tr><td>${fmtDate(a.createdAt)}</td><td><b>${esc(a.productName)}</b></td><td>${esc(a.sku)}</td>
      <td>${a.currentQuantity}</td><td>${a.reorderLevel}</td>
      <td><span class="badge ${a.resolved ? 'b-res' : 'b-open'}">${a.resolved ? '✔ Resolved' : '🚨 Open'}</span></td>
      <td>${a.resolved ? '-' : `<button class="btn sm amber" onclick="resolveAlert(${a.id})">Resolve</button>`}</td></tr>`).join('')
      : '<tr><td colspan="7" class="empty">No alerts 🎉</td></tr>';
    pager('alertPager', data, loadAlerts);
  } catch (e) { toast(e.message, false); }
}
async function resolveAlert(id) {
  try { await api(`/reorder-alerts/${id}/resolve`, { method: 'PUT' }); toast('Alert resolved'); refreshAll(); }
  catch (e) { toast(e.message, false); }
}

// ---------- wiring ----------
function refreshAll() {
  loadStats();
  const tab = document.querySelector('.tab.active').dataset.tab;
  if (tab === 'products') loadProducts(state.page);
  else if (tab === 'movements') loadMovements();
  else loadAlerts();
}
document.querySelectorAll('.tab').forEach((btn) => btn.onclick = () => {
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b === btn));
  ['products', 'movements', 'alerts'].forEach((t) => $('tab-' + t).classList.toggle('hidden', t !== btn.dataset.tab));
  refreshAll();
});
document.querySelectorAll('th[data-sort]').forEach((th) => th.onclick = () => {
  const f = th.dataset.sort;
  state.sortDir = state.sortBy === f && state.sortDir === 'asc' ? 'desc' : 'asc';
  state.sortBy = f;
  loadProducts(0);
});
const reload = debounce(() => loadProducts(0));
$('fKeyword').oninput = reload;
$('fCategory').oninput = reload;
$('fLow').onchange = () => loadProducts(0);
$('fResolved').onchange = () => loadAlerts(0);
$('btnAdd').onclick = () => productForm(null);
$('overlay').onclick = (e) => { if (e.target.id === 'overlay') closeModal(); };
refreshAll();


// ---------- creative extras ----------
function countUp(el, to) {
  const from = parseInt(el.textContent) || 0, t0 = performance.now();
  (function step(t) {
    const k = Math.min((t - t0) / 700, 1);
    el.textContent = Math.round(from + (to - from) * (1 - Math.pow(1 - k, 3)));
    if (k < 1) requestAnimationFrame(step);
  })(t0);
}
setInterval(() => ($('clock').textContent = new Date().toLocaleTimeString('en-IN')), 1000);

document.addEventListener('mousemove', (e) => { $('glow').style.left = e.clientX + 'px'; $('glow').style.top = e.clientY + 'px'; });

// floating neon particles with connecting lines
(function () {
  const c = $('fx'), x = c.getContext('2d'), colors = ['#22d3ee', '#e879f9', '#a3e635', '#fb923c'];
  let w, h, dots = [];
  function size() { w = c.width = innerWidth; h = c.height = innerHeight; }
  size(); addEventListener('resize', size);
  for (let i = 0; i < 70; i++) dots.push({ x: Math.random() * innerWidth, y: Math.random() * innerHeight, vx: (Math.random() - .5) * .5, vy: (Math.random() - .5) * .5, c: colors[i % 4] });
  (function draw() {
    x.clearRect(0, 0, w, h);
    dots.forEach((d, i) => {
      d.x = (d.x + d.vx + w) % w; d.y = (d.y + d.vy + h) % h;
      x.fillStyle = d.c; x.globalAlpha = .8; x.beginPath(); x.arc(d.x, d.y, 2, 0, 7); x.fill();
      for (let j = i + 1; j < dots.length; j++) {
        const dx = d.x - dots[j].x, dy = d.y - dots[j].y, dist = Math.hypot(dx, dy);
        if (dist < 110) { x.globalAlpha = (1 - dist / 110) * .25; x.strokeStyle = d.c; x.beginPath(); x.moveTo(d.x, d.y); x.lineTo(dots[j].x, dots[j].y); x.stroke(); }
      }
    });
    requestAnimationFrame(draw);
  })();
})();
