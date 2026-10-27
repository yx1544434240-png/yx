const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { app } = require('../../utils/app-state.js');
const { friendlyTime } = require('../../utils/time-format.js');
const { FEEDBACK_STATUS } = require('../../utils/review-feedback.js');

const FEEDBACK_TYPES = ['服务态度', '准时履约', '照护质量', '费用结算', '平台功能', '其他建议'];

Page({
  data: {
    activeTab: 0,
    feedbackTypes: FEEDBACK_TYPES,
    selectedType: '服务态度',
    orderId: null,
    serviceName: '',
    orderPickerOptions: [
      { id: 0, label: '平台与社区整体建言', sub: '无需关联特定服务订单' }
    ],
    selectedOrderIndex: 0,
    title: '',
    content: '',
    submitting: false,
    submitSuccess: false,
    historyList: [],
    historyLoading: false,
    historyLoaded: false,
    isElderMode: false
  },

  onLoad(options = {}) {
    if (!app.globalData || !app.globalData.token) {
      navigation.toLogin('/pages/user/feedback');
      return;
    }

    const rawId = String(options.orderId || '');
    const orderId = /^\d+$/.test(rawId) ? (Number.isSafeInteger(Number(rawId)) ? Number(rawId) : rawId) : null;
    let serviceName = '';
    try { serviceName = options.serviceName ? decodeURIComponent(options.serviceName) : ''; } catch (_) {}

    this.setData({
      orderId,
      activeTab: options.tab === '1' ? 1 : 0,
      serviceName,
      isElderMode: Boolean(app.globalData && app.globalData.isElderMode)
    });

    this.loadRecentOrders(orderId, serviceName);
    this.fetchHistory();
  },

  onShow() {
    this.setData({
      isElderMode: Boolean(app.globalData && app.globalData.isElderMode)
    });
  },

  loadRecentOrders(initialOrderId, initialServiceName) {
    request.get('/orders/page', { pageNum: 1, pageSize: 15 }, { silent: true }).then(data => {
      const list = (data && data.records) || [];
      const options = [
        { id: 0, label: '平台与社区整体建言', sub: '无需关联特定服务订单' }
      ];

      let selectedIndex = 0;
      list.forEach((order, index) => {
        const id = order.id;
        const name = order.serviceNameSnapshot || '养老服务';
        const date = order.plannedStartText || order.plannedStartTime || '';
        options.push({
          id,
          label: `${name} (单号: #${id})`,
          sub: date ? `预约时间: ${date}` : ''
        });

        if (initialOrderId && String(initialOrderId) === String(id)) {
          selectedIndex = index + 1;
        }
      });

      // 如果带入的 orderId 不在最近订单中，手动塞一个选项
      if (initialOrderId && selectedIndex === 0) {
        options.push({
          id: initialOrderId,
          label: `${initialServiceName || '指定服务单据'} (单号: #${initialOrderId})`,
          sub: '当前关联单据'
        });
        selectedIndex = options.length - 1;
      }

      this.setData({
        orderPickerOptions: options,
        selectedOrderIndex: selectedIndex
      });
    }).catch(() => {});
  },

  switchTab(e) {
    const tab = Number(e.currentTarget.dataset.tab) || 0;
    this.setData({ activeTab: tab });
    if (tab === 1 && !this.data.historyLoaded) {
      this.fetchHistory();
    }
  },

  switchToForm() {
    this.setData({ activeTab: 0 });
  },

  selectType(e) {
    const type = e.currentTarget.dataset.type;
    this.setData({ selectedType: type });
  },

  onOrderPickerChange(e) {
    const index = Number(e.detail.value) || 0;
    const option = this.data.orderPickerOptions[index];
    this.setData({
      selectedOrderIndex: index,
      orderId: option && option.id > 0 ? option.id : null,
      serviceName: option && option.id > 0 ? option.label : ''
    });
  },

  onInputTitle(e) {
    this.setData({ title: e.detail.value || '' });
  },

  onInputContent(e) {
    this.setData({ content: e.detail.value || '' });
  },

  submitFeedback() {
    if (this.data.submitting) return;
    const title = (this.data.title || '').trim();
    const content = (this.data.content || '').trim();

    if (title.length < 2) {
      wx.showToast({ title: '请填写反馈主题（至少2个字）', icon: 'none' });
      return;
    }
    if (content.length < 5) {
      wx.showToast({ title: '请详细描述意见（至少5个字）', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });

    const payload = {
      orderId: this.data.orderId || 0,
      complaintTitle: `[${this.data.selectedType}] ${title}`,
      complaintContent: content
    };

    request.post('/fulfillment/complaint', payload).then(() => {
      this.setData({ submitting: false, submitSuccess: true });
      wx.showToast({ title: '反馈已送达社区', icon: 'success' });

      // 清空表单并延时切到历史记录
      setTimeout(() => {
        this.setData({
          title: '',
          content: '',
          submitSuccess: false,
          activeTab: 1
        });
        this.fetchHistory();
      }, 700);
    }).catch(err => {
      this.setData({ submitting: false });
      const msg = err && (err.message || err.errMsg) ? (err.message || err.errMsg) : '提交失败，请稍后重试';
      wx.showToast({ title: msg, icon: 'none' });
    });
  },

  fetchHistory() {
    this.setData({ historyLoading: true });
    request.get('/fulfillment/complaints/my', { pageNum: 1, pageSize: 50 }, { silent: true }).then(data => {
      const records = (data && data.records) || (Array.isArray(data) ? data : []);
      const formatted = records.map(item => ({
        ...item,
        statusText: FEEDBACK_STATUS[item.status] || '待核实',
        createdAtText: item.createdAt ? friendlyTime(item.createdAt) : '',
        handledAtText: item.handledAt ? friendlyTime(item.handledAt) : ''
      }));

      this.setData({
        historyList: formatted,
        historyLoading: false,
        historyLoaded: true
      });
    }).catch(() => {
      this.setData({
        historyLoading: false,
        historyLoaded: true
      });
    });
  }
});
