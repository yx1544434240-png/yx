const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

const appData = (role = 'RESIDENT') => ({
  globalData: {
    token: 'test-token',
    role,
    userInfo: { role, username: 'testuser' },
    baseUrl: 'http://localhost:8080/api',
    isElderMode: false
  }
});

test('STAFF login routes directly to staff workbench via switchTab', () => {
  const app = { globalData: { token: '', baseUrl: 'http://localhost:8080/api' } };
  const calls = [];
  const rt = createRuntime(app, {
    setStorageSync() {},
    showToast() {},
    switchTab: o => calls.push(o)
  });
  const page = rt.page('auth/login');
  page.onLoad({});
  page.onLoginSuccess({
    token: 'staff-token',
    userInfo: { role: 'STAFF', username: 'staff01', realName: '张玉兰' }
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/pages/staff/workbench');
  assert.equal(app.globalData.role, 'STAFF');
});

test('RESIDENT login routes to resident home page via switchTab', () => {
  const app = { globalData: { token: '', baseUrl: 'http://localhost:8080/api' } };
  const calls = [];
  const rt = createRuntime(app, {
    setStorageSync() {},
    showToast() {},
    switchTab: o => calls.push(o)
  });
  const page = rt.page('auth/login');
  page.onLoad({});
  page.onLoginSuccess({
    token: 'resident-token',
    userInfo: { role: 'RESIDENT', username: 'resident01', realName: '赵大爷' }
  });

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/pages/index/index');
  assert.equal(app.globalData.role, 'RESIDENT');
});

test('ADMIN login in mini-program is rejected with prompt to use PC management web', () => {
  const app = { globalData: { token: '', baseUrl: 'http://localhost:8080/api' } };
  const toasts = [];
  const calls = [];
  const rt = createRuntime(app, {
    setStorageSync() {},
    showToast: o => toasts.push(o),
    switchTab: o => calls.push(o),
    redirectTo: o => calls.push(o)
  });
  const page = rt.page('auth/login');
  page.onLoad({});
  page.onLoginSuccess({
    token: 'admin-token',
    userInfo: { role: 'ADMIN', username: 'admin', realName: '管理员' }
  });

  assert.equal(calls.length, 0, 'ADMIN should not navigate into mini-program');
  assert.equal(toasts.length, 1);
  assert.match(toasts[0].title, /管理员请前往电脑端管理平台/);
  assert.equal(app.globalData.token, '');
});

test('RESIDENT attempting to access staff workbench is blocked and routed to home', () => {
  const app = appData('RESIDENT');
  const calls = [];
  const toasts = [];
  const rt = createRuntime(app, {
    showToast: o => toasts.push(o),
    switchTab: o => calls.push(o)
  });
  const nav = rt.require('utils/navigation');
  const allowed = nav.requireRole(['STAFF'], '/pages/staff/workbench');

  assert.equal(allowed, false);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/pages/index/index');
  assert.equal(toasts.length, 1);
  assert.equal(toasts[0].title, '无权访问该角色页面');
});

test('STAFF attempting to access resident booking page is blocked and routed to workbench', () => {
  const app = appData('STAFF');
  const calls = [];
  const toasts = [];
  const rt = createRuntime(app, {
    showToast: o => toasts.push(o),
    switchTab: o => calls.push(o)
  });
  const nav = rt.require('utils/navigation');
  const allowed = nav.requireRole(['RESIDENT'], '/pages/order/create');

  assert.equal(allowed, false);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/pages/staff/workbench');
  assert.equal(toasts.length, 1);
  assert.equal(toasts[0].title, '无权访问该角色页面');
});

test('custom-tab-bar dynamically switches between STAFF and RESIDENT tab menus', () => {
  // 1. RESIDENT sees 4 tabs
  const residentApp = appData('RESIDENT');
  const residentRt = createRuntime(residentApp, {}, {}, {
    getCurrentPages: () => [{ route: 'pages/index/index' }]
  });
  const residentBar = residentRt.component('../custom-tab-bar', {});
  residentBar.syncFromRoute();
  assert.equal(residentBar.data.list.length, 4);
  assert.equal(residentBar.data.list[0].text, '首页');
  assert.equal(residentBar.data.list[3].text, '我的');
  assert.equal(residentBar.data.selected, 0);

  // 2. STAFF sees 2 tabs (工作台 and 我的)
  const staffApp = appData('STAFF');
  const staffRt = createRuntime(staffApp, {}, {}, {
    getCurrentPages: () => [{ route: 'pages/staff/workbench' }]
  });
  const staffBar = staffRt.component('../custom-tab-bar', {});
  staffBar.syncFromRoute();
  assert.equal(staffBar.data.list.length, 2);
  assert.equal(staffBar.data.list[0].text, '工作台');
  assert.equal(staffBar.data.list[1].text, '我的');
  assert.equal(staffBar.data.selected, 0);
});

test('STAFF profile page loads staff stats and switches to workbench', async () => {
  const staffApp = appData('STAFF');
  staffApp.globalData.userInfo = { role: 'STAFF', username: 'staff01', realName: '张玉兰' };
  const calls = [];
  const rt = createRuntime(staffApp, {
    switchTab: o => calls.push(o)
  }, {
    get(url) {
      if (url === '/staff/my-profile') {
        return Promise.resolve({
          profile: { id: 1, priorRating: 4.95, totalOrders: 42, baseStationName: '阳光助老站', currentStatus: 'AVAILABLE' },
          skills: [{ skillName: '生活照料', certificateNo: 'CERT-001', auditStatus: 'APPROVED' }, { skillName: '待审技能', auditStatus: 'PENDING' }]
        });
      }
      return Promise.resolve({});
    }
  });

  const page = rt.page('user/profile');
  page.onShow();
  await page.loadStaffProfile();

  assert.equal(page.data.staffProfile.priorRating, 4.95);
  assert.equal(page.data.staffProfile.totalOrders, 42);
  assert.equal(page.data.staffSkills.length, 1);
  assert.equal(page.data.staffSkillsDesc, '生活照料');

  page.goToWorkbench();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/pages/staff/workbench');
});
