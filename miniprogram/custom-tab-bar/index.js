const navigation = require('../utils/navigation.js');

const RESIDENT_LIST = [
  {
    pagePath: "pages/index/index",
    text: "首页",
    iconPath: "/assets/icons/home-muted.png",
    selectedIconPath: "/assets/icons/home.png"
  },
  {
    pagePath: "pages/service/list",
    text: "服务",
    iconPath: "/assets/icons/service-muted.png",
    selectedIconPath: "/assets/icons/service.png"
  },
  {
    pagePath: "pages/order/list",
    text: "订单",
    iconPath: "/assets/icons/order-muted.png",
    selectedIconPath: "/assets/icons/order.png"
  },
  {
    pagePath: "pages/user/profile",
    text: "我的",
    iconPath: "/assets/icons/user-muted.png",
    selectedIconPath: "/assets/icons/user.png"
  }
];

const STAFF_LIST = [
  {
    pagePath: "pages/staff/workbench",
    text: "工作台",
    iconPath: "/assets/icons/staff-muted.png",
    selectedIconPath: "/assets/icons/staff.png"
  },
  {
    pagePath: "pages/user/profile",
    text: "我的",
    iconPath: "/assets/icons/user-muted.png",
    selectedIconPath: "/assets/icons/user.png"
  }
];

Component({
  data: {
    selected: 0,
    overlayHidden: false,
    isElderMode: false,
    list: RESIDENT_LIST
  },
  lifetimes: {
    attached() {
      this.syncFromRoute();
    }
  },
  pageLifetimes: {
    show() {
      this.syncFromRoute();
    }
  },
  methods: {
    setOverlayVisible(owner, visible) {
      if (!this._overlayOwners) this._overlayOwners = new Set();
      if (visible) this._overlayOwners.add(owner);
      else this._overlayOwners.delete(owner);
      const overlayHidden = this._overlayOwners.size > 0;
      if (this.data.overlayHidden !== overlayHidden) this.setData({ overlayHidden });
    },
    getRole() {
      try {
        const app = typeof getApp === 'function' ? getApp() : null;
        return app && app.globalData ? app.globalData.role || 'RESIDENT' : 'RESIDENT';
      } catch (_) {
        return 'RESIDENT';
      }
    },
    syncState(index, isElderMode) {
      if (typeof isElderMode === 'undefined') {
        try {
          const app = typeof getApp === 'function' ? getApp() : null;
          isElderMode = app && app.globalData ? app.globalData.isElderMode : false;
        } catch (_) {
          isElderMode = false;
        }
      }
      const role = this.getRole();
      const currentList = role === 'STAFF' ? STAFF_LIST : RESIDENT_LIST;
      const patch = {};
      if (this.data.list !== currentList && (this.data.list.length !== currentList.length || this.data.list[0]?.pagePath !== currentList[0]?.pagePath)) {
        patch.list = currentList;
      }
      try {
        const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : [];
        const route = pages.length ? pages[pages.length - 1].route : '';
        const routeIndex = currentList.findIndex(item => item.pagePath === route);
        const targetIndex = routeIndex >= 0 ? routeIndex : (Number.isInteger(index) && currentList[index] ? index : 0);
        if (this.data.selected !== targetIndex) {
          patch.selected = targetIndex;
        }
      } catch (_) {
        if (Number.isInteger(index) && currentList[index] && this.data.selected !== index) {
          patch.selected = index;
        }
      }
      if (this.data.isElderMode !== Boolean(isElderMode)) {
        patch.isElderMode = Boolean(isElderMode);
      }
      if (Object.keys(patch).length) {
        this.setData(patch);
      }
    },
    syncFromRoute() {
      this.syncState();
    },
    switchTab(e) {
      const index = Number(e.currentTarget.dataset.index);
      const item = this.data.list[index];
      if (!item || this.data.overlayHidden || this._switching || index === this.data.selected) return;
      const previous = this.data.selected;
      this._switching = true;
      this.syncState(index);
      navigation.switchTab({
        url: '/' + item.pagePath,
        fail: () => {
          this.syncState(previous);
        },
        complete: () => {
          this._switching = false;
        }
      });
    }
  }
});
