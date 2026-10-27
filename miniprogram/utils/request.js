// utils/request.js
const { toLogin } = require('./navigation.js');

// 统一的错误形状：所有 reject 都是 ApiError，页面只需看 kind / message。
class ApiError extends Error {
  constructor(kind, message, extra) {
    super(message);
    this.name = 'ApiError';
    this.kind = kind; // business | auth | http | network | invalid
    Object.assign(this, extra || {});
    if (kind === 'network') this.isNetworkError = true;
    if (kind === 'invalid') this.isInvalidResponse = true;
  }
}

const FRIENDLY_NETWORK_TIP = '网络不太好，请稍后再试';

function isDevelop(app) {
  const env = app && app.globalData && app.globalData.envVersion;
  return !env || env === 'develop';
}

// 开发者工具/真机调试时给开发者看的排查提示；体验版和正式版只显示友好文案。
function networkTip(errMsg, develop) {
  if (!develop) return FRIENDLY_NETWORK_TIP;
  if (errMsg.includes('url not in domain list')) return '请在微信开发者工具勾选“不校验合法域名”';
  if (errMsg.includes('refused')) return '连接被拒绝，请确认后端8080已启动';
  if (/TIMED_OUT|timeout/i.test(errMsg)) return '连不上电脑后端，请确认同一WiFi并更新服务地址';
  return '服务器网络连接超时';
}

function expireSession(app, token, baseUrl) {
  const state = app.globalData;
  // A late response from a previous account/server must not log out the current account.
  if (state.token !== token || state.baseUrl !== baseUrl) return;
  require('./media-url.js').clearPrivateMedia();
  require('./session.js').clearSession(app);
  wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' });
  toLogin();
}

function request(options) {
  return new Promise((resolve, reject) => {
    const app = getApp();
    const token = app?.globalData?.token || '';
    const baseUrl = (app && app.globalData && app.globalData.baseUrl) || wx.getStorageSync('customBaseUrl') || 'http://localhost:8080/api';
    const isAuthUrl = ['/auth/login', '/auth/wechat-login', '/auth/mock-login'].includes(options.url.split('?')[0]);
    // silent: 轮询、角标等后台请求失败时不打扰用户（登录过期仍会提示并跳转）。
    const toast = (title, duration) => { if (!options.silent) wx.showToast({ title, icon: 'none', duration: duration || 1500 }); };
    const fail = (kind, message, extra) => reject(new ApiError(kind, message, extra));
    const unauthorized = (message, extra) => {
      if (token && !isAuthUrl) expireSession(app, token, baseUrl);
      else toast(message || (isAuthUrl ? '请检查账号和密码' : '请先登录'));
      fail('auth', message || '请先登录', extra);
    };

    wx.request({
      url: baseUrl + options.url,
      method: options.method || 'GET',
      timeout: 15000,
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': token ? 'Bearer ' + token : '',
        ...options.header
      },
      success(res) {
        if (res.statusCode === 200) {
          const apiRes = res.data;
          if (!apiRes || typeof apiRes !== 'object' || typeof apiRes.code !== 'number') {
            toast('服务器响应格式异常，请稍后重试');
            fail('invalid', '服务器响应格式异常', { statusCode: 200 });
          } else if (apiRes.code === 200) {
            resolve(apiRes.data);
          } else if (apiRes.code === 401) {
            unauthorized(apiRes.message, { code: 401, statusCode: 200, data: apiRes.data });
          } else {
            const message = apiRes.message || '服务异常';
            toast(message);
            fail('business', message, { code: apiRes.code, statusCode: 200, data: apiRes.data });
          }
        } else if (res.statusCode === 401) {
          unauthorized(res.data && res.data.message, { code: 401, statusCode: 401 });
        } else {
          const message = (res.data && res.data.message) || '服务暂时不可用，请稍后再试';
          toast(isDevelop(app) ? '网络连接失败(' + res.statusCode + ')' : message);
          fail('http', message, { code: res.data && res.data.code, statusCode: res.statusCode });
        }
      },
      fail(err) {
        console.error('[API请求失败]', options.method || 'GET', baseUrl + options.url, err);
        const errMsg = (err && err.errMsg) || '';
        const tip = networkTip(errMsg, isDevelop(app));
        toast(tip, 3000);
        fail('network', tip, { errMsg });
      }
    });
  });
}

// 第三个参数为可选项：{ header, silent }
const call = method => (url, data, opts) => request({ url, method, data, header: opts && opts.header, silent: !!(opts && opts.silent) });

module.exports = {
  ApiError,
  request,
  networkTip,
  get: call('GET'),
  post: call('POST'),
  put: call('PUT'),
  del: call('DELETE')
};
