const navigation = require('../../utils/navigation.js');
const { lightTap } = require('../../utils/feedback.js');
const BOOKING_BEAT_MS = 420;
const request = require('../../utils/request.js');
const { getCachedService, loadService } = require('../../utils/service-catalog.js');
const { markOrdersChanged, sessionKey } = require('../../utils/list-state.js');
const { parseTime, dateKey, friendlyTime, bookingDays, hourSlots } = require('../../utils/time-format.js');
const { getAppSafe, app } = require('../../utils/app-state.js');

function formatFriendlyDate(dateStr) {
  const date = parseTime(dateStr);
  if (!date) return dateStr || '';
  return friendlyTime(date).replace(/ \d{2}:\d{2}$/, '');
}

Page({
  data: {
    serviceId: null,
    service: null,
    elders: [],
    eldersLoadError: false,
    elderIndex: -1,
    selectedElder: null,
    startDate: '',
    endDate: '',
    selectedDate: '',
    friendlyDateText: '',
    bookingDays: [],
    timeSlots: [],
    selectedTime: '09:00',
    address: '',
    latitude: null,
    longitude: null,
    contactName: '',
    contactPhone: '',
    remark: '',
    submitting: false,
    submitPhase: '',
    idempotencyKey: '',
    isElderMode: false,
    formErrors: {
      elder: '',
      time: '',
      address: '',
      contactName: '',
      contactPhone: ''
    }
  },

  onLoad(options) {
    // 先校验登录/角色再发请求（onShow 晚于 onLoad）
    const serviceId = (options && options.serviceId) || '';
    if (!navigation.requireRole(['RESIDENT'], `/pages/order/create?serviceId=${serviceId}`)) { this._denied = true; return; }
    if (options && options.serviceId) {
      this.setData({ serviceId: options.serviceId, service: getCachedService(options.serviceId) });
      this.loadService(options.serviceId);
    }
    this.initDates();
  },

  onShow() {
    const app = getAppSafe();
    const isElder = Boolean(app.globalData?.isElderMode);
    if (this.data.isElderMode !== isElder) this.setData({ isElderMode: isElder });
    if (this._denied) return;
    if (!navigation.requireRole(['RESIDENT'], `/pages/order/create?serviceId=${this.data.serviceId || ''}`)) return;
    return this.loadElders();
  },
  onUnload() { this._disposed = true; },

  initDates() {
    const now = new Date();
    const maxDate = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 30);
    const days = bookingDays(now);
    // 默认选中最近一个仍可预约的时段（今天约满则顺延到明天）
    const day = days.find(item => hourSlots(item.date, now).some(slot => !slot.disabled)) || days[1];
    this.setData({ startDate: days[0].date, endDate: dateKey(maxDate), bookingDays: days });
    this.selectDate(day.date, true);
  },

  // 切换日期时重算时段；已选时段若变得不可选则改为当天第一个可选时段
  selectDate(selectedDate, pickFirst) {
    const timeSlots = hourSlots(selectedDate, new Date());
    const keep = !pickFirst && timeSlots.some(slot => slot.time === this.data.selectedTime && !slot.disabled);
    const first = timeSlots.find(slot => !slot.disabled);
    this.setData({
      selectedDate,
      friendlyDateText: formatFriendlyDate(selectedDate),
      timeSlots,
      selectedTime: keep ? this.data.selectedTime : (first ? first.time : ''),
      'formErrors.time': ''
    });
  },

  loadService(id) {
    const context = app.globalData.baseUrl;
    return loadService(id).then(data => {
      if (!this._disposed && app.globalData.baseUrl === context) this.setData({ service: data });
    }).catch(() => {});
  },

  loadElders() {
    const context = sessionKey(getAppSafe());
    if (this._eldersJob?.context === context) return this._eldersJob.task;
    if (this._eldersContext !== context) {
      this._eldersContext = context;
      this.setData({ elders: [], selectedElder: null, elderIndex: -1, address: '', latitude: null, longitude: null, contactName: '', contactPhone: '' });
    }
    const job = { context };
    this._eldersJob = job;
    const valid = () => !this._disposed && sessionKey(getAppSafe()) === context && this._eldersJob === job;
    this.setData({ eldersLoadError: false });
    const task = request.get('/elders/my-list').then(data => {
      if (!valid()) return;
      const rawElders = data || [];
      const elders = rawElders.map(elder => ({
        ...elder,
        pickerLabel: `${elder.name || '老人'} · ${elder.relationDesc || (elder.isSelf ? '本人' : '家属代约')}${elder.community ? ` (${elder.community})` : ''}`
      }));
      this.setData({ elders });
      const selected = this.data.selectedElder;
      if (selected && !elders.some(elder => String(elder.id) === String(selected.id))) {
        this.setData({ selectedElder: null, elderIndex: -1, address: '', latitude: null, longitude: null, contactName: '', contactPhone: '' });
      } else if (selected) {
        this.setData({ elderIndex: elders.findIndex(elder => String(elder.id) === String(selected.id)) });
      }
      if (elders.length > 0 && !this.data.selectedElder) {
        this.selectElder(0);
      }
    }).catch(() => {
      if (valid()) this.setData({ eldersLoadError: true });
    }).finally(() => {
      if (this._eldersJob === job) this._eldersJob = null;
    });
    job.task = task;
    return task;
  },

  onElderChange(e) {
    const index = parseInt(e.detail.value, 10);
    this.selectElder(index);
    this.setData({ 'formErrors.elder': '' });
  },

  selectElder(index) {
    const elder = this.data.elders[index];
    if (elder) {
      this.setData({
        elderIndex: index,
        selectedElder: elder,
        address: elder.address || '',
        latitude: elder.latitude ?? null,
        longitude: elder.longitude ?? null,
        contactName: elder.emergencyContact || elder.name || '',
        contactPhone: String(elder.emergencyPhone || elder.phone || ''),
        'formErrors.elder': '',
        'formErrors.address': '',
        'formErrors.contactName': '',
        'formErrors.contactPhone': ''
      });
    }
  },

  onChooseLocation() {
    wx.chooseLocation({
      latitude: this.data.latitude ? Number(this.data.latitude) : undefined,
      longitude: this.data.longitude ? Number(this.data.longitude) : undefined,
      success: (res) => {
        const fullAddress = res.name ? (res.address ? `${res.address} (${res.name})` : res.name) : res.address;
        this.setData({
          address: fullAddress || this.data.address,
          latitude: res.latitude,
          longitude: res.longitude,
          'formErrors.address': ''
        });
      },
      fail: (err) => {
        if (err && err.errMsg && !err.errMsg.includes('cancel')) {
          wx.showToast({ title: '打开地图选点失败', icon: 'none' });
        }
      }
    });
  },

  onDayTap(e) {
    this.selectDate(e.currentTarget.dataset.date);
  },

  // 兜底：后天以后的日期仍用系统日期选择器
  onDateChange(e) {
    this.selectDate(e.detail.value);
  },

  onSlotTap(e) {
    const { time, disabled } = e.currentTarget.dataset;
    if (disabled) return;
    this.setData({ selectedTime: time, 'formErrors.time': '' });
  },

  onInputAddress(e) {
    this.setData({
      address: e.detail.value,
      latitude: null,
      longitude: null,
      'formErrors.address': ''
    });
  },

  onInputContactName(e) {
    this.setData({
      contactName: e.detail.value,
      'formErrors.contactName': ''
    });
  },

  onInputContactPhone(e) {
    this.setData({
      contactPhone: e.detail.value,
      'formErrors.contactPhone': ''
    });
  },

  onInputRemark(e) { this.setData({ remark: e.detail.value }); },

  goToAddElder() {
    navigation.navigateTo({
      url: '/pages/user/elder-form'
    });
  },

  getIdempotencyKey() {
    if (!this.data.idempotencyKey) {
      const key = `REQ_${Date.now()}_${Math.floor(Math.random() * 100000)}`;
      this.setData({ idempotencyKey: key });
      return key;
    }
    return this.data.idempotencyKey;
  },

  submitOrder() {
    if (this._createdOrderId) {
      navigation.redirectTo({ url: `/pages/order/detail?id=${this._createdOrderId}` });
      return;
    }
    if (this.data.submitting) {
      return;
    }

    const { selectedElder, service, selectedDate, selectedTime, address, latitude, longitude, contactName, contactPhone, remark } = this.data;

    if (!service) {
      wx.showToast({ title: '服务信息加载中，请稍后再试', icon: 'none' });
      return;
    }

    const formErrors = { elder: '', time: '', address: '', contactName: '', contactPhone: '' };
    let hasError = false;

    if (!selectedElder) {
      formErrors.elder = '请选择接受服务的老人';
      hasError = true;
    }
    if (!selectedDate || !selectedTime) {
      formErrors.time = '请选择预约上门时间';
      hasError = true;
    } else {
      const start = new Date(`${selectedDate.replace(/-/g, '/')} ${selectedTime}:00`);
      if (!Number.isFinite(start.getTime()) || start.getTime() < Date.now() + 30 * 60000) {
        formErrors.time = '预约时间需至少提前30分钟';
        hasError = true;
      }
    }
    if (!address || !address.trim()) {
      formErrors.address = '请填写详细上门服务地址';
      hasError = true;
    }
    if (!contactName || !contactName.trim()) {
      formErrors.contactName = '请填写联系人姓名';
      hasError = true;
    }
    if (!contactPhone || !contactPhone.trim()) {
      formErrors.contactPhone = '请填写联系电话';
      hasError = true;
    }

    if (hasError) {
      this.setData({ formErrors });
      const firstError = formErrors.elder || formErrors.time || formErrors.address || formErrors.contactName || formErrors.contactPhone;
      wx.showToast({ title: firstError, icon: 'none' });
      return;
    }

    const plannedStartAt = `${selectedDate} ${selectedTime}:00`;
    const idempotencyKey = this.getIdempotencyKey();

    lightTap();
    this.setData({ submitting: true });
    const context = sessionKey(getAppSafe());

    return request.post('/orders/create', {
      elderId: selectedElder.id,
      serviceItemId: service.id,
      plannedStartAt,
      address,
      latitude: latitude != null ? latitude : null,
      longitude: longitude != null ? longitude : null,
      coordinateType: 'GCJ02',
      contactName,
      contactPhone,
      remark,
      idempotencyKey
    }).then(order => {
      if (sessionKey(getAppSafe()) !== context) return;
      markOrdersChanged();
      this._createdOrderId = order.id;
      if (this._disposed) return;
      // A failed route can be retried without issuing another create request.
      let toastTitle = '预约提交成功';
      if (order.status === 'WAIT_ACCEPT') {
        toastTitle = '预约成功，系统已派单';
      } else if (order.status === 'WAIT_MANUAL') {
        toastTitle = '预约已提交，待社区人工派单';
      } else if (order.status === 'WAIT_DISPATCH') {
        toastTitle = '预约已提交，调度排队中';
      }
      this.setData({ submitPhase: 'done' });
      lightTap();
      return new Promise(resolve => {
        setTimeout(() => {
          if (!this._disposed) {
            wx.showToast({ title: toastTitle, icon: 'success' });
            navigation.redirectTo({
              url: `/pages/order/detail?id=${order.id}`,
              fail: () => { if (!this._disposed) this.setData({ submitting: false, submitPhase: '' }); }
            });
          }
          resolve();
        }, BOOKING_BEAT_MS);
      });
    }).catch(() => {
      if (!this._disposed) this.setData({ submitting: false, submitPhase: '' });
    });
  }
});
