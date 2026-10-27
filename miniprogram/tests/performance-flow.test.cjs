const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
const tap = index => ({ currentTarget: { dataset: { index } } });
const finish = current => ({ detail: { current } });
function rows(start, count = 10) {
  return Array.from({ length: count }, (_, i) => ({
    order: { id: String(start + i), orderNo: `O-${start + i}`, status: 'WAIT_VISIT', serviceNameSnapshot: '助餐',
      priceSnapshot: 15, plannedStartAt: '2026-10-20 10:00:00', addressSnapshot: '社区', contactPhoneSnapshot: '123' },
    elder: { name: '赵先生', age: 80, healthNote: 'not-a-list-field' },
    logs: Array(30).fill({ reason: 'detail only' }), serviceRecord: { serviceSummary: 'detail only' }
  }));
}
function appData() { return { globalData: { token: 'account-a', baseUrl: 'local', isElderMode: false } }; }

test('catalog requests are shared across home and service pages, including an empty result', async () => {
  let calls = 0;
  const pending = deferred();
  const rt = createRuntime(appData(), {}, { get: () => { calls++; return pending.promise; } });
  const home = rt.page('index/index');
  const service = rt.page('service/list');
  service.onLoad();
  const first = home.loadServiceItems();
  const second = service.onShow();
  assert.equal(calls, 1);
  pending.resolve([]);
  await Promise.all([first, second]);
  await service.onShow();
  assert.equal(calls, 1, 'a valid empty catalog should also be cached');
});

test('catalog cache expires and a forced refresh is coalesced', async () => {
  let now = 1000, calls = 0;
  class Clock extends Date { static now() { return now; } }
  const rt = createRuntime(appData(), {}, { get: async () => { calls++; return []; } }, { Date: Clock });
  const catalog = rt.require('utils/service-catalog');
  await catalog.loadCatalog();
  now += 59000;
  await catalog.loadCatalog();
  assert.equal(calls, 1);
  now += 2000;
  await catalog.loadCatalog();
  assert.equal(calls, 2);
  await Promise.all([catalog.loadCatalog(true), catalog.loadCatalog(true)]);
  assert.equal(calls, 3);
});

test('changing the backend during a catalog request discards the old server result', async () => {
  const old = deferred(), fresh = deferred(); let calls = 0;
  const rt = createRuntime(appData(), {}, { get: () => (++calls === 1 ? old.promise : fresh.promise) });
  const page = rt.page('service/list'); page.onLoad(); const first = page.onShow();
  rt.app.globalData.baseUrl = 'another-server'; const second = page.onShow();
  fresh.resolve([{ id: '2', name: '新社区服务', category: 'MEAL' }]); await second;
  old.resolve([{ id: '1', name: '旧社区服务', category: 'MEAL' }]); await first;
  assert.equal(page.data.catServices[0][0].name, '新社区服务');
});

test('service views retain transition endpoints then release distant lists', async () => {
  const rt = createRuntime(appData(), {}, { get: async () => ['MEAL','CLEANING','ERRAND','ESCORT'].map((category, id) => ({ id, category, name: category })) });
  const page = rt.page('service/list');
  page.onLoad(); await page.onShow();
  page.changeCat(tap(4));
  assert.equal(page.data.visibleTabs[0], true, 'the outgoing view remains during native motion');
  assert.equal(page.data.visibleTabs[4], true);
  page.onAnimationFinish(finish(4));
  assert.equal(page.data.visibleTabs.filter(Boolean).length, 2);
  assert.equal(page.data.catServices[0].length, 0);
  assert.equal(page.data.catServices[4][0].category, 'ESCORT');
  assert.equal(Object.hasOwn(page.data, 'allServices'), false);
});

test('pagination transmits only new card rows and excludes detail payloads', async () => {
  const rt = createRuntime(appData(), {}, { get: async (_, query) => ({ records: rows((query.pageNum - 1) * 10) }) });
  const page = rt.page('order/list');
  page.onLoad(); await page.onShow();
  page.patches.length = 0;
  await page.onReachBottomScroll(tap(0));
  assert.equal(page.data.tabData[0].list.length, 20);
  const keys = page.patches.flatMap(Object.keys);
  assert.equal(keys.includes('tabData[0].list'), false, 'existing rows must not be retransmitted');
  assert.equal(keys.filter(key => /^tabData\[0\]\.list\[\d+\]$/.test(key)).length, 10);
  assert.equal(JSON.stringify(page.data).includes('detail only'), false);
  assert.equal(JSON.stringify(page.data).includes('healthNote'), false);
});

test('concurrent bottom events share one request and failed pages retry without skipping', async () => {
  const next = deferred(); let calls = 0;
  const rt = createRuntime(appData(), {}, { get: () => { calls++; return calls === 1 ? Promise.resolve({ records: rows(0) }) : next.promise; } });
  const page = rt.page('order/list'); page.onLoad(); await page.onShow();
  const first = page.onReachBottomScroll(tap(0));
  const second = page.onReachBottomScroll(tap(0));
  assert.equal(calls, 2);
  next.reject(new Error('offline'));
  await Promise.all([first, second]);
  assert.equal(page._cache[0].nextPage, 2);
  assert.equal(page.data.tabData[0].list.length, 10);
  assert.equal(page.data.tabData[0].error, true);
});

test('a return within TTL reuses orders but mutations and elapsed TTL refresh', async () => {
  let now = 1000, calls = 0;
  class Clock extends Date { static now() { return now; } }
  const app = appData();
  const rt = createRuntime(app, {}, { get: async () => { calls++; return { records: [] }; } }, { Date: Clock });
  const page = rt.page('order/list'); page.onLoad(); await page.onShow(); await page.onShow();
  assert.equal(calls, 1);
  rt.require('utils/list-state').markOrdersChanged();
  await page.onShow(); assert.equal(calls, 2);
  now += 16000;
  await page.onShow(); assert.equal(calls, 3);
});

test('reading a page longer than the cache TTL still loads the next page', async () => {
  let now = 1000;
  class Clock extends Date { static now() { return now; } }
  const requestedPages = [];
  const rt = createRuntime(appData(), {}, { get: async (_, query) => {
    requestedPages.push(query.pageNum);
    return { records: rows(query.pageNum * 10) };
  } }, { Date: Clock });
  const page = rt.page('order/list'); page.onLoad(); await page.onShow();
  now += 60000;
  await page.onReachBottomScroll(tap(0));
  assert.deepEqual(requestedPages, [1, 2]);
  assert.equal(page.data.tabData[0].list.length, 20);
});

test('a mutation during an in-flight order read queues one fresh read', async () => {
  const old = deferred(), fresh = deferred(); let calls = 0;
  const rt = createRuntime(appData(), {}, { get: () => (++calls === 1 ? old.promise : fresh.promise) });
  const page = rt.page('order/list'); page.onLoad(); const first = page.onShow();
  rt.require('utils/list-state').markOrdersChanged();
  const second = page.onShow();
  old.resolve({ records: rows(100, 1) });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls, 2);
  assert.equal(page.data.tabData[0].list.length, 0);
  fresh.resolve({ records: rows(200, 1) }); await Promise.all([first, second]);
  assert.equal(page.data.tabData[0].list[0].order.id, '200');
});

test('ten pages keep each append payload bounded rather than growing with list history', async () => {
  const rt = createRuntime(appData(), {}, { get: async (_, query) => ({ records: rows(query.pageNum * 10) }) });
  const page = rt.page('order/list'); page.onLoad(); await page.onShow();
  const sizes = [];
  for (let i = 0; i < 9; i++) {
    page.patches.length = 0;
    await page.onReachBottomScroll(tap(0));
    const rowPatch = page.patches.find(patch => Object.keys(patch).some(key => key.includes('.list[')));
    assert.equal(Object.keys(rowPatch).filter(key => key.includes('.list[')).length, 10);
    sizes.push(JSON.stringify(rowPatch).length);
  }
  assert.equal(page.data.tabData[0].list.length, 100);
  assert.ok(Math.max(...sizes) < Math.min(...sizes) * 1.15);
});

test('old account responses cannot overwrite a newly signed-in account', async () => {
  const old = deferred(); const fresh = deferred(); let calls = 0;
  const app = appData();
  const rt = createRuntime(app, {}, { get: () => (++calls === 1 ? old.promise : fresh.promise) });
  const page = rt.page('order/list'); page.onLoad(); const first = page.onShow();
  app.globalData.token = 'account-b'; const second = page.onShow();
  fresh.resolve({ records: rows(200, 1) }); await second;
  old.resolve({ records: rows(100, 1) }); await first;
  assert.equal(page.data.tabData[0].list[0].order.id, '200');
});

test('rapid tab clicks ignore obsolete programmatic change events and defer response rendering', async () => {
  const requests = [];
  const rt = createRuntime(appData(), {}, { get: () => { const item = deferred(); requests.push(item); return item.promise; } });
  const page = rt.page('order/list'); page.onLoad(); const initial = page.onShow();
  requests[0].resolve({ records: rows(0, 1) }); await initial;
  const first = page.onTabClick(tap(2));
  const last = page.onTabClick(tap(5));
  page.onSwiperChange({ detail: { current: 2, source: '' } });
  assert.equal(page.data.currentTabIndex, 5);
  requests[1].resolve({ records: rows(20, 1) });
  requests[2].resolve({ records: rows(50, 1) });
  await Promise.all([first, last]);
  assert.equal(page.data.tabData[5].list.length, 0, 'avoid list hydration during the slide');
  page.onAnimationFinish(finish(2));
  assert.equal(page.data.currentTabIndex, 5);
  page.onAnimationFinish(finish(5));
  assert.equal(page.data.tabData[5].list[0].order.id, '50');
  assert.equal(page.data.visibleTabs.filter(Boolean).length, 2);
  assert.equal(page.data.tabData[0].list.length, 0);
});

test('profile order shortcuts use switchTab and the requested status is consumed once', async () => {
  let url;
  const rt = createRuntime(appData(), { switchTab: value => { url = value.url; } }, { get: async () => ({ records: [] }) });
  const profile = rt.page('user/profile');
  profile.goToOrderList({ currentTarget: { dataset: { status: 'WAIT_CONFIRM' } } });
  assert.equal(url, '/pages/order/list');
  const page = rt.page('order/list'); page.onLoad(); await page.onShow();
  assert.equal(page.data.currentTabIndex, 4);
  assert.equal(page.data.tabData[4].loaded, true, 'first entry must not wait for an animation that has not started');
  assert.equal(Object.hasOwn(rt.app.globalData, 'orderStatusFilter'), false);
  await page.onTabClick(tap(2)); page.onAnimationFinish(finish(2)); await page.onShow();
  assert.equal(page.data.currentTabIndex, 2);
});

test('countdown catches up with wall time, expires once and stops in background', () => {
  let now = new Date('2026-10-20T09:59:50').getTime();
  class Clock extends Date { static now() { return now; } }
  const timers = new Map(); let next = 0;
  const rt = createRuntime(appData(), {}, {}, {
    Date: Clock, setInterval: tick => { timers.set(++next, tick); return next; }, clearInterval: id => timers.delete(id)
  });
  const counter = rt.component('dispatch-countdown', { deadline: '2026-10-20 10:00:00' });
  counter.lifecycle('lifetimes', 'attached');
  assert.equal(counter.data.seconds, 10);
  now += 6000; [...timers.values()][0]();
  assert.equal(counter.data.seconds, 4, 'timer delay must not create countdown drift');
  counter.lifecycle('pageLifetimes', 'hide'); assert.equal(timers.size, 0);
  now += 5000; counter.lifecycle('pageLifetimes', 'show');
  assert.equal(counter.events.length, 1);
  counter.syncDeadline(); assert.equal(counter.events.length, 1);
  counter.lifecycle('lifetimes', 'detached'); assert.equal(timers.size, 0);
});

test('staff actions immediately lock repeat taps and keep task source data out of the view', async () => {
  const mutation = deferred(); let posts = 0;
  const rt = createRuntime(appData(), { showToast() {} }, {
    post: () => { posts++; return mutation.promise; },
    get: async url => url === '/staff/my-profile' ? { user: { realName: '张女士' }, profile: { id: '1' } } : { records: rows(0, 2) }
  });
  const page = rt.page('staff/workbench'); await page.onShow();
  assert.equal(Object.hasOwn(page.data, 'myTasks'), false);
  const first = page.acceptDispatch({ currentTarget: { dataset: { id: 'attempt' } } });
  assert.equal(page.data.actionLoading, true);
  const second = page.acceptDispatch({ currentTarget: { dataset: { id: 'attempt' } } });
  assert.equal(posts, 1);
  mutation.resolve(); await Promise.all([first, second]);
  assert.equal(page.data.actionLoading, false);
  assert.equal(rt.app.globalData.ordersVersion, 1);
});
