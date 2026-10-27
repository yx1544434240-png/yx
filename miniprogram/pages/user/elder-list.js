const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { lightTap } = require('../../utils/feedback.js');
const { sessionKey } = require('../../utils/list-state.js');
const { getAppSafe } = require('../../utils/app-state.js');

const careLevelMap = {
  // 与后端 ElderSaveDTO 的 careLevel 取值一致：NORMAL | MEDIUM | HIGH
  NORMAL: '能自理',
  MEDIUM: '半自理',
  HIGH: '需全程照护'
};

Page({
  data: {
    elders: [],
    loading: true,
    loadError: false,
    isElderMode: false,
    careLevelMap
  },

  onShow() {
    const app = getAppSafe();
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) this.setData({ isElderMode: isElder });
    if (!navigation.requireRole(['RESIDENT'], '/pages/user/elder-list')) return;
    this.loadElders();
  },

  onUnload() {
    this._disposed = true;
  },

  onPullDownRefresh() {
    this.loadElders().finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  loadElders() {
    this.setData({ loading: true, loadError: false });
    const context = sessionKey(getAppSafe());
    return request.get('/elders/my-list').then(data => {
      if (this._disposed || sessionKey(getAppSafe()) !== context) return;
      this.setData({
        elders: data || [],
        loading: false,
        loadError: false
      });
    }).catch(() => {
      if (!this._disposed) {
        this.setData({ loading: false, loadError: true });
      }
    });
  },

  goToCreate() {
    lightTap();
    navigation.navigateTo({ url: '/pages/user/elder-form' });
  },

  goToEdit(e) {
    const id = e.currentTarget?.dataset?.id;
    if (!id) return;
    lightTap();
    navigation.navigateTo({ url: `/pages/user/elder-form?id=${id}` });
  }
});
