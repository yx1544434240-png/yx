// 登录会话的唯一读写入口：未登录时角色为空字符串，不再默认成 RESIDENT。
function getRole(app) {
  const state = (app && app.globalData) || {};
  return state.role || (state.userInfo && state.userInfo.role) || '';
}

function isLoggedIn(app) {
  return Boolean(app && app.globalData && app.globalData.token);
}

function clearSession(app) {
  if (app && app.globalData) {
    app.globalData.token = '';
    app.globalData.userInfo = null;
    app.globalData.role = '';
  }
  if (typeof wx !== 'undefined' && wx.removeStorageSync) {
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
  }
}

function applySession(app, data) {
  if (!app?.globalData || !data?.token || !data.userInfo || !['RESIDENT', 'STAFF'].includes(data.userInfo.role)) throw new Error('登录身份无效');
  require('./media-url.js').clearPrivateMedia();
  app.globalData.token = data.token;
  app.globalData.userInfo = data.userInfo;
  app.globalData.role = data.userInfo.role;
  app.globalData.pendingTabQuery = null;
  app.globalData.ordersVersion = (app.globalData.ordersVersion || 0) + 1;
  wx.setStorageSync('token', data.token);
  wx.setStorageSync('userInfo', data.userInfo);
}

module.exports = { getRole, isLoggedIn, clearSession, applySession };
