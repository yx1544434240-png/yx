const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('afterLogin keeps tab query for the tab page to take', () => {
  const tabs = [];
  const app = { globalData: { token: 't', role: 'RESIDENT' } };
  const rt = createRuntime(app, { switchTab: o => tabs.push(o) });
  const nav = rt.require('utils/navigation');
  nav.afterLogin('/pages/order/list?status=WAIT_CONFIRM');
  assert.equal(tabs[0].url, '/pages/order/list');
  assert.equal(nav.takeTabQuery('/pages/service/list'), null);
  assert.equal(nav.takeTabQuery('/pages/order/list').status, 'WAIT_CONFIRM');
  assert.equal(nav.takeTabQuery('/pages/order/list'), null, 'query is consumed once');
});

test('ADMIN login is signed out instead of stuck on the login page', () => {
  const removed = [], modals = [];
  const app = { globalData: { token: 't', role: 'ADMIN', userInfo: { role: 'ADMIN' } } };
  const rt = createRuntime(app, { removeStorageSync: k => removed.push(k), showModal: o => modals.push(o) });
  assert.equal(rt.require('utils/navigation').afterLogin('/pages/index/index'), false);
  assert.equal(app.globalData.token, '');
  assert.equal(app.globalData.role, '');
  assert.equal(modals.length, 1);
  assert.ok(removed.includes('token'));
});

test('logged-out role is empty and is not treated as RESIDENT', () => {
  const rt = createRuntime({ globalData: {} });
  const session = rt.require('utils/session');
  assert.equal(session.getRole({ globalData: {} }), '');
  assert.equal(session.getRole({ globalData: { userInfo: { role: 'STAFF' } } }), 'STAFF');
});

for (const [page, options, expected] of [
  ['order/create', { serviceId: '8' }, '/pages/order/create?serviceId=8'],
  ['user/elder-form', { id: '5' }, '/pages/user/elder-form?id=5']
]) {
  test(`${page} guards in onLoad before any request and keeps the return url`, () => {
    const routes = []; let requests = 0;
    const app = { globalData: { token: '' } };
    const rt = createRuntime(app, { redirectTo: o => routes.push(o), navigateTo: o => routes.push(o), setNavigationBarTitle() {} },
      { get: () => { requests++; return Promise.resolve([]); } },
      { getCurrentPages: () => [{ route: 'pages/' + page, options }] });
    const p = rt.page(page);
    p.onLoad(options); p.onShow();
    assert.equal(requests, 0);
    assert.equal(routes.length, 1);
    assert.equal(decodeURIComponent(routes[0].url.split('redirect=')[1]), expected);
  });
}

for (const page of ['order/detail', 'service/detail']) {
  test(`${page} without id offers a way home`, () => {
    const tabs = [];
    const rt = createRuntime({ globalData: { token: 't', role: 'RESIDENT' } }, { switchTab: o => tabs.push(o) });
    const p = rt.page(page);
    p.onLoad({});
    assert.equal(p.data.missingId, true);
    assert.equal(p.data.loading, false);
    p.goHome();
    assert.equal(tabs[0].url, '/pages/index/index');
  });
}
