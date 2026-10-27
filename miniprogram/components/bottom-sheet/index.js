const { sheetDelta, sheetShouldClose } = require('../../utils/sheet-gesture.js');

Component({
  options: { styleIsolation: 'apply-shared' },
  properties: {
    show: { type: Boolean, value: false },
    title: { type: String, value: '' },
    content: { type: String, value: '' },
    confirmText: { type: String, value: '' },
    dismissText: { type: String, value: '再想想' },
    tone: { type: String, value: '' },
    loading: { type: Boolean, value: false },
    success: { type: Boolean, value: false },
    // false：点遮罩不关闭（含未提交输入的抽屉），仍可用取消按钮关闭
    maskClosable: { type: Boolean, value: true },
    // true：内容放进可滚动区域（最高 60vh），只能从把手/标题区下拉关闭，内容区可正常滚动
    scrollable: { type: Boolean, value: false }
  },
  data: { rendered: false, drag: 0, dragging: false },
  lifetimes: {
    detached() {
      clearTimeout(this._closeTimer);
      this.setTabBarBlocked(false);
    }
  },
  pageLifetimes: {
    hide() { this.setTabBarBlocked(false); },
    show() { if (this.properties.show) this.setTabBarBlocked(true); }
  },
  observers: {
    show(show) {
      clearTimeout(this._closeTimer);
      this._startY = null;
      if (show) {
        this._opened = true;
        this.setTabBarBlocked(true);
        this.setData({ rendered: true, dragging: false, drag: 420 }, () => {
          const enter = () => { if (this._opened) this.setData({ drag: 0 }); };
          if (typeof wx !== 'undefined' && typeof wx.nextTick === 'function') wx.nextTick(enter);
          else enter();
        });
        return;
      }
      if (this._opened && this.data.rendered) this.finishClose();
    }
  },
  methods: {
    // 自定义 TabBar 位于独立渲染层，弹层期间移除底栏，关闭后恢复。
    setTabBarBlocked(blocked) {
      if (!blocked) {
        if (this._overlayBar) this._overlayBar.setOverlayVisible(this, false);
        this._overlayBar = null;
        return;
      }
      if (typeof getCurrentPages !== 'function') return;
      const pages = getCurrentPages();
      const page = pages[pages.length - 1];
      const bar = page && typeof page.getTabBar === 'function' ? page.getTabBar() : null;
      if (!bar || typeof bar.setOverlayVisible !== 'function') return;
      if (this._overlayBar && this._overlayBar !== bar) this._overlayBar.setOverlayVisible(this, false);
      this._overlayBar = bar;
      bar.setOverlayVisible(this, true);
    },
    noop() {},
    // scrollable 模式下内容区的 touchmove 不参与拖拽
    onContentMove() {},
    onMask() { if (this.data.maskClosable) this.triggerEvent('close'); },
    onDismiss() { this.triggerEvent('close'); },
    onConfirm() { this.triggerEvent('confirm'); },
    onStart(event) {
      const touch = event.touches && event.touches[0];
      if (!touch) return;
      const target = event.target || {};
      if (target.dataset && (target.dataset.noDrag || target.dataset.noDrag === '')) return;
      if (target.id === 'record-textarea' || target.type === 'textarea') return;
      this._startY = touch.clientY;
      this._startAt = Date.now();
      this.setData({ dragging: true });
    },
    onMove(event) {
      const touch = event.touches && event.touches[0];
      if (!touch || this._startY == null) return;
      const nextDrag = Math.round(sheetDelta(this._startY, touch.clientY));
      if (nextDrag === this.data.drag) return;
      this.setData({ drag: nextDrag });
    },
    onEnd() {
      if (this._startY == null) return;
      const delta = this.data.drag;
      const elapsed = Date.now() - this._startAt;
      this._startY = null;
      this.setData({ dragging: false });
      // 先回弹；若父页面同意关闭，show 变为 false 时再收起，避免确认弹窗取消后抽屉停在半路
      this.setData({ drag: 0 });
      if (sheetShouldClose(delta, elapsed, 420)) this.triggerEvent('close');
    },
    finishClose() {
      this._opened = false;
      clearTimeout(this._closeTimer);
      this.setData({ drag: 640, dragging: false });
      this._closeTimer = setTimeout(() => {
        if (!this._opened) this.setData({ rendered: false, drag: 0 }, () => this.setTabBarBlocked(false));
      }, 280);
    },
    onCancel() {
      this._startY = null;
      this.setData({ drag: 0, dragging: false });
    }
  }
});
