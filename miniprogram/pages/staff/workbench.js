const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { mediaUrl, loadMedia } = require('../../utils/media-url.js');
const { lightTap } = require('../../utils/feedback.js');
const { syncTabBar } = require('../../utils/tab-bar.js');
const { hasCoordinates } = require('../../utils/coordinates.js');
const { taskCard, sessionKey, markOrdersChanged } = require('../../utils/list-state.js');
const { ORDER_STATUS_TEXT } = require('../../utils/order-status.js');
const { getAppSafe, app } = require('../../utils/app-state.js');

const TASK_TABS = [
  { key: '', label: '全部安排' },
  { key: 'WAIT_VISIT', label: '待上门' },
  { key: 'IN_SERVICE', label: '服务中' },
  { key: 'WAIT_CONFIRM,COMPLETED', label: '已完工' }
];

function draftStorageKey(orderId) {
  return orderId != null ? `workbench_record_draft_${orderId}` : '';
}

Page({
  data: {
    staffProfile: null,
    pendingAttempt: null,
    filteredTasks: [],
    tasksLoading: false,
    tasksHasMore: false,
    taskTabs: TASK_TABS,
    currentTaskTabIndex: 0,
    showRecordModal: false,
    currentOrderId: null,
    recordSummary: '',
    recordPhotos: [], recordPhotoSources: [],
    mediaBaseUrl: '',
    photoUploading: false,
    actionLoading: false,
    actionId: null,
    showStartSheet: false,
    startOrderId: null,
    startServiceAddress: '',
    showRejectSheet: false,
    rejectDispatchId: null,
    loadError: false,
    locationAuthDenied: false,
    isElderMode: false,
    statusTextMap: { ...ORDER_STATUS_TEXT },
    statusTagMap: {
      'WAIT_VISIT': 'tag-warning',
      'IN_SERVICE': 'tag-info',
      'WAIT_CONFIRM': 'tag-warning',
      'COMPLETED': 'tag-success'
    },
    isDevMode: false
  },

  onShow() {
    this._hidden = false;
    syncTabBar(this, 0);
    const app = getAppSafe();
    this.setData({ mediaBaseUrl: (app.globalData?.baseUrl || '').replace(/\/$/, '') });
    const isDev = /^https?:\/\/(localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01]))/i.test(app.globalData?.baseUrl || '');
    if (this.data.isDevMode !== isDev) this.setData({ isDevMode: isDev });
    const context = sessionKey(app);
    if (this._context !== context) {
      this._context = context;
      this._tasks = [];
      this._loadedAt = 0;
      this._staffPromise = null;
      this._tasksPromise = null;
      this._taskPage = 0;
      this.setData({ staffProfile: null, pendingAttempt: null, filteredTasks: [], tasksHasMore: false, tasksLoading: false, locationAuthDenied: false });
    }
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) this.setData({ isElderMode: isElder });
    if (!navigation.requireRole(['STAFF'], '/pages/staff/workbench')) {
      return Promise.resolve();
    }
    return this.loadStaffData().then(() => {
      if (this.data.staffProfile && !this._hidden && !this._disposed && sessionKey(getAppSafe()) === context) this.startLocationReporting();
    });
  },

  onHide() {
    this._hidden = true;
    this.stopLocationReporting();
  },

  onUnload() {
    this._disposed = true;
    this.stopLocationReporting();
  },

  onPullDownRefresh() {
    return this.loadStaffData(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() { return this.loadMoreTasks(); },

  loadMoreTasks() {
    if (!this.data.tasksHasMore || this._disposed) return Promise.resolve();
    return this.loadTasks(true).catch(() => {
      if (!this._disposed) this.setData({ loadError: true });
    });
  },

  changeTaskTab(e) {
    const index = Number(e.currentTarget.dataset.index);
    if (index === this.data.currentTaskTabIndex || !TASK_TABS[index]) return;
    this.filterTasks(index);
  },

  filterTasks(index) {
    const keys = TASK_TABS[index].key.split(',');
    const tasks = this._tasks || [];
    const filteredTasks = TASK_TABS[index].key ? tasks.filter(item => keys.includes(item.order.status)) : tasks;
    this.setData({ currentTaskTabIndex: index, filteredTasks });
  },

  loadStaffData(force = false) {
    if (this._staffPromise) return this._staffPromise;
    const version = app.globalData.ordersVersion || 0;
    if (!force && this._loadedVersion === version && this._loadedAt && Date.now() - this._loadedAt < 15000) return Promise.resolve();
    const context = sessionKey(app);
    this.setData({ loadError: false });
    const profileTask = request.get('/staff/my-profile').then(profile => {
      if (this._disposed || sessionKey(app) !== context) return;
      this.setData({
        staffProfile: {
          user: { realName: profile.user.realName },
          profile: {
            id: profile.profile.id,
            priorRating: profile.profile.priorRating,
            totalOrders: profile.profile.totalOrders,
            currentStatus: profile.profile.currentStatus,
            auditStatus: profile.profile.auditStatus
          }
        }
      });
    });

    const task = Promise.all([profileTask, this.loadTasks()]).then(() => {
      if (sessionKey(app) === context) {
        this._loadedAt = Date.now();
        this._loadedVersion = version;
      }
    }).catch(() => {
      if (!this._disposed && sessionKey(app) === context) this.setData({ loadError: true });
    }).finally(() => {
      if (this._staffPromise === task) this._staffPromise = null;
    });
    this._staffPromise = task;
    return task;
  },

  loadTasks(append = false) {
    if (this._tasksPromise) return this._tasksPromise;
    const context = sessionKey(app);
    const pageNum = append ? (this._taskPage || 1) + 1 : 1;
    this.setData({ tasksLoading: true });
    const task = request.get('/orders/page', { pageNum, pageSize: 20 }).then(data => {
      if (this._disposed || sessionKey(app) !== context) return;
      const records = (data?.records || []).map(taskCard);
      const pending = records.find(item => item.order.status === 'WAIT_ACCEPT' && item.currentDispatch?.status === 'PENDING');
      const previous = append ? (this._tasks || []) : [];
      const ids = new Set(previous.map(item => String(item.order.id)));
      const tasks = records.filter(item => ['WAIT_VISIT', 'IN_SERVICE', 'WAIT_CONFIRM', 'COMPLETED'].includes(item.order.status) && !ids.has(String(item.order.id)));
      this._tasks = previous.concat(tasks);
      this._taskPage = pageNum;
      this.setData({
        tasksHasMore: data?.total != null ? pageNum * 20 < Number(data.total) : records.length === 20,
        pendingAttempt: pending ? { attempt: pending.currentDispatch, order: pending.order, elder: pending.elder } : (append ? this.data.pendingAttempt : null)
      });
      this.filterTasks(this.data.currentTaskTabIndex);
    }).finally(() => {
      if (this._tasksPromise === task) {
        this._tasksPromise = null;
        if (!this._disposed && sessionKey(app) === context) this.setData({ tasksLoading: false });
      }
    });
    this._tasksPromise = task;
    return task;
  },

  onDispatchExpired() {
    return Promise.resolve(this._tasksPromise).catch(() => {}).then(() => {
      if (!this._hidden && !this._disposed) return this.loadTasks();
    }).catch(() => {
      if (!this._disposed) this.setData({ loadError: true });
    });
  },

  performAction(url, body, message, changesOrders = true, actionId = null) {
    if (this.data.actionLoading) return Promise.resolve();
    this.setData({ actionLoading: true, actionId });
    const context = sessionKey(getAppSafe());
    return request.post(url, body).then(() => {
      if (sessionKey(getAppSafe()) !== context) return;
      if (changesOrders) markOrdersChanged();
      if (this._disposed) return;
      try { lightTap(); } catch (_) {}
      wx.showToast({ title: message, icon: 'success' });
      this._clearRecordDraft(this.data.currentOrderId);
      this._resetRecordModal();
      this.closeStartSheet();
      this.closeRejectSheet();
      return Promise.all([this._staffPromise, this._tasksPromise].map(pending => Promise.resolve(pending).catch(() => {})))
        .then(() => {
          if (!this._disposed) return this.loadStaffData(true);
        });
    }).catch(() => {}).finally(() => {
      if (!this._disposed) this.setData({ actionLoading: false, actionId: null });
    });
  },

  changeStatus(e) {
    if (!this.data.staffProfile?.profile) return;
    const val = e?.detail?.value != null ? String(e.detail.value) : (e?.currentTarget?.dataset?.status === 'RESTING' ? '1' : '0');
    const newStatus = val === '1' ? 'RESTING' : 'AVAILABLE';
    const staffId = this.data.staffProfile.profile.id;
    return this.performAction(`/staff/${staffId}/status?status=${newStatus}`, undefined, '工作状态已更新', false);
  },

  changeStatusTo(e) {
    const targetStatus = e?.currentTarget?.dataset?.status;
    if (!targetStatus || targetStatus === this.data.staffProfile?.profile?.currentStatus) return;
    const staffId = this.data.staffProfile?.profile?.id;
    if (!staffId) return;
    return this.performAction(`/staff/${staffId}/status?status=${targetStatus}`, undefined, '工作状态已更新', false);
  },

  acceptDispatch(e) {
    const dispatchId = e?.currentTarget?.dataset?.id || e;
    return this.performAction(`/dispatch/accept/${dispatchId}`, undefined, '接单成功，请按时上门', true, dispatchId);
  },

  openRejectSheet(e) {
    const dispatchId = e?.currentTarget?.dataset?.id || e;
    this.setData({ showRejectSheet: true, rejectDispatchId: dispatchId });
  },

  closeRejectSheet() {
    this.setData({ showRejectSheet: false, rejectDispatchId: null });
  },

  confirmRejectDispatch() {
    const dispatchId = this.data.rejectDispatchId;
    if (!dispatchId) return;
    return this.performAction('/dispatch/reject', { dispatchId, reason: '人员当前工作安排冲突' }, '已拒单并释放预留', true, dispatchId);
  },

  rejectDispatch(e) {
    return this.openRejectSheet(e);
  },

  openStartService(e) {
    const orderId = e?.currentTarget?.dataset?.id || e;
    const task = (this.data.filteredTasks || []).find(t => String(t.order.id) === String(orderId));
    const addr = task?.order?.addressSnapshot || '老人居住住址';
    this.setData({
      showStartSheet: true,
      startOrderId: orderId,
      startServiceAddress: `您确认已到达【${addr}】，即将开始上门照护服务吗？`
    });
  },

  closeStartSheet() {
    this.setData({ showStartSheet: false, startOrderId: null });
  },

  confirmStartService() {
    const orderId = this.data.startOrderId;
    if (!orderId) return;
    return this.performAction(`/fulfillment/start/${orderId}`, undefined, '已签到，开始服务', true, orderId);
  },

  startService(e) {
    return this.openStartService(e);
  },

  _saveRecordDraft(orderId, summary, photos, photoSources) {
    const key = draftStorageKey(orderId);
    if (!key || typeof wx === 'undefined' || typeof wx.setStorageSync !== 'function') return;
    try {
      wx.setStorageSync(key, {
        summary: summary || '',
        photos: Array.isArray(photos) ? photos : [],
        photoSources: Array.isArray(photoSources) ? photoSources : []
      });
    } catch (_) {}
  },

  _loadRecordDraft(orderId) {
    const key = draftStorageKey(orderId);
    if (!key || typeof wx === 'undefined' || typeof wx.getStorageSync !== 'function') return null;
    try {
      const draft = wx.getStorageSync(key);
      if (draft && typeof draft === 'object' && (draft.summary || (Array.isArray(draft.photos) && draft.photos.length))) {
        return draft;
      }
    } catch (_) {}
    return null;
  },

  _clearRecordDraft(orderId) {
    const key = draftStorageKey(orderId);
    if (!key || typeof wx === 'undefined') return;
    try {
      if (typeof wx.removeStorageSync === 'function') wx.removeStorageSync(key);
      else if (typeof wx.setStorageSync === 'function') wx.setStorageSync(key, null);
    } catch (_) {}
  },

  _resetRecordModal() {
    this._recordSummary = '';
    this.setData({ showRecordModal: false, recordSummary: '', recordPhotos: [], recordPhotoSources: [], photoUploading: false });
  },

  openSubmitRecordModal(e) {
    const orderId = e?.currentTarget?.dataset?.id || e;
    const draft = this._loadRecordDraft(orderId);
    const summary = draft?.summary || '';
    const photos = Array.isArray(draft?.photos) ? draft.photos : [];
    const photoSources = Array.isArray(draft?.photoSources) ? draft.photoSources : [];
    this._recordSummary = summary;
    this.setData({
      showRecordModal: true,
      currentOrderId: orderId,
      recordSummary: summary,
      recordPhotos: photos,
      recordPhotoSources: photoSources,
      photoUploading: false
    });
  },

  closeRecordModal() {
    const summary = this._recordSummary || this.data.recordSummary;
    const hasPhotos = this.data.recordPhotos && this.data.recordPhotos.length > 0;
    const orderId = this.data.currentOrderId;
    if ((summary && summary.trim()) || hasPhotos) {
      const that = this;
      if (typeof wx !== 'undefined' && typeof wx.showModal === 'function') {
        wx.showModal({
          title: '放弃编辑',
          content: '已输入的服务记录与照片凭证尚未提交，确定要放弃吗？',
          confirmText: '放弃',
          cancelText: '继续写',
          confirmColor: '#b83a3a',
          success(res) {
            if (res.confirm) {
              that._clearRecordDraft(orderId);
              that._resetRecordModal();
            }
          }
        });
        return;
      }
    }
    this._clearRecordDraft(orderId);
    this._resetRecordModal();
  },

  onInputSummary(e) {
    this._recordSummary = e.detail.value;
    this._saveRecordDraft(this.data.currentOrderId, this._recordSummary, this.data.recordPhotos, this.data.recordPhotoSources);
  },

  chooseRecordPhoto() {
    if (this.data.photoUploading) return;
    const currentList = this.data.recordPhotos || [];
    const remaining = 4 - currentList.length;
    if (remaining <= 0) {
      if (typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
        wx.showToast({ title: '最多上传4张现场照片', icon: 'none' });
      }
      return;
    }

    const that = this;
    const app = getAppSafe();
    const baseUrl = app.globalData?.baseUrl || 'http://localhost:8080/api';
    const token = app.globalData?.token || (typeof wx !== 'undefined' && typeof wx.getStorageSync === 'function' ? wx.getStorageSync('token') : null);

    const uploadFilePromise = (filePath) => {
      return new Promise((resolve, reject) => {
        if (typeof wx === 'undefined' || typeof wx.uploadFile !== 'function') {
          resolve(filePath);
          return;
        }
        wx.uploadFile({
          url: `${baseUrl}/upload/image?type=fulfillment&orderId=${encodeURIComponent(that.data.currentOrderId)}`,
          filePath: filePath,
          name: 'file',
          header: {
            'Authorization': token ? `Bearer ${token}` : ''
          },
          success(res) {
            let data;
            try {
              data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
            } catch (e) {
              reject(new Error('解析响应失败'));
              return;
            }
            if (data && (data.code === 200 || data.code === 0) && data.data && data.data.url) {
              loadMedia(data.data.url).then(preview => resolve({ url: data.data.url, preview }), reject);
            } else {
              reject(new Error(data?.message || '上传失败'));
            }
          },
          fail: reject
        });
      });
    };

    const processFiles = (filePaths) => {
      if (!filePaths || filePaths.length === 0) return;
      that.setData({ photoUploading: true });
      if (typeof wx !== 'undefined' && typeof wx.showLoading === 'function') {
        wx.showLoading({ title: '正在上传图片...', mask: true });
      }

      const uploadTasks = filePaths.slice(0, remaining).map(p => uploadFilePromise(p));
      const context = sessionKey(getAppSafe());
      const orderId = that.data.currentOrderId;
      Promise.allSettled(uploadTasks).then(results => {
        if (context !== sessionKey(getAppSafe()) || orderId !== that.data.currentOrderId || !that.data.showRecordModal) return;
        if (typeof wx !== 'undefined' && typeof wx.hideLoading === 'function') {
          wx.hideLoading();
        }
        that.setData({ photoUploading: false });
        const newUrls = [];
        let failCount = 0;
        results.forEach(r => {
          if (r.status === 'fulfilled') {
            newUrls.push(r.value);
          } else {
            failCount++;
          }
        });
        if (newUrls.length > 0) {
          const updated = [...(that.data.recordPhotos || []), ...newUrls.map(item => item.url)];
          const updatedSources = [...(that.data.recordPhotoSources || []), ...newUrls.map(item => item.preview)];
          that.setData({ recordPhotos: updated, recordPhotoSources: updatedSources });
          that._saveRecordDraft(orderId, that._recordSummary || that.data.recordSummary, updated, updatedSources);
        }
        if (failCount > 0 && typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
          wx.showToast({ title: `${failCount}张图片上传失败，请重试`, icon: 'none' });
        }
      });
    };

    if (typeof wx !== 'undefined' && typeof wx.chooseMedia === 'function') {
      wx.chooseMedia({
        count: remaining,
        mediaType: ['image'],
        sourceType: ['camera', 'album'],
        sizeType: ['compressed'],
        success(res) {
          const paths = (res.tempFiles || []).map(f => f.tempFilePath);
          processFiles(paths);
        }
      });
    } else if (typeof wx !== 'undefined' && typeof wx.chooseImage === 'function') {
      wx.chooseImage({
        count: remaining,
        sizeType: ['compressed'],
        sourceType: ['camera', 'album'],
        success(res) {
          processFiles(res.tempFilePaths || []);
        }
      });
    }
  },

  deleteRecordPhoto(e) {
    const idx = e.currentTarget.dataset.index;
    const photos = [...(this.data.recordPhotos || [])];
    photos.splice(idx, 1);
    const previews = [...(this.data.recordPhotoSources || [])];
    previews.splice(idx, 1);
    this.setData({ recordPhotos: photos, recordPhotoSources: previews });
    this._saveRecordDraft(this.data.currentOrderId, this._recordSummary || this.data.recordSummary, photos, previews);
  },

  previewRecordPhoto(e) {
    const url = e.currentTarget.dataset.url;
    if (!url || typeof wx === 'undefined' || typeof wx.previewImage !== 'function') return;
    wx.previewImage({
      current: this.data.recordPhotoSources[this.data.recordPhotos.indexOf(url)] || mediaUrl(url),
      urls: this.data.recordPhotoSources.length ? this.data.recordPhotoSources : (this.data.recordPhotos || [url]).map(mediaUrl)
    });
  },

  submitRecord() {
    const { currentOrderId } = this.data;
    const recordSummary = this._recordSummary || this.data.recordSummary;
    if (!recordSummary.trim()) {
      if (typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
        wx.showToast({ title: '请填写服务情况说明', icon: 'none' });
      }
      return;
    }

    const pad = n => (n < 10 ? '0' + n : n);
    const now = new Date();
    const formatTime = d => `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;

    const attachmentsJson = this.data.recordPhotos && this.data.recordPhotos.length > 0
      ? JSON.stringify(this.data.recordPhotos)
      : null;

    return this.performAction('/fulfillment/submit-record', {
      orderId: currentOrderId,
      actualEndAt: formatTime(now),
      serviceSummary: recordSummary,
      attachmentsJson: attachmentsJson
    }, '服务记录已提交', true, currentOrderId);
  },

  dialPhone(e) {
    const phone = e.currentTarget.dataset.phone;
    if (phone) {
      wx.makePhoneCall({ phoneNumber: phone });
    }
  },

  openNavigation(e) {
    const orderId = e.currentTarget.dataset.id;
    const item = (this._tasks || []).find(t => String(t.order.id) === String(orderId));
    if (!item) return;
    if (!hasCoordinates(item.order.latitude, item.order.longitude)) {
      wx.showToast({ title: '暂无目的地坐标，请联系服务对象确认地址', icon: 'none' });
      return;
    }
    const lat = Number(item.order.latitude);
    const lon = Number(item.order.longitude);
    if (typeof wx !== 'undefined' && typeof wx.openLocation === 'function') {
      wx.openLocation({
        latitude: lat,
        longitude: lon,
        name: item.elder?.name ? `${item.elder.name}的住址` : '服务地址',
        address: item.order.addressSnapshot || item.elder?.address || '老人住址',
        scale: 18
      });
    }
  },

  mockMoveStep(e) {
    const orderId = e.currentTarget.dataset.id;
    if (!orderId) return;
    if (typeof wx !== 'undefined' && typeof wx.showLoading === 'function') {
      wx.showLoading({ title: '移动中...' });
    }
    request.post(`/staff/location/mock-move?orderId=${orderId}`).then(() => {
      if (typeof wx !== 'undefined' && typeof wx.hideLoading === 'function') {
        wx.hideLoading();
      }
      if (typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
        wx.showToast({ title: '已向目的地推进', icon: 'success' });
      }
    }).catch(err => {
      if (typeof wx !== 'undefined' && typeof wx.hideLoading === 'function') {
        wx.hideLoading();
      }
      if (typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
        wx.showToast({ title: err?.message || '模拟移动失败', icon: 'none' });
      }
    });
  },

  openLocationSetting() {
    if (typeof wx === 'undefined' || typeof wx.openSetting !== 'function') return;
    wx.openSetting({
      success: (res) => {
        if (res?.authSetting?.['scope.userLocation']) {
          this.setData({ locationAuthDenied: false });
          this.startLocationReporting();
        }
      }
    });
  },

  startLocationReporting() {
    this.stopLocationReporting();
    if (typeof wx === 'undefined' || typeof wx.getLocation !== 'function') return;
    this._locationTimer = setInterval(() => {
      if (!this._disposed && !this._hidden) {
        this.reportCurrentLocation();
      }
    }, 25000);
    if (this._locationTimer && typeof this._locationTimer.unref === 'function') {
      this._locationTimer.unref();
    }
    this.reportCurrentLocation();
  },

  stopLocationReporting() {
    if (this._locationTimer) {
      clearInterval(this._locationTimer);
      this._locationTimer = null;
    }
  },

  reportCurrentLocation() {
    if (this._disposed || this._hidden || this._locationPending) return;
    if (typeof wx === 'undefined' || typeof wx.getLocation !== 'function') return;
    const context = sessionKey(getAppSafe());
    this._locationPending = true;
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        if (this._disposed || this._hidden || sessionKey(getAppSafe()) !== context || !hasCoordinates(res.latitude, res.longitude)) return;
        if (this.data.locationAuthDenied) this.setData({ locationAuthDenied: false });
        request.post('/staff/location/report', {
          longitude: res.longitude,
          latitude: res.latitude,
          speed: Math.max(0, Number(res.speed) || 0)
        }).catch(() => {});
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || '';
        if (/auth deny|authorize no response|permission/i.test(msg)) {
          this.stopLocationReporting();
          if (!this._disposed && !this.data.locationAuthDenied) {
            this.setData({ locationAuthDenied: true });
          }
        }
      },
      complete: () => { this._locationPending = false; }
    });
  }
});
