const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
const app = () => ({ globalData: { token: 'a', baseUrl: 'https://test/api', role: 'RESIDENT' } });
const event = value => ({ detail: { value } });

test('malformed successful HTTP response rejects instead of leaving request pending', async () => {
  let options;
  const runtime = createRuntime(app(), { request: o => { options = o; }, showToast() {} }, null);
  const result = runtime.require('utils/request.js').get('/test');
  const rejected = assert.rejects(result);
  assert.doesNotThrow(() => options.success({ statusCode: 200, data: null }));
  await rejected;
  assert.equal(options.timeout, 15000);
});

test('task cards retain destination coordinates for navigation', () => {
  const runtime = createRuntime();
  const card = runtime.require('utils/list-state.js').taskCard({ order: { id: 7, latitude: 30, longitude: 120 } });
  assert.equal(card.order.latitude, 30);
  assert.equal(card.order.longitude, 120);
});

test('workbench never navigates to a fabricated destination', () => {
  let opened = 0, tips = 0;
  const page = createRuntime(app(), { openLocation() { opened++; }, showToast() { tips++; } }).page('staff/workbench');
  page._tasks = [{ order: { id: 7 } }];
  page.openNavigation({ currentTarget: { dataset: { id: 7 } } });
  assert.equal(opened, 0);
  assert.equal(tips, 1);
});

test('workbench accepts string dataset ids and valid zero coordinates', () => {
  let target;
  const page = createRuntime(app(), { openLocation(o) { target = o; }, showToast() {} }).page('staff/workbench');
  page._tasks = [{ order: { id: 7, latitude: 0, longitude: 120 } }];
  page.openNavigation({ currentTarget: { dataset: { id: '7' } } });
  assert.equal(target?.latitude, 0);
});

test('GPS callback arriving after leaving workbench does not report location', () => {
  let gps, posts = 0;
  const page = createRuntime(app(), { getLocation(o) { gps = o; } }, { post() { posts++; return Promise.resolve(); } }).page('staff/workbench');
  page.reportCurrentLocation();
  page.onHide();
  gps.success({ latitude: 30, longitude: 120 });
  assert.equal(posts, 0);
});

test('tracking clears old markers when destination is missing', () => {
  const page = createRuntime().page('order/detail');
  page.updateMapState({ destLatitude: 30, destLongitude: 120 });
  page.updateMapState({ destLatitude: null, destLongitude: null });
  assert.equal(page.data.markers.length, 0);
  assert.equal(page.data.hasTrackingLocation, false);
});

test('tracking supports coordinates on the equator', () => {
  const page = createRuntime().page('order/detail');
  page.updateMapState({ destLatitude: 0, destLongitude: 120 });
  assert.equal(page.data.markers.length, 1);
});

test('cached order detail resumes polling after returning to page', async () => {
  let timers = 0;
  const runtime = createRuntime(app(), { createMapContext() {} }, { get: async () => ({ order: { status: 'WAIT_VISIT' } }) }, {
    setInterval() { timers++; return 1; }, clearInterval() {}
  });
  const page = runtime.page('order/detail');
  page.setData({ orderId: 7 });
  await page.loadDetail(7);
  page.onHide();
  page._hidden = false;
  await page.loadDetail(7);
  assert.equal(timers, 2);
});

test('tracking response from previous account cannot replace map', async () => {
  const pending = deferred(), state = app();
  const page = createRuntime(state, {}, { get: () => pending.promise }).page('order/detail');
  page.setData({ orderId: 7 });
  const result = page.fetchTracking();
  state.globalData.token = 'b';
  pending.resolve({ destLatitude: 30, destLongitude: 120 });
  await result;
  assert.equal(page.data.tracking, null);
});

test('failed cancel action settles and unlocks for retry', async () => {
  const page = createRuntime(app(), {}, { post: async () => { throw Error('offline'); } }).page('order/detail');
  await assert.doesNotReject(page.submitCancel());
  assert.equal(page._cancelling, false);
});

test('manual address editing invalidates map coordinates on both forms', () => {
  for (const name of ['order/create', 'user/elder-form']) {
    const page = createRuntime().page(name);
    page.setData({ address: 'old', latitude: 30, longitude: 120 });
    page.onInputAddress(event('new'));
    assert.equal(page.data.latitude, null, name);
    assert.equal(page.data.longitude, null, name);
  }
});

test('elder without phone is normalized to an empty form field', () => {
  const page = createRuntime().page('order/create');
  page.setData({ elders: [{ id: 1, name: '老人' }] });
  page.selectElder(0);
  assert.equal(page.data.contactPhone, '');
});

test('booking elder response from previous session is ignored', async () => {
  const pending = deferred(), state = app();
  const page = createRuntime(state, {}, { get: () => pending.promise }).page('order/create');
  const result = page.loadElders();
  state.globalData.token = 'b';
  pending.resolve([{ id: 1, name: '旧账号' }]);
  await result;
  assert.equal(page.data.elders.length, 0);
});

test('changing family search phone clears previous selected elder', () => {
  const page = createRuntime().page('user/family');
  page.setData({ targetElder: { id: 7 }, selectedElderIndex: 0 });
  page.onInputSearchPhone(event('13800000000'));
  assert.equal(page.data.targetElder, null);
  assert.equal(page.data.selectedElderIndex, -1);
});

test('late family search cannot restore an elder after phone was edited', async () => {
  const pending = deferred();
  const page = createRuntime(app(), { showLoading() {}, hideLoading() {}, showToast() {} }, { get: () => pending.promise }).page('user/family');
  page.onInputSearchPhone(event('13800000000'));
  const result = page.searchElder();
  page.onInputSearchPhone(event('13900000000'));
  pending.resolve({ id: 7, name: '旧查询' });
  await result;
  assert.equal(page.data.targetElder, null);
});

test('reopening a closing sheet cancels the old close timer and dragging has no delay', () => {
  const timers = new Map(); let id = 0;
  const sheet = createRuntime(undefined, {}, undefined, {
    setTimeout(fn) { timers.set(++id, fn); return id; },
    clearTimeout(key) { timers.delete(key); }
  }).component('bottom-sheet', {});
  sheet.observe('show', true);
  sheet.observe('show', false);
  sheet.observe('show', true);
  for (const timer of timers.values()) timer();
  assert.equal(sheet.data.rendered, true);
  sheet.onStart({ touches: [{ clientY: 10 }] });
  assert.equal(sheet.data.dragging, true);
  sheet.onCancel();
  assert.equal(sheet.data.dragging, false);
  assert.equal(sheet.data.drag, 0);
});

test('detaching a closing sheet cancels delayed updates', () => {
  const timers = new Map();
  const sheet = createRuntime(undefined, {}, undefined, {
    setTimeout(fn) { timers.set(1, fn); return 1; }, clearTimeout(key) { timers.delete(key); }
  }).component('bottom-sheet', {});
  sheet.observe('show', true);
  sheet.observe('show', false);
  sheet.lifecycle('lifetimes', 'detached');
  assert.equal(timers.size, 0);
});

test('WeChat login locks duplicates and always releases loading after failure', async () => {
  let posts = 0, hidden = 0;
  const pending = deferred();
  const page = createRuntime(app(), { login(o) { o.success({ code: 'test-code' }); }, showLoading() {}, hideLoading() { hidden++; } }, {
    post() { posts++; return pending.promise; }
  }).page('auth/login');
  page.setData({ isAgreed: true });
  const first = page.handleQuickLogin();
  await page.handleQuickLogin();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts, 1);
  pending.reject(Error('offline'));
  await first;
  assert.equal(page.data.loggingIn, false);
  assert.equal(hidden, 1);
});

test('login response cannot overwrite a new session or navigate after unload', async () => {
  for (const unload of [true, false]) {
    const pending = deferred(), state = app(); let writes = 0;
    const page = createRuntime(state, { login(o) { o.success({ code: 'test-code' }); }, showLoading() {}, hideLoading() {}, setStorageSync() { writes++; } }, {
      post: () => pending.promise
    }).page('auth/login');
    page.setData({ isAgreed: true });
    const task = page.handleQuickLogin();
    await Promise.resolve();
    if (unload) page.onUnload(); else state.globalData.token = 'new-session';
    pending.resolve({ token: 'old-response', userInfo: { role: 'RESIDENT' } });
    await task;
    assert.equal(writes, 0);
    assert.equal(state.globalData.token, unload ? 'a' : 'new-session');
  }
});

test('booking defaults to a future slot inside picker hours, even in the evening', () => {
  for (const date of ['2026-10-21T10:23:00', '2026-10-21T17:50:00', '2026-10-21T23:58:00']) {
    class Clock extends Date { constructor(...args) { super(...(args.length ? args : [date])); } static now() { return new Date(date).getTime(); } }
    const page = createRuntime(undefined, {}, undefined, { Date: Clock }).page('order/create');
    page.initDates();
    const slot = new Date(`${page.data.selectedDate}T${page.data.selectedTime}:00`);
    assert.ok(slot.getTime() >= new Date(date).getTime() + 30 * 60000);
    assert.ok(slot.getHours() >= 8 && slot.getHours() < 18);
  }
});

test('switching servers clears service from the old server even if new request fails', async () => {
  const state = app(); let fail = false;
  const page = createRuntime(state, {}, { get: async () => {
    if (fail) throw Error('offline');
    return { id: 1, name: '旧服务' };
  } }, { console: { warn() {} } }).page('service/detail');
  await page.onLoad({ id: 1 });
  assert.equal(page.data.service.name, '旧服务');
  fail = true;
  state.globalData.baseUrl = 'https://new/api';
  await page.onShow();
  assert.equal(page.data.service, null);
  assert.equal(page.data.loadError, true);
});

test('family binding submit is single flight and an error permits retry', async () => {
  let posts = 0;
  const pending = deferred();
  const page = createRuntime(app(), {}, { post() { posts++; return pending.promise; } }).page('user/family');
  page.setData({ targetElder: { id: 1 }, relation: '父子' });
  const task = page.submitApply();
  await page.submitApply();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(posts, 1);
  pending.reject(Error('offline'));
  await task;
  assert.equal(page.data.applying, false);
});

test('saving a directly opened elder form can return without a back stack', () => {
  let route;
  const page = createRuntime(app(), { switchTab(o) { route = o.url; } }, undefined, { getCurrentPages: () => [{ route: 'pages/user/elder-form' }] }).page('user/elder-form');
  page.returnToPrevious();
  assert.equal(route, '/pages/user/profile');
});

test('review cannot be sent without an order id', () => {
  let posts = 0, tips = 0;
  const page = createRuntime(app(), { showToast() { tips++; } }, { post() { posts++; } }).page('order/confirm');
  page.submitReview();
  assert.equal(posts, 0);
  assert.equal(tips, 1);
});

test('tracking status change refreshes order actions and stops completed-order polling', async () => {
  const paths = [];
  const page = createRuntime(app(), {}, { get: async path => {
    paths.push(path);
    return path.endsWith('/tracking') ? { status: 'WAIT_CONFIRM' } : { order: { status: 'WAIT_CONFIRM' } };
  } }).page('order/detail');
  page.setData({ orderId: 7, orderVO: { order: { status: 'IN_SERVICE' } } });
  await page.fetchTracking();
  assert.equal(paths.length, 2);
  assert.equal(page.data.orderVO.order.status, 'WAIT_CONFIRM');
  assert.equal(page.data.canCancel, false);
  assert.equal(page._trackingTimer || null, null);
});

test('booking rejects past appointment time without creating an order', () => {
  let posts = 0, tip;
  const page = createRuntime(app(), { showToast(o) { tip = o.title; } }, { post() { posts++; } }).page('order/create');
  page.setData({ selectedElder: { id: 1 }, service: { id: 1 }, selectedDate: '2000-01-01', selectedTime: '09:00' });
  page.submitOrder();
  assert.equal(posts, 0);
  assert.match(tip, /30分钟/);
});

test('workbench can find older active tasks beyond the first twenty orders', async () => {
  const calls = [];
  const page = createRuntime(app(), {}, { get: async (url, query) => {
    calls.push(query.pageNum);
    return {
      total: '21', records: query.pageNum === 1
        ? Array.from({ length: 20 }, (_, i) => ({ order: { id: i + 1, status: 'COMPLETED' } }))
        : [{ order: { id: 21, status: 'WAIT_VISIT', latitude: 30, longitude: 120 } }]
    };
  } }).page('staff/workbench');
  page.filterTasks(1);
  await page.loadTasks();
  assert.equal(page.data.tasksHasMore, true);
  const task = page.loadMoreTasks();
  await page.loadMoreTasks();
  await task;
  assert.deepEqual(calls, [1, 2]);
  assert.equal(page.data.filteredTasks[0].order.id, 21);
  assert.equal(page.data.tasksHasMore, false);
  assert.equal(page.data.tasksLoading, false);
});


test('workbench stops location polling on auth denial and resumes after openSetting grants permission', () => {
  let cleared = 0, granted = false;
  const page = createRuntime(app(), {
    getLocation(o) {
      if (!granted) o.fail?.({ errMsg: 'getLocation:fail auth deny' });
      else o.success?.({ latitude: 30, longitude: 120 });
      o.complete?.();
    },
    openSetting(o) {
      granted = true;
      o.success?.({ authSetting: { 'scope.userLocation': true } });
    }
  }, { post: () => Promise.resolve() }, {
    setInterval: () => 99,
    clearInterval: () => { cleared++; }
  }).page('staff/workbench');
  page.startLocationReporting();
  assert.equal(page.data.locationAuthDenied, true);
  assert.equal(page._locationTimer, null);
  assert.ok(cleared >= 1);
  page.openLocationSetting();
  assert.equal(page.data.locationAuthDenied, false);
  assert.equal(page._locationTimer, 99);
});

test('workbench persists fulfillment record drafts per order and clears draft after submit', async () => {
  const store = new Map();
  const wxMock = {
    getStorageSync: k => store.get(k),
    setStorageSync: (k, v) => store.set(k, v),
    removeStorageSync: k => store.delete(k),
    showToast() {}
  };
  const page = createRuntime(app(), wxMock, { post: () => Promise.resolve(), get: () => Promise.resolve({}) }).page('staff/workbench');
  page.openSubmitRecordModal({ currentTarget: { dataset: { id: 42 } } });
  page.onInputSummary({ detail: { value: '老人测量血压正常' } });
  assert.equal(store.get('workbench_record_draft_42')?.summary, '老人测量血压正常');

  // Reopening the modal for the same order restores the draft
  const rebuilt = createRuntime(app(), wxMock, { post: () => Promise.resolve(), get: () => Promise.resolve({}) }).page('staff/workbench');
  rebuilt.openSubmitRecordModal({ currentTarget: { dataset: { id: 42 } } });
  assert.equal(rebuilt.data.recordSummary, '老人测量血压正常');

  await rebuilt.submitRecord();
  assert.equal(store.has('workbench_record_draft_42'), false);
});
