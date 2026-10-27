const navigation = require('../../utils/navigation.js');
const { lightTap } = require('../../utils/feedback.js');
const { getCachedService, loadService } = require('../../utils/service-catalog.js');
const { getAppSafe, app } = require('../../utils/app-state.js');

Page({
  data: {
    serviceId: null,
    service: null,
    loading: true,
    loadError: false,
    missingId: false,
    isElderMode: false,
    loginSheet: false
  },

  onLoad(options) {
    const app = getAppSafe();
    if (options && options.id) {
      const cached = getCachedService(options.id);
      this.setData({
        serviceId: options.id,
        service: cached,
        loading: !cached,
        isElderMode: Boolean(app.globalData?.isElderMode)
      });
      return this.loadServiceDetail();
    } else {
      this.setData({ loading: false, missingId: true });
    }
  },

  goHome() {
    navigation.switchTab({ url: '/pages/index/index' });
  },

  onShow() {
    const app = getAppSafe();
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) {
      this.setData({ isElderMode: isElder });
    }
    const currentBase = app.globalData?.baseUrl || '';
    if (this._serviceContext !== currentBase && this.data.serviceId) {
      return this.loadServiceDetail();
    }
  },

  onUnload() {
    this._disposed = true;
  },

  loadServiceDetail() {
    const app = getAppSafe();
    const context = app.globalData?.baseUrl || '';
    if (!this.data.serviceId) return Promise.resolve();
    if (this._pending && this._serviceContext === context) return this._pending;
    if (this._serviceContext !== undefined && this._serviceContext !== context) {
      this.setData({ service: null });
    }
    this._serviceContext = context;
    if (!this.data.service) {
      this.setData({ loading: true, loadError: false });
    }
    const valid = () => !this._disposed && (app.globalData?.baseUrl || '') === context && this._pending === task;
    const task = loadService(this.data.serviceId).then(data => {
      if (valid() && data) {
        this.setData({ service: data, loadError: false });
      }
    }).catch(err => {
      console.warn('[service/detail loadService error]', err);
      if (valid() && !this.data.service) {
        this.setData({ loadError: true });
      }
    }).finally(() => {
      if (valid()) {
        this._pending = null;
        this.setData({ loading: false });
      }
    });
    this._pending = task;
    return task;
  },

  goToBook() {
    lightTap();
    const app = getAppSafe();
    const returnUrl = `/pages/order/create?serviceId=${this.data.serviceId}`;
    if (!app.globalData?.token) {
      this._loginReturn = returnUrl;
      this.setData({ loginSheet: true });
      return;
    }
    navigation.navigateTo({ url: returnUrl });
  },

  closeLoginSheet() {
    this.setData({ loginSheet: false });
  },

  confirmLogin() {
    const returnUrl = this._loginReturn || `/pages/order/create?serviceId=${this.data.serviceId}`;
    this.setData({ loginSheet: false });
    navigation.toLogin(returnUrl);
  }
});
