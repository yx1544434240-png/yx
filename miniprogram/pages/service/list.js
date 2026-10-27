const navigation = require('../../utils/navigation.js');
const { syncTabBar } = require('../../utils/tab-bar.js');
const { loadCatalog } = require('../../utils/service-catalog.js');
const { visibleTabs } = require('../../utils/list-state.js');
const { createSwipeMethods } = require('../../utils/slider-shift.js');
const { app } = require('../../utils/app-state.js');

const CATEGORIES = [
  { key: '', label: '全部' },
  { key: 'MEAL', label: '助餐送餐' },
  { key: 'CLEANING', label: '居家保洁' },
  { key: 'ERRAND', label: '代买代办' },
  { key: 'ESCORT', label: '陪同办事' }
];

Page({
  data: {
    isElderMode: false,
    categories: CATEGORIES,
    currentCatIndex: 0,
    selectedCat: '',
    catServices: CATEGORIES.map(() => []),
    visibleTabs: visibleTabs(0, CATEGORIES.length),
    sliderShift: 0,
    sliderLive: false,
    loaded: false,
    loading: false,
    loadError: false,
    categoryIcons: { MEAL: 'meal', CLEANING: 'cleaning', ERRAND: 'errand', ESCORT: 'escort' }
  },

  ...createSwipeMethods({ indexKey: 'currentCatIndex', count: CATEGORIES.length }),

  onLoad(options = {}) {
    this._disposed = false;
    this.measurePage();
    const index = Math.max(0, CATEGORIES.findIndex(cat => cat.key === options.category));
    this.selectCategory(index);
  },

  onShow() {
    if (navigation.redirectStaffHome()) return;
    syncTabBar(this, 1);
    if (this._animating) this.selectCategory(this.data.currentCatIndex);
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) {
      this.setData({ isElderMode: isElder });
    }
    const tabQuery = navigation.takeTabQuery('/pages/service/list');
    if (tabQuery && tabQuery.category && app.globalData) app.globalData.serviceCategory = tabQuery.category;
    const requested = app.globalData?.serviceCategory;
    if (requested) {
      const index = CATEGORIES.findIndex(cat => cat.key === requested);
      if (index >= 0) this.selectCategory(index, this.data.loaded);
      app.globalData.serviceCategory = '';
    }
    return this.loadServices();
  },

  onUnload() {
    this._disposed = true;
  },

  selectCategory(index, animate = false) {
    if (!Number.isInteger(index) || index < 0 || index >= CATEGORIES.length) return;
    this._animating = animate;
    const flags = visibleTabs(index, CATEGORIES.length, animate ? this.data.visibleTabs : []);
    const patch = {};
    if (this.data.currentCatIndex !== index) patch.currentCatIndex = index;
    if (this.data.selectedCat !== CATEGORIES[index].key) patch.selectedCat = CATEGORIES[index].key;
    if (flags.some((flag, i) => flag !== this.data.visibleTabs[i])) patch.visibleTabs = flags;
    if (!this._gesturing && this.data.sliderShift !== index * 100) patch.sliderShift = index * 100;
    if (!this._gesturing && this.data.sliderLive) patch.sliderLive = false;
    flags.forEach((mounted, i) => {
      const rows = mounted ? (this._groups?.[i] || []) : [];
      if (rows !== this.data.catServices[i] && (rows.length || this.data.catServices[i].length)) patch[`catServices[${i}]`] = rows;
    });
    if (Object.keys(patch).length) this.setData(patch);
  },

  changeCat(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index !== this.data.currentCatIndex) this.selectCategory(index, true);
  },

  onSwiperChange(e) {
    const index = e.detail.current;
    if (e.detail.source === 'touch' && index !== this.data.currentCatIndex) {
      this.selectCategory(index, true);
    }
  },

  onAnimationFinish(e) {
    this._gesturing = false;
    this._gestureIndex = null;
    if (e.detail.current === this.data.currentCatIndex) {
      this.selectCategory(e.detail.current);
    }
  },

  loadServices() {
    const context = app.globalData?.baseUrl || '';
    if (this._catalogContext !== context) {
      this._catalogContext = context;
      this._catalogPromise = null;
      this._rows = null;
      this._groups = null;
      this.setData({ loaded: false, catServices: CATEGORIES.map(() => []) });
    }
    if (this._catalogPromise) return this._catalogPromise;
    if (!this.data.loaded) this.setData({ loading: true, loadError: false });

    const task = loadCatalog().then(rows => {
      if (this._disposed || (app.globalData?.baseUrl || '') !== context || this._rows === rows) return;
      this._rows = rows;
      this._groups = CATEGORIES.map(cat => cat.key ? rows.filter(row => row.category === cat.key) : rows);
      this.setData({ loaded: true, loadError: false });
      this.selectCategory(this.data.currentCatIndex, this._animating);
    }).catch(() => {
      if (!this._disposed && this._catalogContext === context) {
        this.setData({ loadError: true });
      }
    }).finally(() => {
      if (this._catalogPromise === task) {
        if (!this._disposed && this.data.loading) this.setData({ loading: false });
        this._catalogPromise = null;
      }
    });

    this._catalogPromise = task;
    return task;
  },

  // 卡片和按钮都进入服务详情，预约只在详情页发起
  goToDetail(e) {
    navigation.navigateTo({ url: `/pages/service/detail?id=${e.currentTarget.dataset.id}` });
  }
});
