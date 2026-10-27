const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRuntime } = require('./page-harness.cjs');
const read = f => fs.readFileSync(path.join(__dirname, '..', f), 'utf8');
const row = id => ({ order: { id, orderNo: 'N' + id, status: 'WAIT_VISIT', plannedStartAt: '2026-10-22 09:00:00' } });

test('home shows "N more" in-progress orders and an error instead of the welcome view on failure', async () => {
  const app = { globalData: { token: 't' } };
  let fail = false;
  const home = createRuntime(app, {}, { get: async () => { if (fail) throw Error('offline'); return { records: [row(1)], total: 3 }; } }).page('index/index');
  await home.loadActiveOrder(true);
  assert.equal(home.data.activeMoreCount, 2);
  assert.equal(home.data.activeOrderError, false);
  const fresh = createRuntime(app, {}, { get: async () => { throw Error('offline'); } }).page('index/index');
  await fresh.loadActiveOrder(true);
  assert.equal(fresh.data.activeOrderReady, true);
  assert.equal(fresh.data.activeOrderError, true);
  assert.match(read('pages/index/index.wxml'), /wx:if="\{\{activeOrderError\}\}"[^>]*><state-view type="error"/);
});

test('staff opening a resident tab page is sent back to the workbench', () => {
  for (const name of ['index/index', 'service/list', 'order/list']) {
    const routes = [];
    const app = { globalData: { token: 't', role: 'STAFF' } };
    const page = createRuntime(app, { switchTab: o => { routes.push(o.url); o.complete?.(); } }).page(name);
    page.onShow();
    assert.deepEqual(routes, ['/pages/staff/workbench'], name);
  }
  const routes = [];
  const resident = createRuntime({ globalData: { token: 't', role: 'RESIDENT' } }, { switchTab: o => routes.push(o.url) }).require('utils/navigation.js');
  assert.equal(resident.redirectStaffHome(), false);
  assert.deepEqual(routes, []);
});

test('the whole elder-mode row toggles the setting', () => {
  const app = { globalData: { isElderMode: false }, toggleElderMode(v) { this.globalData.isElderMode = v; } };
  const profile = createRuntime(app).page('user/profile');
  profile.toggleElderRow();
  assert.equal(app.globalData.isElderMode, true);
  profile.toggleElderRow();
  assert.equal(app.globalData.isElderMode, false);
  assert.match(read('pages/user/profile.wxml'), /class="menu-item"[^>]*bindtap="toggleElderRow"/);
});

test('record sheet ignores mask taps but the cancel button still closes', () => {
  const sheet = createRuntime().component('bottom-sheet', { maskClosable: false });
  sheet.data.maskClosable = false;
  sheet.onMask();
  assert.deepEqual(sheet.events, []);
  sheet.onDismiss();
  assert.deepEqual(sheet.events, ['close']);
  assert.match(read('pages/staff/workbench.wxml'), /title="提交上门服务记录"[^>]*mask-closable="\{\{false\}\}"/);
});

test('a swipe-close attempt snaps the sheet back so a "keep editing" answer leaves it usable', () => {
  const sheet = createRuntime({ globalData: {} }, {}, undefined, {}).component('bottom-sheet', {});
  sheet._startY = 0; sheet._startAt = Date.now(); sheet.data.drag = 400;
  sheet.onEnd();
  assert.equal(sheet.data.drag, 0);
  assert.deepEqual(sheet.events, ['close']);
});

test('profile order hub shows per-status counts', async () => {
  const app = { globalData: { token: 't', userInfo: { role: 'RESIDENT' } } };
  const seen = [];
  const profile = createRuntime(app, {}, { get: async (url, q) => { seen.push(q.status); return { total: q.status === 'WAIT_VISIT' ? 2 : 0 }; } }).page('user/profile');
  await profile.loadHubCounts();
  assert.deepEqual(seen, ['WAIT_DISPATCH,WAIT_ACCEPT,WAIT_MANUAL', 'WAIT_VISIT', 'IN_SERVICE', 'WAIT_CONFIRM']);
  assert.equal(profile.data.hubCounts.WAIT_VISIT, 2);
  assert.equal(profile.data.hubCounts.IN_SERVICE, 0);
});

test('review page gets service context from the authorized order instead of URL text', async () => {
  const page = createRuntime({ globalData: { token: 't' } }, {}, { get: async () => ({ order: { status: 'COMPLETED', serviceNameSnapshot: '助餐送餐' }, currentStaff: { user: { realName: '张玉兰' } }, serviceRecord: { actualEndAt: '2020-01-02 10:00:00' } }) }).page('order/confirm');
  page.onLoad({ orderId: '5', service: encodeURIComponent('助餐送餐'), staff: encodeURIComponent('张玉兰'), done: encodeURIComponent('2020-01-02 10:00:00') });
  await page.onShow();
  assert.equal(page.data.reviewContext.service, '助餐送餐');
  assert.equal(page.data.reviewContext.staff, '张玉兰');
  assert.match(page.data.reviewContext.doneText, /10:00$/);
  const routes = [];
  const detail = createRuntime({ globalData: {} }, { navigateTo: o => routes.push(o.url) }).page('order/detail');
  detail.data.orderId = 5;
  detail.data.orderVO = { order: { serviceNameSnapshot: '助餐送餐' }, currentStaff: { user: { realName: '张玉兰' } } };
  detail.goToReview();
  assert.equal(routes[0], `/pages/order/confirm?orderId=5&service=${encodeURIComponent('助餐送餐')}&staff=${encodeURIComponent('张玉兰')}`);
});

test('care level uses backend values and can be chosen in the form', () => {
  const form = createRuntime().page('user/elder-form');
  assert.deepEqual(form.data.careLevelOptions.map(o => o.value), ['NORMAL', 'MEDIUM', 'HIGH']);
  form.onCareLevelTap({ currentTarget: { dataset: { value: 'MEDIUM' } } });
  assert.equal(form.data.careLevel, 'MEDIUM');
  const list = createRuntime().page('user/elder-list');
  assert.equal(list.data.careLevelMap.MEDIUM, '半自理');
  assert.doesNotMatch(read('pages/user/elder-list.wxml'), /自理长辈/);
});

test('layout fixes: tabs ≥88rpx, scalable category font, bigger targets, elder bottom padding', () => {
  for (const [f, sel] of [['pages/service/list.wxss', 'cat-nav-item'], ['pages/order/list.wxss', 'tab-nav-item'], ['pages/staff/workbench.wxss', 'workbench-tab-item']]) {
    const block = read(f).match(new RegExp(`\\.${sel} \\{[^}]*\\}`))[0];
    assert.match(block, /min-height: 88rpx/, f);
  }
  assert.match(read('pages/service/list.wxss').match(/\.cat-nav-item \{[^}]*\}/)[0], /font-size: var\(--font-caption\)/);
  assert.match(read('pages/user/elder-form.wxss').match(/\.map-picker-btn \{[^}]*\}/)[0], /min-height: 88rpx/);
  assert.match(read('pages/staff/workbench.wxss').match(/\.photo-delete-btn \{[^}]*\}/)[0], /border: 22rpx solid transparent/);
  for (const f of ['pages/service/detail.wxss', 'pages/order/detail.wxss', 'pages/order/create.wxss']) {
    assert.match(read(f), /\.container\.elder-mode \{ padding-bottom: calc\((2[89]|3\d)0rpx/, f);
  }
});

test('order card puts the appointment time before other details; detail merges status and next step', () => {
  const card = read('pages/order/list.wxml');
  assert.ok(card.indexOf('order-time') < card.indexOf('服务对象'));
  assert.doesNotMatch(card, /订单号：/);
  const detail = read('pages/order/detail.wxml');
  assert.doesNotMatch(detail, /next-step-card|接下来怎么办？/);
  assert.match(detail, /status-banner[\s\S]*next-step-body/);
  assert.match(detail, /btn-cancel spring \{\{[^}]*is-solo/);
});

test('load failures use state-view on remaining pages; login agreement sits above the button', () => {
  for (const f of ['order/detail', 'service/detail', 'user/elder-list', 'order/create', 'user/family']) {
    assert.match(read(`pages/${f}.wxml`), /<state-view type="error"/, f);
    assert.ok(JSON.parse(read(`pages/${f}.json`)).usingComponents['state-view'], f);
  }
  const login = read('pages/auth/login.wxml');
  assert.ok(login.indexOf('agreement-section') < login.indexOf('btn-quick-login'));
});
