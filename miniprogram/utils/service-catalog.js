const request = require('./request.js');
const { serviceCard } = require('./list-state.js');
const TTL = 60 * 1000;
let cache = null;
let pending = null;
let context = '';
const details = new Map();

function getAppSafe() {
  try {
    return getApp();
  } catch (_) {
    return null;
  }
}

function getCachedService(id) {
  const app = getAppSafe();
  const key = app?.globalData?.baseUrl || '';
  const entry = details.get(String(id));
  if (entry && entry.context === key && Date.now() - entry.updatedAt < TTL) return entry.row;
  if (cache && context === key && Date.now() - cache.updatedAt < TTL) return cache.rows.find(row => String(row.id) === String(id)) || null;
  return null;
}

function loadService(id) {
  const row = getCachedService(id);
  if (row) return Promise.resolve(row);
  const app = getAppSafe();
  const key = app?.globalData?.baseUrl || '';
  const entry = details.get(String(id));
  if (entry?.context === key && entry.promise) return entry.promise;
  const next = { context: key };
  const task = request.get(`/service-items/${id}`).then(data => {
    const curApp = getAppSafe();
    if (curApp?.globalData?.baseUrl === key && details.get(String(id)) === next) {
      next.row = serviceCard(data);
      next.updatedAt = Date.now();
    }
    return serviceCard(data);
  }).finally(() => { next.promise = null; });
  next.promise = task;
  details.set(String(id), next);
  return task;
}

function loadCatalog(force = false) {
  const app = getAppSafe();
  const key = app?.globalData?.baseUrl || '';
  if (key !== context) { cache = null; pending = null; context = key; }
  if (pending) return pending;
  if (!force && cache && Date.now() - cache.updatedAt < TTL) return Promise.resolve(cache.rows);
  const task = request.get('/service-items/list-available').then(data => {
    const rows = (data || []).map(serviceCard);
    if (context === key && pending === task) { cache = { rows, updatedAt: Date.now() }; details.clear(); }
    return rows;
  }).finally(() => { if (pending === task) pending = null; });
  pending = task;
  return task;
}

module.exports = { loadCatalog, getCachedService, loadService };
