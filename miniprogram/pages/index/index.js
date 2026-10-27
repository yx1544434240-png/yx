const navigation = require('../../utils/navigation.js');
const { syncTabBar } = require('../../utils/tab-bar.js');
// pages/index/index.js
const request = require('../../utils/request.js');
const { loadCatalog } = require('../../utils/service-catalog.js');
const { orderCard, sessionKey } = require('../../utils/list-state.js');
const { lightTap } = require('../../utils/feedback.js');
const { HOTLINE_NUMBER, HOTLINE_HOURS } = require('../../utils/contact.js');
const { DEFAULT_IMAGES, categoryImages } = require('../../utils/service-images.js');
const { ORDER_STATUS_TEXT } = require('../../utils/order-status.js');
const { getAppSafe, app } = require('../../utils/app-state.js');

Page({
  data: {
    isElderMode: false,
    isStaff: false,
    serviceItems: [],
    servicesLoading: false,
    serviceError: false,
    categoryPhotos: DEFAULT_IMAGES,
    photoFallbacks: DEFAULT_IMAGES,
    categoryIcons: { MEAL: 'meal', CLEANING: 'cleaning', ERRAND: 'errand', ESCORT: 'escort' },
    hotlineHours: HOTLINE_HOURS,
    activeOrder: null,
    activeOrderReady: false,
    activeOrderError: false,
    activeMoreCount: 0,
    statusTextMap: { ...ORDER_STATUS_TEXT }
  },

  onShow() {
    if (navigation.redirectStaffHome()) return;
    syncTabBar(this, 0);
    const app = getAppSafe();
    const patch = {};
    const isElder = Boolean(app.globalData?.isElderMode);
    const isStaff = app.globalData?.role === 'STAFF';
    if (this.data.isElderMode !== isElder) patch.isElderMode = isElder;
    if (this.data.isStaff !== isStaff) patch.isStaff = isStaff;
    if (Object.keys(patch).length) this.setData(patch);
    return Promise.all([this.loadServiceItems(), this.loadActiveOrder()]);
  },

  onUnload() { this._disposed = true; },

  onPullDownRefresh() {
    return Promise.all([this.loadServiceItems(true), this.loadActiveOrder(true)])
      .finally(() => wx.stopPullDownRefresh());
  },

  onModeChange(e) {
    const isElder = e.detail.value;
    app.toggleElderMode(isElder);
    syncTabBar(this, 0);
    this.setData({ isElderMode: isElder });
  },

  toggleMode() {
    const isElder = !this.data.isElderMode;
    app.toggleElderMode(isElder);
    syncTabBar(this, 0);
    this.setData({ isElderMode: isElder });
  },

  loadServiceItems(force = false) {
    const context = app.globalData.baseUrl || '';
    if (this._catalogContext !== context) {
      this._catalogContext = context; this._catalogPromise = null; this._catalogRows = null;
      if (this.data.serviceItems.length) this.setData({ serviceItems: [], categoryPhotos: DEFAULT_IMAGES });
    }
    if (this._catalogPromise) return this._catalogPromise;
    if (!this.data.serviceItems.length) this.setData({ servicesLoading: true, serviceError: false });
    const task = loadCatalog(force === true).then(rows => {
      if (this._disposed || (app.globalData.baseUrl || '') !== context) return;
      if (this._catalogRows !== rows) {
        this._catalogRows = rows;
        this.setData({ serviceItems: rows.slice(0, 3), categoryPhotos: categoryImages(rows), serviceError: false });
      }
    }).catch(() => {
      if (!this._disposed && this._catalogContext === context && !this.data.serviceItems.length) this.setData({ serviceError: true });
    }).finally(() => {
      if (this._catalogPromise === task) {
        if (!this._disposed && this.data.servicesLoading) this.setData({ servicesLoading: false });
        this._catalogPromise = null;
      }
    });
    this._catalogPromise = task;
    return task;
  },

  loadActiveOrder(force = false) {
    const context = sessionKey(app);
    if (this._activeContext !== context) {
      this._activeContext = context; this._activeAt = 0; this._activeTask = null; this._activeRefreshAfter = false;
      if (this.data.activeOrder || this.data.activeOrderReady) this.setData({ activeOrder: null, activeOrderReady: false });
    }
    if (!app.globalData.token) {
      if (!this.data.activeOrderReady || this.data.activeOrder) this.setData({ activeOrder: null, activeOrderReady: true });
      return Promise.resolve();
    }
    const version = app.globalData.ordersVersion || 0;
    if (this._activeTask) {
      if (this._activeRequestVersion !== version) this._activeRefreshAfter = true;
      return this._activeTask;
    }
    if (!force && this._activeVersion === version && this._activeAt && Date.now() - this._activeAt < 15000) {
      if (!this.data.activeOrderReady) this.setData({ activeOrderReady: true });
      return Promise.resolve();
    }
    this._activeRequestVersion = version;
    const task = request.get('/orders/page', {
      pageNum: 1, pageSize: 1,
      status: 'WAIT_DISPATCH,WAIT_ACCEPT,WAIT_VISIT,IN_SERVICE,WAIT_CONFIRM,WAIT_MANUAL'
    }, { silent: true }).then(data => {
      if (this._disposed || sessionKey(app) !== context) return;
      if (version !== (app.globalData.ordersVersion || 0)) { this._activeRefreshAfter = true; return; }
      const row = data?.records?.[0];
      const total = Number(data?.total) || (row ? 1 : 0);
      this.setData({ activeOrder: row ? orderCard(row) : null, activeOrderReady: true, activeOrderError: false, activeMoreCount: row ? Math.max(0, total - 1) : 0 });
      this._activeAt = Date.now(); this._activeVersion = version;
    }).catch(() => {
      // 失败时不要静默显示欢迎区：没有已知订单就提示可重试
      if (!this._disposed && sessionKey(app) === context) this.setData({ activeOrderReady: true, activeOrderError: !this.data.activeOrder });
    }).finally(() => {
      if (this._activeTask === task) {
        this._activeTask = null;
        if (this._activeRefreshAfter && !this._disposed) {
          this._activeRefreshAfter = false;
          return this.loadActiveOrder(true);
        }
      }
    });
    this._activeTask = task;
    return task;
  },

  callHotline() {
    wx.makePhoneCall({
      phoneNumber: HOTLINE_NUMBER,
      fail() {
        wx.showModal({
          title: '社区服务热线',
          content: `${HOTLINE_NUMBER}（服务时间 ${HOTLINE_HOURS}）`,
          showCancel: false
        });
      }
    });
  },

  goToService(e) {
    lightTap();
    const category = e.currentTarget.dataset.category;
    app.globalData.serviceCategory = category;
    navigation.switchTab({ url: '/pages/service/list' });
  },

  goToServiceList() {
    navigation.switchTab({
      url: '/pages/service/list'
    });
  },

  goToServiceDetail(e) {
    const id = e.currentTarget.dataset.id;
    navigation.navigateTo({
      url: `/pages/service/detail?id=${id}`
    });
  },

  retryActiveOrder() {
    this.setData({ activeOrderReady: false, activeOrderError: false });
    return this.loadActiveOrder(true);
  },

  goToActiveOrders() {
    navigation.switchTab({ url: '/pages/order/list' });
  },

  goToOrderDetail(e) {
    const id = e.currentTarget.dataset.id;
    navigation.navigateTo({
      url: `/pages/order/detail?id=${id}`
    });
  },

  goToWorkbench() {
    navigation.navigateTo({
      url: '/pages/staff/workbench'
    });
  }
});
