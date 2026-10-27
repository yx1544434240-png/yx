const navigation = require('../../utils/navigation.js');
const { syncTabBar } = require('../../utils/tab-bar.js');
const request = require('../../utils/request.js');
const { orderCard, visibleTabs, sessionKey } = require('../../utils/list-state.js');
const { createSwipeMethods } = require('../../utils/slider-shift.js');
const { ORDER_STATUS_TEXT } = require('../../utils/order-status.js');
const { app } = require('../../utils/app-state.js');

const TTL = 15 * 1000;
const PAGE_SIZE = 10;
const TABS = [
  { key: '', label: '全部' },
  { key: 'WAIT_DISPATCH,WAIT_ACCEPT,WAIT_MANUAL', label: '待调度' },
  { key: 'WAIT_VISIT', label: '待上门' },
  { key: 'IN_SERVICE', label: '服务中' },
  { key: 'WAIT_CONFIRM', label: '待确认' },
  { key: 'COMPLETED', label: '已完成' }
];

const emptyView = () => ({ list: [], hasMore: true, loaded: false, loading: false, error: false, isRefreshing: false });
const emptyCache = () => ({
  rows: [], nextPage: 1, hasMore: true, loaded: false, loading: false,
  error: false, updatedAt: 0, version: -1, promise: null, replace: false
});

Page({
  data: {
    tabs: TABS,
    currentTabIndex: 0,
    isElderMode: false,
    isLoggedIn: true,
    visibleTabs: visibleTabs(0, TABS.length),
    tabData: TABS.map(emptyView),
    sliderShift: 0,
    sliderLive: false,
    statusTextMap: { ...ORDER_STATUS_TEXT },
    statusTagMap: {
      WAIT_DISPATCH: 'tag-warning', WAIT_ACCEPT: 'tag-warning', WAIT_VISIT: 'tag-info',
      IN_SERVICE: 'tag-info', WAIT_CONFIRM: 'tag-warning', COMPLETED: 'tag-success',
      WAIT_MANUAL: 'tag-danger', CANCELLED: 'tag-danger'
    }
  },

  ...createSwipeMethods({ indexKey: 'currentTabIndex', count: TABS.length }),

  onLoad(options = {}) {
    this._disposed = false;
    this.measurePage();
    this.resetSession();
    const index = this.findStatus(options.status || '');
    this.selectTab(index);
  },

  onReady() {
    this._ready = true;
  },

  onShow() {
    if (navigation.redirectStaffHome()) return;
    syncTabBar(this, 2);
    this._hidden = false;
    const token = app.globalData?.token || '';
    const isLoggedIn = Boolean(token);
    const isElder = Boolean(app.globalData?.isElderMode);

    const patch = {};
    if (this.data.isLoggedIn !== isLoggedIn) patch.isLoggedIn = isLoggedIn;
    if (this.data.isElderMode !== isElder) patch.isElderMode = isElder;

    if (!this._cache || this._context !== sessionKey(app)) {
      this.resetSession();
    }

    const tabQuery = navigation.takeTabQuery('/pages/order/list');
    if (tabQuery && tabQuery.status !== undefined && app.globalData) app.globalData.orderStatusFilter = tabQuery.status;
    let index = this.data.currentTabIndex;
    if (app.globalData && Object.prototype.hasOwnProperty.call(app.globalData, 'orderStatusFilter')) {
      index = this.findStatus(app.globalData.orderStatusFilter);
      delete app.globalData.orderStatusFilter;
    }
    this.selectTab(index, Boolean(this._ready) && index !== this.data.currentTabIndex);

    if (Object.keys(patch).length) {
      this.setData(patch);
    }

    if (!isLoggedIn) {
      return Promise.resolve();
    }
    return this.ensureTab(index);
  },

  onHide() {
    this._hidden = true;
    this._animating = false;
  },

  onUnload() {
    this._disposed = true;
    this._cache = null;
  },

  resetSession() {
    this._context = sessionKey(app);
    this._cache = TABS.map(emptyCache);
    this._animating = false;
    this.setData({ tabData: TABS.map(emptyView) });
  },

  findStatus(status) {
    return Math.max(0, TABS.findIndex(tab => tab.key === status || tab.key.split(',').includes(status)));
  },

  selectTab(index, animate = false) {
    if (!Number.isInteger(index) || index < 0 || index >= TABS.length) return;
    this._animating = animate;
    const flags = visibleTabs(index, TABS.length, animate ? this.data.visibleTabs : []);
    const patch = {};
    if (this.data.currentTabIndex !== index) patch.currentTabIndex = index;
    if (!this._gesturing && this.data.sliderShift !== index * 100) patch.sliderShift = index * 100;
    if (!this._gesturing && this.data.sliderLive) patch.sliderLive = false;
    if (flags.some((flag, i) => flag !== this.data.visibleTabs[i])) patch.visibleTabs = flags;
    flags.forEach((mounted, i) => {
      if (!mounted && this.data.tabData[i].list.length) patch[`tabData[${i}]`] = emptyView();
    });
    if (Object.keys(patch).length) this.setData(patch);
    flags.forEach((mounted, i) => { if (mounted) this.publishTab(i); });
  },

  publishTab(index) {
    if (this._hidden || this._disposed || !this.data.visibleTabs[index] || !this._cache?.[index]) return;
    const cache = this._cache[index];
    const view = this.data.tabData[index];
    const prefix = `tabData[${index}]`;
    const patch = {};
    for (const key of ['hasMore', 'loaded', 'loading', 'error']) {
      if (view[key] !== cache[key]) patch[`${prefix}.${key}`] = cache[key];
    }
    if (cache.replace || view.list.length > cache.rows.length) {
      patch[`${prefix}.list`] = cache.rows;
    } else {
      for (let i = view.list.length; i < cache.rows.length; i++) {
        patch[`${prefix}.list[${i}]`] = cache.rows[i];
      }
    }
    cache.replace = false;
    if (Object.keys(patch).length) this.setData(patch);
  },

  onTabClick(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.currentTabIndex) return;
    this.selectTab(index, true);
    if (this.data.isLoggedIn) {
      return this.ensureTab(index);
    }
  },

  onSwiperChange(e) {
    const index = e.detail.current;
    if (e.detail.source !== 'touch' || index === this.data.currentTabIndex) return;
    this.selectTab(index, true);
    if (this.data.isLoggedIn) {
      return this.ensureTab(index);
    }
  },

  onAnimationFinish(e) {
    this._gesturing = false;
    this._gestureIndex = null;
    if (e.detail.current === this.data.currentTabIndex) this.selectTab(e.detail.current);
  },

  onRefreshScroll(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index !== this.data.currentTabIndex) return;
    this.setData({ [`tabData[${index}].isRefreshing`]: true });
    return this.ensureTab(index, { force: true }).finally(() => {
      if (!this._disposed && this.data.visibleTabs[index]) this.setData({ [`tabData[${index}].isRefreshing`]: false });
    });
  },

  onReachBottomScroll(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.currentTabIndex) return this.ensureTab(index, { more: true });
  },

  retryTab(e) {
    return this.ensureTab(Number(e.currentTarget.dataset.index), { force: true });
  },

  ensureTab(index, { force = false, more = false } = {}) {
    const token = app.globalData?.token || '';
    if (!token || !this._cache?.[index]) return Promise.resolve();

    const entry = this._cache[index];
    const version = app.globalData?.ordersVersion || 0;
    if (entry.promise) {
      if (force || entry.requestVersion !== version) entry.refreshAfter = true;
      return entry.promise;
    }

    const stale = entry.version !== version || (!more && Date.now() - entry.updatedAt >= TTL);
    if (!force && !more && entry.loaded && !stale) return Promise.resolve();
    if (more && entry.loaded && !entry.hasMore && !stale) return Promise.resolve();

    const reset = force || !entry.loaded || stale;
    const pageNum = reset ? 1 : entry.nextPage;
    const context = this._context;
    const valid = () => !this._disposed && this._context === context && sessionKey(app) === context && this._cache?.[index] === entry;

    entry.loading = true;
    entry.error = false;
    entry.requestVersion = version;
    this.publishTab(index);

    const query = { pageNum, pageSize: PAGE_SIZE };
    // “全部”必须省略参数，避免真机把 null 编码为 status=null 后被当作状态筛选。
    if (TABS[index].key) query.status = TABS[index].key;
    const task = request.get('/orders/page', query)
      .then(data => {
        if (!valid()) return;
        if (version !== (app.globalData?.ordersVersion || 0)) {
          entry.refreshAfter = true;
          return;
        }
        if (!Array.isArray(data?.records)) throw new Error('订单列表响应格式异常');
        const records = data.records.map(orderCard);
        if (reset) {
          entry.rows = records;
          entry.replace = true;
        } else {
          entry.rows = entry.rows.concat(records);
        }
        entry.nextPage = pageNum + 1;
        entry.hasMore = records.length === PAGE_SIZE;
        entry.loaded = true;
        entry.updatedAt = Date.now();
        entry.version = version;
      }).catch(err => {
        console.warn('[order/list ensureTab error]', err);
        if (valid()) entry.error = true;
      }).finally(() => {
        if (!valid()) return;
        entry.loading = false;
        entry.promise = null;
        if (!this._animating) this.publishTab(index);
        if (entry.refreshAfter) {
          entry.refreshAfter = false;
          return this.ensureTab(index, { force: true });
        }
      });

    entry.promise = task;
    return task;
  },

  goToDetail(e) {
    navigation.navigateTo({ url: `/pages/order/detail?id=${e.currentTarget.dataset.id}` });
  },

  goToServices() {
    navigation.switchTab({ url: '/pages/service/list' });
  },

  showAllOrders() {
    this.selectTab(0, this.data.currentTabIndex !== 0);
    return this.ensureTab(0, { force: true });
  },

  goToLogin() {
    navigation.toLogin('/pages/order/list');
  },

  noAction() {}
});
