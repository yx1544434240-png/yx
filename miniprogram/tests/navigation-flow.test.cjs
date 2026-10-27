const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
const appData = () => ({ globalData: { token: 'account-a', role: 'RESIDENT', baseUrl: 'local', isElderMode: false } });
const noDelay = () => { throw new Error('navigation must not wait for a timer'); };

test('repeated taps open one detail page and a failed route unlocks immediately', () => {
  const calls = [];
  const rt = createRuntime(appData(), { navigateTo: options => calls.push(options) });
  const page = rt.page('index/index');
  const tap = { currentTarget: { dataset: { id: '1' } } };
  page.goToServiceDetail(tap); page.goToServiceDetail(tap);
  assert.equal(calls.length, 1);
  calls[0].complete({ errMsg: 'navigateTo:fail' });
  page.goToServiceDetail(tap);
  assert.equal(calls.length, 2);
});

test('a protected detail replaces itself with login and preserves all return parameters', () => {
  const calls = [];
  const app = appData(); app.globalData.token = '';
  const rt = createRuntime(app, { redirectTo: o => calls.push(o) }, {}, {
    getCurrentPages: () => [{ route: 'pages/order/confirm', options: { orderId: '42' } }]
  });
  const page = rt.page('order/confirm'); page.onLoad({ orderId: '42' }); page.onShow(); page.onShow();
  assert.equal(calls.length, 1);
  assert.equal(decodeURIComponent(calls[0].url.split('redirect=')[1]), '/pages/order/confirm?orderId=42');
});

test('login resumes immediately using switchTab for tabs and redirectTo for details', () => {
  for (const [target, method] of [['/pages/order/list', 'switchTab'], ['/pages/order/create?serviceId=9', 'redirectTo']]) {
    const app = appData(); app.globalData.token = '';
    const calls = [];
    const rt = createRuntime(app, { setStorageSync() {}, showToast() {}, [method]: o => calls.push(o) }, {}, { setTimeout: noDelay });
    const page = rt.page('auth/login'); page.onLoad({ redirect: encodeURIComponent(target) });
    page.onLoginSuccess({ token: 'new-token', userInfo: { role: 'RESIDENT', username: 'test' } });
    assert.equal(calls.length, 1);
    assert.equal(calls[0].url, target);
  }
});

test('concurrent 401 responses clear the session and open login only once', async () => {
  const app = appData(); const requests = [], routes = [], toasts = [];
  const rt = createRuntime(app, {
    request: o => requests.push(o), removeStorageSync() {}, showToast: o => toasts.push(o), navigateTo: o => routes.push(o)
  }, null, { setTimeout: noDelay, getCurrentPages: () => [{ route: 'pages/index/index' }] });
  const api = rt.require('utils/request');
  const pending = Promise.allSettled([api.get('/orders/page'), api.get('/elders/my-list')]);
  requests[0].success({ statusCode: 401 });
  requests[1].success({ statusCode: 200, data: { code: 401 } });
  await pending;
  assert.equal(routes.length, 1);
  assert.equal(toasts.length, 1);
  assert.equal(app.globalData.token, '');
  assert.equal(app.globalData.role, '');
});

test('an authentication redirect waits only for the current native route to complete', () => {
  const calls = [];
  const rt = createRuntime(appData(), { navigateTo: o => calls.push(o) });
  const nav = rt.require('utils/navigation');
  nav.navigateTo({ url: '/pages/service/detail?id=1' });
  nav.toLogin('/pages/order/list'); nav.toLogin('/pages/order/list');
  assert.equal(calls.length, 1);
  calls[0].complete();
  assert.equal(calls.length, 2);
  assert.match(calls[1].url, /^\/pages\/auth\/login\?redirect=/);
});

test('opening login with an existing session resumes after its native transition', () => {
  const loginRoutes = [], tabRoutes = [];
  const rt = createRuntime(appData(), { navigateTo: o => loginRoutes.push(o), switchTab: o => tabRoutes.push(o) });
  rt.require('utils/navigation').navigateTo({ url: '/pages/auth/login' });
  rt.page('auth/login').onLoad({ redirect: encodeURIComponent('/pages/order/list') });
  assert.equal(tabRoutes.length, 0);
  loginRoutes[0].complete();
  assert.equal(tabRoutes[0].url, '/pages/order/list');
  rt.require('utils/navigation').afterLogin('/pages/order/list');
  tabRoutes[0].complete();
  assert.equal(tabRoutes.length, 1, 'duplicate login completion must not switch the same tab twice');
});

test('a tab tap during another route cannot leave the tab bar permanently locked', () => {
  const pending = [], tabs = [];
  const rt = createRuntime(appData(), { navigateTo: o => pending.push(o), switchTab: o => tabs.push(o) });
  rt.require('utils/navigation').navigateTo({ url: '/pages/service/detail?id=1' });
  const bar = rt.component('../custom-tab-bar', {});
  const tap = { currentTarget: { dataset: { index: 1 } } };
  bar.switchTab(tap);
  assert.equal(bar._switching, false); assert.equal(bar.data.selected, 0);
  pending[0].complete(); bar.switchTab(tap);
  assert.equal(tabs.length, 1);
});

test('late 401 responses from another account or server do not clear the new session', async () => {
  for (const change of [app => { app.globalData.token = 'account-b'; }, app => { app.globalData.baseUrl = 'new-server'; }]) {
    const app = appData(); const requests = [];
    const rt = createRuntime(app, {
      request: o => requests.push(o), removeStorageSync() { assert.fail('new session removed'); }, showToast() { assert.fail('stale error shown'); }
    }, null);
    const result = rt.require('utils/request').get('/orders/page').catch(() => {});
    change(app);
    requests[0].success({ statusCode: 401 }); await result;
    assert.ok(app.globalData.token);
  }
});

test('an incorrect password stays on the login form without a route loop', async () => {
  const app = appData(); const requests = [], toasts = [];
  const rt = createRuntime(app, { request: o => requests.push(o), showToast: o => toasts.push(o) }, null);
  const result = rt.require('utils/request').post('/auth/login').catch(() => {});
  requests[0].success({ statusCode: 200, data: { code: 401, message: '密码错误' } });
  await result;
  assert.equal(app.globalData.token, 'account-a');
  assert.equal(toasts[0].title, '密码错误');
});

test('service detail and booking reuse the catalog without two extra network round trips', async () => {
  let calls = 0;
  const rt = createRuntime(appData(), {}, { get: async () => { calls++; return [{ id: '8', name: '助餐', price: 15 }]; } });
  await rt.require('utils/service-catalog').loadCatalog();
  const detail = rt.page('service/detail'); const opening = detail.onLoad({ id: '8' });
  assert.equal(detail.data.service.name, '助餐', 'content is available synchronously during onLoad');
  await opening;
  const booking = rt.page('order/create'); booking.onLoad({ serviceId: '8' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(booking.data.service.id, '8');
  assert.equal(calls, 1);
});

test('a detail failure exits loading and a retry can recover', async () => {
  let calls = 0;
  const rt = createRuntime(appData(), {}, { get: async () => { if (++calls === 1) throw new Error('offline'); return { id: '1', name: '服务' }; } });
  const page = rt.page('service/detail'); await page.onLoad({ id: '1' });
  assert.equal(page.data.loading, false); assert.equal(page.data.loadError, true);
  await page.loadServiceDetail();
  assert.equal(page.data.service.name, '服务'); assert.equal(page.data.loadError, false);
});

test('service cache expires and never crosses backend boundaries', async () => {
  let now = 1000, calls = 0;
  class Clock extends Date { static now() { return now; } }
  const rt = createRuntime(appData(), {}, { get: async () => { calls++; return { id: '1', name: '服务' }; } }, { Date: Clock });
  const catalog = rt.require('utils/service-catalog');
  await Promise.all([catalog.loadService('1'), catalog.loadService('1')]);
  assert.equal(calls, 1);
  now += 61000; assert.equal(catalog.getCachedService('1'), null);
  await catalog.loadService('1'); assert.equal(calls, 2);
  rt.app.globalData.baseUrl = 'another-server'; assert.equal(catalog.getCachedService('1'), null);
});

test('returning to a service detail after a backend change rejects the old response', async () => {
  const old = deferred(), fresh = deferred(); let calls = 0;
  const rt = createRuntime(appData(), {}, { get: () => ++calls === 1 ? old.promise : fresh.promise });
  const page = rt.page('service/detail'); const a = page.onLoad({ id: '1' });
  rt.app.globalData.baseUrl = 'new-server'; const b = page.onShow();
  fresh.resolve({ id: '1', name: '新服务' }); await b;
  old.reject(new Error('old server offline')); await a;
  assert.equal(page.data.service.name, '新服务');
  assert.equal(page.data.loadError, false); assert.equal(page.data.loading, false);
});

test('returning to cached tab pages sends no redundant render patches', async () => {
  const rt = createRuntime(appData(), {}, { get: async url => url === '/orders/page' ? { records: [] } : [] });
  for (const name of ['service/list', 'order/list']) {
    const page = rt.page(name); page.onLoad(); await page.onShow();
    const count = page.patches.length;
    await page.onShow();
    assert.equal(page.patches.length, count, name);
  }
});

test('returning after an interrupted order swipe releases distant mounted lists', async () => {
  const rt = createRuntime(appData(), {}, { get: async () => ({ records: [] }) });
  const page = rt.page('order/list'); page.onLoad(); await page.onShow();
  await page.onTabClick({ currentTarget: { dataset: { index: 5 } } });
  assert.equal(page.data.visibleTabs.filter(Boolean).length, 4);
  page.onHide(); await page.onShow();
  assert.equal(page.data.visibleTabs.filter(Boolean).length, 2);
});

test('order detail coalesces reads, reuses a short return and refreshes after mutations', async () => {
  const first = deferred(); let calls = 0;
  const rt = createRuntime(appData(), {}, { get: () => { calls++; return calls === 1 ? first.promise : Promise.resolve({ order: { id: '1', status: 'WAIT_VISIT' } }); } });
  const page = rt.page('order/detail'); page.onLoad({ id: '1' });
  const a = page.onShow(), b = page.onShow(); assert.equal(calls, 1);
  first.resolve({ order: { id: '1', status: 'WAIT_ACCEPT' } }); await Promise.all([a, b]);
  await page.onShow(); assert.equal(calls, 1);
  rt.require('utils/list-state').markOrdersChanged(); await page.onShow();
  assert.equal(calls, 2); assert.equal(page.data.orderVO.order.status, 'WAIT_VISIT');
});

test('successful booking redirects immediately and retries routing without creating a second order', async () => {
  const calls = []; let posts = 0;
  const rt = createRuntime(appData(), { showToast() {}, redirectTo: o => calls.push(o) }, { post: async () => { posts++; return { id: '42', status: 'WAIT_ACCEPT' }; } }, { setTimeout: fn => { fn(); return 0; } });
  const page = rt.page('order/create');
  Object.assign(page.data, { selectedElder: { id: '1' }, service: { id: '2' }, selectedDate: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString().slice(0, 10), selectedTime: '09:00', address: '社区', contactName: '居民', contactPhone: '123' });
  await page.submitOrder();
  assert.equal(calls[0].url, '/pages/order/detail?id=42');
  calls[0].fail(); calls[0].complete();
  page.submitOrder(); assert.equal(posts, 1); assert.equal(calls.length, 2);
});

test('elder saves return immediately while duplicate taps stay locked', async () => {
  for (const [name, action] of [['user/elder-form', 'saveElder']]) {
    const saved = deferred(); const calls = []; let posts = 0;
    const rt = createRuntime(appData(), { showToast() {}, navigateBack: o => calls.push(o) }, { post: () => { posts++; return saved.promise; } }, { setTimeout: noDelay });
    const page = rt.page(name); page.data.name = '居民'; page.data.orderId = '1';
    const first = page[action](); page[action](); assert.equal(posts, 1);
    saved.resolve({}); await first;
    assert.equal(calls.length, 1); assert.equal(page.data.submitting, true);
  }
});
