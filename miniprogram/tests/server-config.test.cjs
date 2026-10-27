const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

const LOCAL_LAN = 'http://192.168.31.20:8080/api';
function currentLanUrl() {
  return launch('ios').app.globalData.baseUrl;
}

function launch(platform, saved = {}, legacy = false, envVersion = 'develop', local = { LAN_BASE_URL: LOCAL_LAN }) {
  const app = {};
  const storage = { ...saved };
  const wx = {
    getStorageSync: key => storage[key],
    setStorageSync: (key, value) => { storage[key] = value; },
    getAccountInfoSync: () => ({ miniProgram: { envVersion } }),
    [legacy ? 'getSystemInfoSync' : 'getDeviceInfo']: () => ({ platform })
  };
  const rt = createRuntime(app, wx, undefined, {
    App: value => Object.assign(app, value, { globalData: structuredClone(value.globalData) }),
    __stubModules: { 'config/local.js': local || {} }
  });
  rt.require('app'); app.onLaunch();
  return { app, storage, rt };
}

test('App registers and starts even before common modules are injected', () => {
  const app = {};
  const rt = createRuntime(app, {
    getStorageSync() {}, getDeviceInfo: () => ({ platform: 'ios' })
  }, undefined, {
    require() { throw new Error('common modules have not been injected'); },
    App: value => Object.assign(app, value, { globalData: structuredClone(value.globalData) })
  });
  rt.require('app');
  assert.equal(typeof app.onLaunch, 'function', 'App must register without requiring a new module');
  app.onLaunch();
  // 本机配置模块不可加载时仍能启动，并退回模拟器地址
  assert.equal(app.globalData.baseUrl, 'http://localhost:8080/api');
  assert.match(currentLanUrl(), /^http:\/\/\d+\.\d+\.\d+\.\d+:\d+\/api$/);
});

test('phone startup uses the computer LAN endpoint while the simulator uses localhost', () => {
  const lan = currentLanUrl();
  for (const platform of ['ios', 'android']) {
    const { app } = launch(platform);
    assert.equal(app.globalData.baseUrl, lan);
    assert.equal(app.globalData.platform, platform);
  }
  assert.equal(launch('devtools').app.globalData.baseUrl, 'http://localhost:8080/api');
});

test('phone startup migrates previously cached loopback addresses', () => {
  const lan = currentLanUrl();
  for (const url of ['http://localhost:8080/api', 'http://127.0.0.1:8080/api', 'http://[::1]:8080/api']) {
    const { app, storage } = launch('ios', { customBaseUrl: url });
    assert.equal(app.globalData.baseUrl, lan);
    assert.equal(storage.customBaseUrl, app.globalData.baseUrl);
  }
});

test('phone startup migrates any previously saved private LAN address after the computer changes networks', () => {
  const lan = currentLanUrl();
  for (const stale of ['http://10.0.151.146:8080/api', 'http://192.168.1.8:8080/api', 'http://172.16.0.9:8080/api']) {
    const { app, storage } = launch('ios', { customBaseUrl: stale });
    assert.equal(app.globalData.baseUrl, lan);
    assert.equal(storage.customBaseUrl, lan);
  }
});

test('explicit remote endpoints and restored login preferences remain intact', () => {
  const saved = { customBaseUrl: 'https://care.example.com/api', token: 'signed-in', userInfo: { role: 'STAFF' }, isElderMode: true };
  const { app, storage } = launch('android', saved);
  assert.equal(app.globalData.baseUrl, saved.customBaseUrl);
  assert.equal(storage.customBaseUrl, saved.customBaseUrl);
  assert.equal(app.globalData.token, 'signed-in');
  assert.equal(app.globalData.role, 'STAFF');
  assert.equal(app.globalData.isElderMode, true);
});

test('legacy device detection still chooses the phone endpoint', () => {
  assert.equal(launch('android', {}, true).app.globalData.baseUrl, currentLanUrl());
});

test('the phone server menu does not offer local loopback addresses', () => {
  let options;
  const { app } = launch('ios');
  const page = createRuntime(app, { showActionSheet: value => { options = value; } }).page('user/profile');
  page.changeServerUrl();
  assert.equal(options.itemList.length, 2);
  assert.equal(options.itemList.some(item => item.includes('localhost') || item.includes('127.0.0.1')), false);
});

test('a blocked network request does not open a misleading WeChat authorization fallback', async () => {
  const requests = []; let modals = 0;
  const app = { globalData: { baseUrl: currentLanUrl(), token: '' } };
  const wx = {
    login: options => options.success({ code: 'test-code' }),
    request: options => requests.push(options), showLoading() {}, hideLoading() {}, showToast() {},
    showModal() { modals++; }
  };
  const rt = createRuntime(app, wx, null, { console: { error() {} } });
  const page = rt.page('auth/login'); page.data.isAgreed = true;
  page.handleQuickLogin();
  await new Promise(resolve => setImmediate(resolve));
  requests[0].fail({ errMsg: 'request:fail url not in domain list', errno: 600002 });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(modals, 0);
});

test('a connection timeout tells the user to check WiFi and the LAN address', async () => {
  const requests = []; const toasts = [];
  const app = { globalData: { baseUrl: currentLanUrl(), token: '' } };
  const wx = {
    login: options => options.success({ code: 'test-code' }),
    request: options => requests.push(options), showLoading() {}, hideLoading() {},
    showToast: options => toasts.push(options), showModal() {}
  };
  const rt = createRuntime(app, wx, null, { console: { error() {} } });
  const page = rt.page('auth/login'); page.data.isAgreed = true;
  page.handleQuickLogin();
  await new Promise(resolve => setImmediate(resolve));
  requests[0].fail({ errMsg: 'request:fail -118:net::ERR_CONNECTION_TIMED_OUT', errno: 600001 });
  await new Promise(resolve => setImmediate(resolve));
  assert.match(toasts.at(-1).title, /同一WiFi|服务地址/);
});

test('trial and release always use the production HTTPS endpoint and never a LAN address', () => {
  for (const env of ['trial', 'release']) {
    for (const platform of ['ios', 'devtools']) {
      const { app, storage } = launch(platform, { customBaseUrl: 'http://192.168.1.8:8080/api' }, false, env);
      assert.match(app.globalData.baseUrl, /^https:\/\//, env);
      assert.deepEqual(Array.from(app.getServerUrls()), [app.globalData.baseUrl]);
      assert.equal(storage.customBaseUrl, 'http://192.168.1.8:8080/api', 'production does not rewrite dev cache');
    }
  }
});

test('committed app.js contains no private LAN address; develop without local config falls back safely', () => {
  const source = require('node:fs').readFileSync(require('node:path').join(__dirname, '..', 'app.js'), 'utf8');
  assert.doesNotMatch(source, /https?:\/\/(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/);
  const { app } = launch('ios', {}, false, 'develop', null);
  assert.equal(app.globalData.baseUrl, 'http://localhost:8080/api');
  assert.deepEqual(Array.from(app.getServerUrls()), []);
});

test('logged-out role is empty, not RESIDENT', () => {
  assert.equal(launch('devtools').app.globalData.role, '');
});
