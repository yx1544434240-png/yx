const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

function setup(response, envVersion = 'develop', state = {}) {
  const toasts = [];
  const logins = [];
  const app = { globalData: { baseUrl: 'http://api', token: '', role: 'RESIDENT', envVersion, ...state } };
  const wx = {
    request(opts) { response.fail ? opts.fail(response.fail) : opts.success(response); },
    showToast(o) { toasts.push(o.title); },
    getStorageSync() { return ''; }, removeStorageSync() {}
  };
  const rt = createRuntime(app, wx, null, {
    __stubModules: {
      'utils/navigation.js': { toLogin: () => logins.push(1) },
      'utils/media-url.js': { clearPrivateMedia() {} }
    }
  });
  rt.toasts = toasts; rt.logins = logins;
  rt.api = rt.require('utils/request.js');
  return rt;
}

test('success resolves data', async () => {
  const rt = setup({ statusCode: 200, data: { code: 200, data: { ok: 1 } } });
  assert.deepEqual(JSON.parse(JSON.stringify(await rt.api.get('/x'))), { ok: 1 });
});

test('business error rejects ApiError and toasts unless silent', async () => {
  const rt = setup({ statusCode: 200, data: { code: 409, message: '订单已支付' } });
  const err = await rt.api.post('/x').catch(e => e);
  assert.equal(err.name, 'ApiError');
  assert.equal(err.kind, 'business');
  assert.equal(err.code, 409);
  assert.equal(err.message, '订单已支付');
  assert.deepEqual(rt.toasts, ['订单已支付']);
  await rt.api.get('/x', null, { silent: true }).catch(() => {});
  assert.equal(rt.toasts.length, 1);
});

test('network error shows developer tip only in develop', async () => {
  const fail = { errMsg: 'request:fail url not in domain list' };
  const dev = setup({ fail });
  const e1 = await dev.api.get('/x').catch(e => e);
  assert.equal(e1.kind, 'network');
  assert.ok(e1.isNetworkError);
  assert.match(dev.toasts[0], /不校验合法域名/);
  const prod = setup({ fail }, 'release');
  const e2 = await prod.api.get('/x').catch(e => e);
  assert.equal(prod.toasts[0], '网络不太好，请稍后再试');
  assert.equal(e2.message, '网络不太好，请稍后再试');
});

test('http error and invalid response share the same shape', async () => {
  const rt = setup({ statusCode: 500, data: {} }, 'release');
  const e = await rt.api.get('/x').catch(e => e);
  assert.equal(e.kind, 'http');
  assert.equal(e.statusCode, 500);
  assert.ok(!/500/.test(rt.toasts[0]));
  const bad = setup({ statusCode: 200, data: 'oops' });
  const e2 = await bad.api.get('/x').catch(e => e);
  assert.equal(e2.kind, 'invalid');
});

test('401 with token expires session, clears role and goes to login even when silent', async () => {
  const rt = setup({ statusCode: 401, data: {} }, 'develop', { token: 't' });
  const e = await rt.api.get('/orders/1/tracking', null, { silent: true }).catch(e => e);
  assert.equal(e.kind, 'auth');
  assert.equal(rt.app.globalData.token, '');
  assert.equal(rt.app.globalData.role, '');
  assert.equal(rt.logins.length, 1);
});
