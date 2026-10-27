const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { getAppSafe } = require('../../utils/app-state.js');
const { sessionKey } = require('../../utils/list-state.js');
const { applySession, clearSession } = require('../../utils/session.js');
function requestId() { return Array.from({ length: 32 }, () => Math.floor(Math.random() * 16).toString(16)).join(''); }
Page({
  data: { isElderMode: false, loading: true, failed: false, enabled: false, accounts: [], realName: '', busy: false },
  onShow() {
    const state = getAppSafe().globalData;
    this.setData({ isElderMode: Boolean(state.isElderMode) });
    if (!navigation.requireRole(['RESIDENT', 'STAFF'], '/pages/auth/demo')) return;
    return this.load();
  },
  onUnload() { this._disposed = true; },
  valid(key) { return !this._disposed && key === sessionKey(getAppSafe()); },
  async load() {
    const key = this._context = sessionKey(getAppSafe());
    const seq = this._loadSeq = (this._loadSeq || 0) + 1;
    this.setData({ loading: true, failed: false, enabled: false });
    if (['trial', 'release'].includes(getAppSafe().globalData.envVersion)) { this.setData({ loading: false }); return; }
    try {
      const capability = await request.get('/auth/demo-capabilities', {}, { silent: true });
      if (!this.valid(key) || seq !== this._loadSeq) return;
      if (!capability?.enabled) { this.setData({ loading: false }); return; }
      const rows = await request.get('/auth/demo-accounts');
      if (!this.valid(key) || seq !== this._loadSeq) return;
      this.setData({ loading: false, enabled: true, accounts: (Array.isArray(rows) ? rows : []).map(row => ({ ...row, roleText: row.role === 'STAFF' ? '工作人员' : row.username === 'family01' ? '家属代约' : '居民', isCurrent: row.username === getAppSafe().globalData.userInfo?.username })) });
    } catch (_) { if (this.valid(key) && seq === this._loadSeq) this.setData({ loading: false, failed: true }); }
  },
  onNameInput(e) { this.setData({ realName: e.detail.value }); },
  returnWechat() {
    if (this.data.busy) return;
    require('../../utils/media-url.js').clearPrivateMedia();
    clearSession(getAppSafe());
    navigation.toLogin('/pages/index/index');
  },
  async login(url, payload, destination) {
    if (this.data.busy || !this.data.enabled || !this.valid(this._context)) return;
    const key = this._context;
    this.setData({ busy: true });
    try {
      const result = await request.post(url, payload);
      if (!this.valid(key)) return;
      applySession(getAppSafe(), result);
      navigation.afterLogin(destination || '');
    } catch (_) {} finally { if (!this._disposed) this.setData({ busy: false }); }
  },
  createUser() {
    if (!this.data.realName.trim()) { wx.showToast({ title: '请填写演示用户姓名', icon: 'none' }); return; }
    this._requestId ||= requestId();
    return this.login('/auth/demo-register', { requestId: this._requestId, realName: this.data.realName.trim() }, '/pages/user/welcome');
  },
  switchAccount(e) {
    const row = this.data.accounts.find(item => item.username === e.currentTarget.dataset.username);
    if (row && !row.isCurrent) return this.login('/auth/mock-login?username=' + encodeURIComponent(row.username), {});
  }
});
