const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { loadMedia } = require('../../utils/media-url.js');
const { hasCoordinates } = require('../../utils/coordinates.js');
const { lightTap } = require('../../utils/feedback.js');
const { markOrdersChanged, sessionKey } = require('../../utils/list-state.js');
const { friendlyTime, friendlyRange } = require('../../utils/time-format.js');
const { detailView } = require('../../utils/order-view.js');
const { ORDER_STATUS_TEXT_DETAIL } = require('../../utils/order-status.js');
const { getAppSafe, app } = require('../../utils/app-state.js');
const { parseTags, FEEDBACK_STATUS } = require('../../utils/review-feedback.js');

function decorateTimes(data) {
  if (!data) return;
  if (data.order) data.order.plannedStartText = friendlyTime(data.order.plannedStartAt);
  if (data.serviceRecord) data.serviceRecord.actualRangeText = friendlyRange(data.serviceRecord.actualStartAt, data.serviceRecord.actualEndAt);
  if (Array.isArray(data.logs)) data.logs.forEach(log => { log.createdAtText = friendlyTime(log.createdAt); });
  if (data.serviceReview) data.serviceReview.tagList = parseTags(data.serviceReview.tags);
  if (Array.isArray(data.feedbackRecords)) data.feedbackRecords.forEach(record => {
    record.statusText = FEEDBACK_STATUS[record.status] || '待核实';
    record.createdAtText = friendlyTime(record.createdAt);
    record.handledAtText = friendlyTime(record.handledAt);
  });
}

function formatTrackingDistance(value) {
  if (value === null || value === undefined || value === '') return '';
  const meters = Number(value);
  if (!Number.isFinite(meters) || meters < 0) return '';
  return meters >= 1000 ? (meters / 1000).toFixed(1) + 'km' : meters + 'm';
}

Page({
  data: {
    orderId: null,
    orderVO: null,
    fulfillmentPhotos: [],
    loading: true,
    loadError: false,
    missingId: false,
    canCancel: false,
    isElderMode: false,
    cancelSheet: false,
    confirmSheet: false,
    paySheet: false,
    paying: false,
    tracking: null,
    hasTrackingLocation: false,
    trackingDistanceText: '',
    markers: [],
    polyline: [],
    mapCenter: { latitude: 39.9042, longitude: 116.4074 },
    mapScale: 15,
    statusTextMap: { ...ORDER_STATUS_TEXT_DETAIL },
    statusSubMap: {
      'WAIT_DISPATCH': '系统正在为您匹配符合技能与时间要求的服务人员',
      'WAIT_ACCEPT': '已向服务人员发送派单通知，等待确认接单',
      'WAIT_VISIT': '服务人员已接单，正在准备并按时上门履约',
      'IN_SERVICE': '服务人员已到达现场，正在提供照护服务',
      'WAIT_CONFIRM': '服务人员已完成服务并提交记录，请您核实确认',
      'COMPLETED': '服务已顺利完成，感谢您对社区养老工作的信任',
      'WAIT_MANUAL': '暂时暂无匹配人员，社区管理员正在人工协调处置',
      'CANCELLED': '该订单已按规则取消，排班预留已释放'
    }
  },

  onLoad(options) {
    if (options && options.id) {
      this.setData({ orderId: options.id });
    } else {
      this.setData({ loading: false, missingId: true });
    }
  },

  onShow() {
    this._hidden = false;
    const app = getAppSafe();
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) {
      this.setData({ isElderMode: isElder });
    }
    if (!navigation.requireLogin(`/pages/order/detail?id=${this.data.orderId || ''}`)) return;
    if (this.data.orderId) {
      return this.loadDetail(this.data.orderId);
    }
  },

  onHide() {
    this._hidden = true;
    this.stopTrackingPoll();
  },

  onUnload() {
    this._disposed = true;
    this._hidden = true;
    this.stopTrackingPoll();
  },

  onPullDownRefresh() {
    return this.loadDetail(this.data.orderId, true).finally(() => wx.stopPullDownRefresh());
  },

  goHome() {
    navigation.switchTab({ url: '/pages/index/index' });
  },

  retryDetail() {
    return this.loadDetail(this.data.orderId, true);
  },

  loadDetail(id, force = false) {
    if (!id) return Promise.resolve();
    const app = getAppSafe();
    const context = sessionKey(app);
    if (this._context !== context) {
      this._context = context;
      this._pending = null;
      this._updatedAt = 0;
      this._refreshAfter = false;
      if (this.data.orderVO) this.setData({ orderVO: null, canCancel: false });
      this.updateMapState(null);
      this.stopTrackingPoll();
    }
    const version = app.globalData?.ordersVersion || 0;
    if (this._pending) {
      if (force || this._requestVersion !== version) this._refreshAfter = true;
      return this._pending;
    }
    if (!force && this._version === version && this._updatedAt && Date.now() - this._updatedAt < 15000) {
      if (['WAIT_VISIT', 'IN_SERVICE'].includes(this.data.orderVO?.order?.status)) this.startTrackingPoll();
      return Promise.resolve();
    }
    this._requestVersion = version;
    this.setData({ loading: true, loadError: false });
    const valid = () => !this._disposed && this._context === context && sessionKey(getAppSafe()) === context;
    const task = request.get(`/orders/${id}`).then(async data => {
      if (!valid()) return;
      const curApp = getAppSafe();
      if (version !== (curApp.globalData?.ordersVersion || 0)) {
        this._refreshAfter = true;
        return;
      }
      const canCancel = ['WAIT_DISPATCH', 'WAIT_ACCEPT', 'WAIT_VISIT', 'WAIT_MANUAL'].includes(data?.order?.status);
      let fulfillmentPhotos = [];
      let unavailablePhotos = 0;
      if (data?.serviceRecord?.attachmentsJson) {
        try {
          const parsed = JSON.parse(data.serviceRecord.attachmentsJson);
          if (Array.isArray(parsed)) {
            const images = await Promise.allSettled(parsed.map(loadMedia));
            fulfillmentPhotos = images.filter(image => image.status === 'fulfilled').map(image => image.value);
            unavailablePhotos = images.length - fulfillmentPhotos.length;
          }
        } catch (_) {}
      }
      if (!valid()) return;
      decorateTimes(data);
      this.setData({ orderVO: detailView(data), canCancel, fulfillmentPhotos, unavailablePhotos });
      this._version = version;
      this._updatedAt = Date.now();
      this.updateMapState(data?.tracking || null);
      if (['WAIT_VISIT', 'IN_SERVICE'].includes(data?.order?.status)) {
        this.startTrackingPoll();
      } else {
        this.stopTrackingPoll();
      }
    }).catch(err => {
      console.warn('[order/detail loadDetail error]', err);
      if (valid()) this.setData({ loadError: true });
    }).finally(() => {
      if (!valid() || this._pending !== task) return;
      this._pending = null;
      this.setData({ loading: false });
      if (this._refreshAfter) {
        this._refreshAfter = false;
        return this.loadDetail(id, true);
      }
    });
    this._pending = task;
    return task;
  },

  fetchTracking() {
    if (!this.data.orderId || this._disposed || this._hidden) return Promise.resolve();
    const context = sessionKey(getAppSafe());
    const id = this.data.orderId;
    if (this._trackingPending?.context === context) return this._trackingPending.task;
    const job = { context };
    this._trackingPending = job;
    const task = request.get(`/orders/${id}/tracking`, null, { silent: true }).then(tracking => {
      if (this._disposed || this._hidden || sessionKey(getAppSafe()) !== context || this.data.orderId !== id) return;
      const status = this.data.orderVO?.order?.status;
      if (status && !['WAIT_VISIT', 'IN_SERVICE'].includes(status)) return;
      if (tracking?.status && status && tracking.status !== status) {
        return this.loadDetail(id, true);
      }
      this.updateMapState(tracking);
    }).catch(err => {
      console.warn('[order/detail fetchTracking error]', err);
    }).finally(() => {
      if (this._trackingPending === job) this._trackingPending = null;
    });
    job.task = task;
    return task;
  },

  updateMapState(tracking) {
    if (!tracking) {
      this.setData({ tracking: null, trackingDistanceText: '', hasTrackingLocation: false, markers: [], polyline: [] });
      return;
    }
    const trackingDistanceText = formatTrackingDistance(tracking.distanceMeters);
    const destLat = Number(tracking.destLatitude);
    const destLon = Number(tracking.destLongitude);
    const staffLat = Number(tracking.staffLatitude);
    const staffLon = Number(tracking.staffLongitude);
    const hasStaff = hasCoordinates(tracking.staffLatitude, tracking.staffLongitude);

    if (!hasCoordinates(tracking.destLatitude, tracking.destLongitude)) {
      this.setData({ tracking, trackingDistanceText, hasTrackingLocation: false, markers: [], polyline: [] });
      return;
    }

    const markers = [];
    markers.push({
      id: 1,
      latitude: destLat,
      longitude: destLon,
      title: '服务目的地',
      iconPath: '/assets/icons/home.png',
      width: 32,
      height: 32,
      callout: {
        content: `老人住址: ${tracking.elderName || '老人'}\n${tracking.destAddress || ''}`,
        color: '#176B5D',
        fontSize: 12,
        borderRadius: 8,
        borderWidth: 1,
        borderColor: '#d8e4cf',
        bgColor: '#ffffff',
        padding: 6,
        display: 'ALWAYS'
      }
    });

    if (hasStaff) {
      markers.push({
        id: 2,
        latitude: staffLat,
        longitude: staffLon,
        title: tracking.staffName || '服务人员',
        iconPath: '/assets/icons/staff.png',
        width: 34,
        height: 34,
        callout: {
          content: `${tracking.staffName || '服务人员'}${trackingDistanceText ? ' (距约' + trackingDistanceText + ')' : ''}`,
          color: '#ffffff',
          fontSize: 12,
          borderRadius: 8,
          borderWidth: 0,
          bgColor: '#176B5D',
          padding: 6,
          display: 'ALWAYS'
        }
      });
    }

    let centerLat = destLat;
    let centerLon = destLon;
    let scale = 15;
    if (hasStaff) {
      centerLat = (destLat + staffLat) / 2;
      centerLon = (destLon + staffLon) / 2;
      const dist = tracking.distanceMeters || 1000;
      if (dist > 5000) scale = 12;
      else if (dist > 2000) scale = 13;
      else if (dist > 800) scale = 14;
      else scale = 16;
    }

    const polyline = hasStaff ? [{
      points: [
        { latitude: staffLat, longitude: staffLon },
        { latitude: destLat, longitude: destLon }
      ],
      color: '#176B5D',
      width: 4,
      dottedLine: true,
      arrowLine: true
    }] : [];

    this.setData({
      tracking,
      trackingDistanceText,
      hasTrackingLocation: true,
      markers,
      polyline,
      mapCenter: { latitude: centerLat, longitude: centerLon },
      mapScale: scale
    });
  },

  startTrackingPoll() {
    this.stopTrackingPoll();
    if (this._disposed || this._hidden) return;
    if (typeof wx === 'undefined' || typeof wx.createMapContext !== 'function') return;
    this._trackingTimer = setInterval(() => {
      if (!this._disposed && !this._hidden) {
        this.fetchTracking();
      }
    }, 15000);
    if (this._trackingTimer && typeof this._trackingTimer.unref === 'function') {
      this._trackingTimer.unref();
    }
  },

  stopTrackingPoll() {
    if (this._trackingTimer) {
      clearInterval(this._trackingTimer);
      this._trackingTimer = null;
    }
  },

  openNavigation() {
    const tracking = this.data.tracking;
    if (!tracking || !hasCoordinates(tracking.destLatitude, tracking.destLongitude)) {
      wx.showToast({ title: '暂无目的地坐标', icon: 'none' });
      return;
    }
    wx.openLocation({
      latitude: Number(tracking.destLatitude),
      longitude: Number(tracking.destLongitude),
      name: tracking.destAddress || '服务地址',
      address: tracking.destAddress || '',
      scale: 18
    });
  },

  callStaff() {
    const staffPhone = this.data.orderVO?.currentStaff?.user?.phone;
    if (staffPhone) {
      wx.makePhoneCall({ phoneNumber: staffPhone });
    }
  },

  previewFulfillmentPhoto(e) {
    const url = e.currentTarget.dataset.url;
    if (!url || typeof wx === 'undefined' || typeof wx.previewImage !== 'function') return;
    wx.previewImage({
      current: url,
      urls: this.data.fulfillmentPhotos || [url]
    });
  },

  cancelOrder() {
    this.setData({ cancelSheet: true });
  },

  closeCancelSheet() {
    this.setData({ cancelSheet: false });
  },

  submitCancel() {
    if (this._cancelling) return Promise.resolve();
    this._cancelling = true;
    this.setData({ cancelSheet: false });
    return request.post(`/orders/${this.data.orderId}/cancel`).then(() => {
      markOrdersChanged();
      wx.showToast({ title: '订单已取消', icon: 'success' });
      return this.loadDetail(this.data.orderId, true);
    }).catch(() => {}).finally(() => { this._cancelling = false; });
  },

  openPaySheet() {
    if (!this.data.orderVO?.canPay) return;
    this.setData({ paySheet: true });
  },

  closePaySheet() {
    if (!this.data.paying) this.setData({ paySheet: false });
  },

  // 老人本人或已审核绑定的家属支付；后端保证每单只付一次（重复返回 409）
  submitPay() {
    if (this.data.paying) return Promise.resolve();
    const orderId = this.data.orderId;
    this.setData({ paying: true });
    return request.post(`/orders/${orderId}/pay`).then(() => {
      if (this._disposed) return;
      markOrdersChanged();
      this.setData({ paySheet: false });
      wx.showToast({ title: '支付成功', icon: 'success' });
      return this.loadDetail(orderId, true);
    }).catch(() => {
      if (!this._disposed) this.setData({ paySheet: false });
      return this._disposed ? null : this.loadDetail(orderId, true);
    }).finally(() => { if (!this._disposed) this.setData({ paying: false }); });
  },

  confirmOrder() {
    this.setData({ confirmSheet: true });
  },

  closeConfirmSheet() {
    this.setData({ confirmSheet: false });
  },

  submitConfirm() {
    if (this._confirming) return Promise.resolve();
    this._confirming = true;
    lightTap();
    this.setData({ confirmSheet: false });
    return request.post('/fulfillment/confirm', { orderId: this.data.orderId, note: '老人/家属确认满意' }).then(() => {
      markOrdersChanged();
      wx.showToast({ title: '服务确认完成', icon: 'success' });
      return this.loadDetail(this.data.orderId, true);
    }).catch(() => {}).finally(() => { this._confirming = false; });
  },

  goToReview() {
    // 带上服务名、服务人员与完成时间，评价页据此告诉老人正在评价哪一笔
    const vo = this.data.orderVO || {};
    const ctx = {
      service: vo.order?.serviceNameSnapshot || '',
      staff: vo.currentStaff?.user?.realName || '',
      done: vo.serviceRecord?.actualEndAt || ''
    };
    const query = Object.keys(ctx).filter(k => ctx[k]).map(k => `&${k}=${encodeURIComponent(ctx[k])}`).join('');
    navigation.navigateTo({
      url: `/pages/order/confirm?orderId=${this.data.orderId}${query}`
    });
  },
  goToOrderFeedback() {
    const vo = this.data.orderVO;
    if (!vo || !vo.order) return;
    const orderId = vo.order.id;
    const serviceName = encodeURIComponent(vo.order.serviceNameSnapshot || '');
    navigation.navigateTo({
      url: `/pages/user/feedback?orderId=${orderId}&serviceName=${serviceName}`
    });
  }
});
