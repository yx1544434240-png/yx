const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');
function loadPage(name, app, wx = {}, request) { return createRuntime(app, wx, request).page(name); }

test('a home service shortcut uses switchTab and consumes its category once', () => {
  const app = { globalData: { isElderMode: false } };
  let target;
  const home = loadPage('index/index', app, { switchTab: value => { target = value.url; value.complete?.(); } });
  home.goToService({ currentTarget: { dataset: { category: 'MEAL' } } });
  assert.equal(target, '/pages/service/list');
  const list = loadPage('service/list', app);
  list.onLoad();
  list.loadServices = () => {};
  list.onShow();
  assert.equal(list.data.selectedCat, 'MEAL');
  assert.equal(app.globalData.serviceCategory, '');
  list.changeCat({ currentTarget: { dataset: { index: 2 } } });
  list.onAnimationFinish({ detail: { current: 2 } });
  list.onShow();
  assert.equal(list.data.selectedCat, 'CLEANING');
});

test('large text preference is shared by the home and profile pages', () => {
  const app = { globalData: { isElderMode: false }, toggleElderMode(value) { this.globalData.isElderMode = value; } };
  const home = loadPage('index/index', app);
  home.toggleMode();
  assert.equal(app.globalData.isElderMode, true);
  const profile = loadPage('user/profile', app);
  profile.onShow();
  assert.equal(profile.data.isElderMode, true);
  profile.onModeChange({ detail: { value: false } });
  assert.equal(app.globalData.isElderMode, false);
});

test('the home clears a previous active order when logged out', () => {
  const app = { globalData: { token: '' } };
  const home = loadPage('index/index', app);
  home.data.activeOrder = { order: { id: 'previous-user-order' } };
  home.loadActiveOrder();
  assert.equal(home.data.activeOrder, null);
});

test('login, cancel, confirm and logout open a bottom sheet', async () => {
  const dialogs = [];
  const taps = [];
  const posts = [];
  const wx = {
    showModal() { dialogs.push('modal'); },
    showActionSheet() { dialogs.push('action'); },
    showToast() {},
    vibrateShort() { taps.push('light'); }
  };
  const detail = loadPage('service/detail', { globalData: { token: '' } }, wx);
  detail.data.serviceId = '3';
  detail.goToBook();
  assert.equal(detail.data.loginSheet, true);
  assert.equal(taps.length, 1);
  detail.closeLoginSheet();
  assert.equal(detail.data.loginSheet, false);

  const runtime = createRuntime({ globalData: { token: 't' } }, wx, {
    post: async (url, body) => { posts.push({ url, body }); return {}; },
    get: async () => ({ order: { id: '9', status: 'COMPLETED' } })
  });
  const order = runtime.page('order/detail');
  order.data.orderId = '9';
  order.cancelOrder();
  assert.equal(order.data.cancelSheet, true);
  order.closeCancelSheet();
  order.confirmOrder();
  assert.equal(order.data.confirmSheet, true);
  await order.submitConfirm();
  assert.equal(order.data.confirmSheet, false);
  assert.equal(posts[0].url, '/fulfillment/confirm');
  assert.equal(posts[0].body.orderId, '9');

  const profile = loadPage('user/profile', { globalData: {} }, wx);
  profile.confirmLogout();
  assert.equal(profile.data.logoutSheet, true);
  assert.deepEqual(dialogs, []);
});

test('the service preview leaves loading state on request failure', async () => {
  const home = loadPage('index/index', { globalData: {} }, {}, { get: () => Promise.reject(new Error('offline')) });
  home.loadServiceItems();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(home.data.serviceError, true);
  assert.equal(home.data.servicesLoading, false);
});

test('booking form surfaces in-place field validation errors and clears them on input', () => {
  const page = loadPage('order/create', { globalData: { token: 't' } }, { showToast() {} });
  page.setData({ service: { id: 1, name: '助餐', price: 20 } });
  page.submitOrder();
  assert.equal(page.data.formErrors.elder, '请选择接受服务的老人');
  assert.equal(page.data.formErrors.address, '请填写详细上门服务地址');
  assert.equal(page.data.formErrors.contactName, '请填写联系人姓名');
  assert.equal(page.data.formErrors.contactPhone, '请填写联系电话');

  page.onInputAddress({ detail: { value: '阳光花园1号楼' } });
  assert.equal(page.data.formErrors.address, '');
  page.onInputContactName({ detail: { value: '张三' } });
  assert.equal(page.data.formErrors.contactName, '');
  page.onInputContactPhone({ detail: { value: '13800000000' } });
  assert.equal(page.data.formErrors.contactPhone, '');
});

test('profile opens elder list and elder list navigates to create or edit', async () => {
  const navigated = [];
  const wx = {
    navigateTo(o) { navigated.push(o.url); o.complete?.(); },
    showToast() {},
    vibrateShort() {}
  };
  const profile = loadPage('user/profile', { globalData: { token: 't' } }, wx);
  profile.goToElderList();
  assert.equal(navigated[0], '/pages/user/elder-list');

  const eldersList = [{ id: 101, name: '李大爷', age: 80, community: '阳光花园' }];
  const elderPage = loadPage('user/elder-list', { globalData: { token: 't' } }, wx, {
    get: async () => eldersList
  });
  await elderPage.loadElders();
  assert.equal(elderPage.data.elders.length, 1);
  assert.equal(elderPage.data.elders[0].name, '李大爷');

  elderPage.goToCreate();
  assert.equal(navigated[1], '/pages/user/elder-form');

  elderPage.goToEdit({ currentTarget: { dataset: { id: 101 } } });
  assert.equal(navigated[2], '/pages/user/elder-form?id=101');
});

test('accessibility touch target and workbench tab-bar bottom padding standards are satisfied', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const appWxss = fs.readFileSync(path.join(__dirname, '../app.wxss'), 'utf8');
  const workbenchWxss = fs.readFileSync(path.join(__dirname, '../pages/staff/workbench.wxss'), 'utf8');
  const workbenchWxml = fs.readFileSync(path.join(__dirname, '../pages/staff/workbench.wxml'), 'utf8');
  const profileWxml = fs.readFileSync(path.join(__dirname, '../pages/user/profile.wxml'), 'utf8');

  // W1: Workbench page has tab-bar bottom padding
  assert.equal(workbenchWxml.includes('workbench-page'), true);
  assert.equal(workbenchWxss.includes('--tab-bar-height'), true);

  // G3: Global buttons enforce minimum 88rpx accessible touch height
  assert.equal(/button\s*\{[^}]*min-height:\s*88rpx/.test(appWxss), true);
  assert.equal(/button\[size="mini"\]\s*\{[^}]*min-height:\s*88rpx/.test(appWxss), true);

  // P1: Profile does not display hardcoded fake statistics when profile data is empty
  assert.equal(profileWxml.includes("'4.8'"), false);
  assert.equal(profileWxml.includes("'31'"), false);

  // N1: Order list action button strictly enforces >=88rpx touch height without mini override
  const orderListWxss = fs.readFileSync(path.join(__dirname, '../pages/order/list.wxss'), 'utf8');
  const orderListWxml = fs.readFileSync(path.join(__dirname, '../pages/order/list.wxml'), 'utf8');
  assert.equal(/\.btn-action[^{]*\{[^}]*min-height:\s*88rpx/.test(orderListWxss), true);
  assert.equal(orderListWxml.includes('btn-action" size="mini"'), false);

  // N2: Workbench head card does not display fabricated '5.0' rating
  assert.equal(workbenchWxml.includes("'5.0'"), false);

  // G4: Critical small font size cleanup (no 22rpx font size in profile or workbench)
  const profileWxss = fs.readFileSync(path.join(__dirname, '../pages/user/profile.wxss'), 'utf8');
  assert.equal(/font-size:\s*22rpx/.test(profileWxss), false);
  assert.equal(/font-size:\s*22rpx/.test(workbenchWxss), false);

  // N3: Bottom-sheet component supports button-level loading feedback
  const bottomSheetJs = fs.readFileSync(path.join(__dirname, '../components/bottom-sheet/index.js'), 'utf8');
  assert.equal(bottomSheetJs.includes('loading:'), true);
});
