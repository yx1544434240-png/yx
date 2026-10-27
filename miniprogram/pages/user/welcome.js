const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { getAppSafe } = require('../../utils/app-state.js');
const { sessionKey } = require('../../utils/list-state.js');
Page({
  data: { realName: '', saving: false, isElderMode: false },
  onShow() {
    if (!navigation.requireRole(['RESIDENT'], '/pages/user/welcome')) return;
    this._context = sessionKey(getAppSafe());
    this.setData({ realName: getAppSafe().globalData.userInfo?.realName || '', isElderMode: Boolean(getAppSafe().globalData.isElderMode) });
  },
  onUnload() { this._disposed = true; },
  onNameInput(e) { this.setData({ realName: e.detail.value }); },
  async continueTo(e) {
    if (this.data.saving) return;
    const destination = { elder: '/pages/user/elder-form', family: '/pages/user/family', staff: '/pages/staff/apply', home: '/pages/index/index' }[e.currentTarget.dataset.kind];
    if (!destination) return;
    if (!this.data.realName.trim()) { wx.showToast({ title: '请填写您的姓名', icon: 'none' }); return; }
    const key = this._context;
    if (key !== sessionKey(getAppSafe())) return;
    this.setData({ saving: true });
    try {
      const userInfo = await request.put('/users/profile', { realName: this.data.realName.trim() });
      if (this._disposed || key !== sessionKey(getAppSafe())) return;
      getAppSafe().globalData.userInfo = userInfo;
      wx.setStorageSync('userInfo', userInfo);
      navigation.afterLogin(destination);
    } catch (_) {} finally { if (!this._disposed) this.setData({ saving: false }); }
  }
});
