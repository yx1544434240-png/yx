// app.js
// 启动配置放在入口中，避免真机增量编译遗漏新增公共模块时中断 App 注册。
//
// 环境与服务地址（详见 docs/09-真机调试网络说明.md）：
// - 体验版 / 正式版（envVersion = trial / release）：固定使用 PROD_BASE_URL，忽略本地缓存的自定义地址，
//   也不读取任何局域网地址。
// - 开发版（develop，开发者工具与真机调试）：模拟器默认 localhost；手机默认读取未提交的
//   config/local.js 里的 LAN_BASE_URL（由 `node scripts/sync-lan-ip.cjs` 生成，已加入 .gitignore）。
const SIMULATOR_BASE_URL = 'http://localhost:8080/api';
// 正式环境 HTTPS 入口：上线前替换为已在小程序后台配置为 request 合法域名的地址。
const PROD_BASE_URL = 'https://care.example.com/api';

function loadLocalConfig() {
  try { return require('./config/local.js') || {}; } catch (_) { return {}; }
}

function envVersion() {
  try {
    const info = typeof wx.getAccountInfoSync === 'function' ? wx.getAccountInfoSync() : null;
    return (info && info.miniProgram && info.miniProgram.envVersion) || 'develop';
  } catch (_) { return 'develop'; }
}

function isLocalDevUrl(url) {
  const ipv6 = /^https?:\/\/\[([^\]]+)\](?=[:/]|$)/i.exec(url);
  if (ipv6) return ipv6[1] === '::1';
  const match = /^https?:\/\/([^/:]+)(?=[:/]|$)/i.exec(url);
  if (!match) return false;
  const host = match[1];
  if (/^localhost$/i.test(host)) return true;
  const parts = host.split('.').map(Number);
  if (parts.length !== 4 || parts.some(part => !Number.isInteger(part) || part < 0 || part > 255)) return false;
  if (parts[0] === 127) return true;
  if (parts[0] === 10) return true;
  if (parts[0] === 192 && parts[1] === 168) return true;
  return parts[0] === 172 && parts[1] >= 16 && parts[1] <= 31;
}

function resolveBaseUrl(customBaseUrl, onDevice, lanBaseUrl) {
  const custom = typeof customBaseUrl === 'string' ? customBaseUrl.trim().replace(/\/+$/, '') : '';
  const deviceDefault = lanBaseUrl || SIMULATOR_BASE_URL;
  if (!custom || (onDevice && isLocalDevUrl(custom))) return onDevice ? deviceDefault : SIMULATOR_BASE_URL;
  return custom;
}

App({
  globalData: {
    baseUrl: SIMULATOR_BASE_URL,
    platform: 'devtools',
    envVersion: 'develop',
    userInfo: null,
    token: '',
    role: '', // 未登录为空；登录后为 RESIDENT / STAFF / ADMIN
    isElderMode: false // 适老化关爱模式
  },

  onLaunch() {
    // 恢复本地存储的登录态与自定义服务器地址
    const token = wx.getStorageSync('token');
    const userInfo = wx.getStorageSync('userInfo');
    const isElderMode = wx.getStorageSync('isElderMode') || false;
    const customBaseUrl = wx.getStorageSync('customBaseUrl');
    const device = typeof wx.getDeviceInfo === 'function' ? wx.getDeviceInfo() : wx.getSystemInfoSync();
    this.globalData.platform = device.platform || 'devtools';
    this.globalData.envVersion = envVersion();
    this._lanBaseUrl = this.isProduction() ? '' : (loadLocalConfig().LAN_BASE_URL || '');
    this.globalData.baseUrl = this.resolveBaseUrl(customBaseUrl);
    // 迁移手机上之前保存的 localhost，避免旧缓存继续覆盖真机地址（正式环境不写缓存）。
    if (!this.isProduction() && customBaseUrl && customBaseUrl !== this.globalData.baseUrl) wx.setStorageSync('customBaseUrl', this.globalData.baseUrl);

    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = userInfo;
      this.globalData.role = (userInfo && userInfo.role) || '';
    }
    this.globalData.isElderMode = isElderMode;
  },

  isRealDevice() {
    return Boolean(this.globalData.platform && this.globalData.platform !== 'devtools');
  },

  isProduction() {
    return this.globalData.envVersion === 'trial' || this.globalData.envVersion === 'release';
  },

  resolveBaseUrl(customBaseUrl) {
    if (this.isProduction()) return PROD_BASE_URL;
    return resolveBaseUrl(customBaseUrl, this.isRealDevice(), this._lanBaseUrl);
  },

  getServerUrls() {
    if (this.isProduction()) return [PROD_BASE_URL];
    const lan = this._lanBaseUrl ? [this._lanBaseUrl] : [];
    return this.isRealDevice() ? lan : [SIMULATOR_BASE_URL, 'http://127.0.0.1:8080/api', ...lan];
  },

  toggleElderMode(enable) {
    this.globalData.isElderMode = enable;
    wx.setStorageSync('isElderMode', enable);
  }
});
