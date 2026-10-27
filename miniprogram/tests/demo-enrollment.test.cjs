const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
function setup({ get, post, put, env = 'develop' } = {}) {
  const app = { globalData: { envVersion: env, token: 'old', baseUrl: 'http://localhost:8080/api', role: 'RESIDENT', userInfo: { id: '2', username: 'resident01', role: 'RESIDENT', realName: '居民' } } };
  const calls = [], routes = [], toasts = [];
  const runtime = createRuntime(app, { setStorageSync() {}, removeStorageSync() {}, showToast: o => toasts.push(o.title), redirectTo: o => routes.push(o.url), switchTab: o => routes.push(o.url), navigateTo: o => routes.push(o.url) }, {
    get: get || (async url => url === '/auth/demo-capabilities' ? { enabled: true } : []),
    post: (url, data) => { calls.push({ url, data }); return post ? post(url, data) : Promise.resolve({}); },
    put: put || (async (_, user) => ({ ...app.globalData.userInfo, ...user }))
  });
  return { app, calls, routes, toasts, runtime };
}
test('release builds never request or expose the demo capability', async () => {
  let count = 0; const { runtime } = setup({ env: 'release', get: async () => { count++; return { enabled: true }; } });
  const page = runtime.page('auth/demo'); await page.onShow(); assert.equal(page.data.enabled, false); assert.equal(count, 0);
});
test('creating a demo user is single flight, starts as resident, then opens registration guide', async () => {
  const pending = deferred(); const { runtime, calls, app, routes } = setup({ post: () => pending.promise });
  const page = runtime.page('auth/demo'); await page.onShow(); page.setData({ realName: '答辩新人' });
  const creating = page.createUser(); page.createUser(); assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/auth/demo-register'); assert.match(calls[0].data.requestId, /^[a-f0-9]{32}$/);
  assert.equal(Object.hasOwn(calls[0].data, 'role'), false);
  pending.resolve({ token: 'new', userInfo: { id: '9', role: 'RESIDENT', realName: '答辩新人' } }); await creating;
  assert.equal(app.globalData.token, 'new'); assert.equal(routes.at(-1), '/pages/user/welcome');
});
test('failed demo creation retains retry key and form', async () => {
  const { runtime, calls } = setup({ post: async () => { throw Error('offline'); } }); const page = runtime.page('auth/demo'); await page.onShow();
  page.setData({ realName: '新人' }); await page.createUser(); await page.createUser();
  assert.equal(calls[0].data.requestId, calls[1].data.requestId); assert.equal(page.data.realName, '新人'); assert.equal(page.data.busy, false);
});
test('late demo response cannot replace another login session', async () => {
  const pending = deferred(); const { runtime, app, routes } = setup({ post: () => pending.promise }); const page = runtime.page('auth/demo'); await page.onShow();
  page.setData({ realName: '新人' }); const creating = page.createUser(); app.globalData.token = 'other';
  pending.resolve({ token: 'stale', userInfo: { role: 'RESIDENT' } }); await creating;
  assert.equal(app.globalData.token, 'other'); assert.equal(routes.length, 0);
});
test('staff application does not grant staff identity before administrator review', async () => {
  let current = null; const { runtime, app, calls } = setup({ get: async () => current, post: async () => { current = { status: 'PENDING', revision: 1, realName: '张三', phone: '13812345678', serviceArea: '社区', introduction: '服务经历' }; } });
  const page = runtime.page('staff/apply'); await page.onShow(); page.setData({ form: { revision: 0, realName: '张三', phone: '13812345678', serviceArea: '社区', introduction: '服务经历' } });
  await page.submit(); assert.equal(app.globalData.role, 'RESIDENT'); assert.equal(page.data.application.status, 'PENDING');
  await page.submit(); assert.equal(calls.length, 1);
});
test('rejected staff application resubmits its material revision', async () => {
  const row = { status: 'REJECTED', revision: 3, realName: '张三', phone: '13812345678', serviceArea: '社区', introduction: '经历', auditReason: '补充经历' };
  const { runtime, calls } = setup({ get: async () => row }); const page = runtime.page('staff/apply'); await page.onShow(); await page.submit();
  assert.equal(calls[0].data.revision, 3); assert.equal(calls[0].url, '/staff/applications/submit');
});
test('approved applicant refreshes the backend identity before entering workbench', async () => {
  const { runtime, app, routes, calls } = setup({ get: async () => ({ status: 'APPROVED' }), post: async () => ({ token: 'staff-new', userInfo: { id: '2', role: 'STAFF' } }) });
  const page = runtime.page('staff/apply'); await page.onShow(); await page.activate();
  assert.equal(calls[0].url, '/auth/refresh'); assert.equal(app.globalData.role, 'STAFF'); assert.equal(routes.at(-1), '/pages/staff/workbench');
});
test('welcome saves the name and directs staff applicants into the real application form', async () => {
  const { runtime, routes, app } = setup(); const page = runtime.page('user/welcome'); page.onShow(); page.setData({ realName: '张三' });
  await page.continueTo({ currentTarget: { dataset: { kind: 'staff' } } });
  assert.equal(app.globalData.userInfo.realName, '张三'); assert.equal(routes.at(-1), '/pages/staff/apply');
});
