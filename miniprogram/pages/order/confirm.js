const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { lightTap } = require('../../utils/feedback.js');
const { markOrdersChanged, sessionKey } = require('../../utils/list-state.js');
const { friendlyTime } = require('../../utils/time-format.js');
const { getAppSafe } = require('../../utils/app-state.js');
const { ISSUE_TAGS, tagsFor, draftKey } = require('../../utils/review-feedback.js');

Page({
  data: {
    orderId: null, reviewContext: null, rating: 0, content: '', selectedTags: [],
    tagOptions: [], issueOptions: ISSUE_TAGS, issueType: '', followUp: false,
    submitting: false, submitSuccess: false, isElderMode: false,
    loading: true, loadError: false, canReview: false, errorMessage: '', draftRestored: false,
    scoreDescMap: { 1: '非常不满意', 2: '不太满意', 3: '一般', 4: '满意', 5: '非常满意' }
  },
  onLoad(options = {}) {
    const id = String(options.orderId || '');
    this.setData({ orderId: /^\d+$/.test(id) ? id : null });
  },
  onShow() {
    const app = getAppSafe();
    this.setData({ isElderMode: Boolean(app.globalData?.isElderMode) });
    if (!navigation.requireLogin(`/pages/order/confirm?orderId=${this.data.orderId || ''}`)) return;
    return this.loadContext();
  },
  onHide() { this.saveDraft(); },
  onUnload() { this.saveDraft(); this._disposed = true; clearTimeout(this._draftTimer); },
  validContext(context) { return !this._disposed && sessionKey(getAppSafe()) === context; },
  loadContext() {
    const context = sessionKey(getAppSafe());
    if (!this.data.orderId) { this.setData({ loading: false, loadError: true, errorMessage: '缺少订单信息，请从订单详情进入' }); return Promise.resolve(); }
    if (this._context !== context) {
      this._submitted = false;
      this._context = context;
      this._restoredKey = null;
      this.setData({ rating: 0, content: '', selectedTags: [], issueType: '', followUp: false, submitSuccess: false, submitting: false, draftRestored: false, reviewContext: null });
    }
    this.setData({ loading: true, loadError: false, canReview: false });
    return request.get(`/orders/${this.data.orderId}`).then(vo => {
      if (!this.validContext(context)) return;
      if (!vo?.order) throw new Error('订单信息暂时无法获取');
      this.setData({ reviewContext: { service: vo.order.serviceNameSnapshot || '社区服务', staff: vo.currentStaff?.user?.realName || '', doneText: friendlyTime(vo.serviceRecord?.actualEndAt || '') } });
      if (vo.serviceReview) {
        this._submitted = true;
        this.clearDraft();
        this.setData({ submitSuccess: true, loading: false, followUp: (vo.feedbackRecords || []).some(record => String(record.complaintTitle || '').startsWith('[评价跟进]')) });
        return;
      }
      if (vo.order.status !== 'COMPLETED') throw new Error('服务完成并确认后，才能进行评价');
      this.restoreDraft();
      this.setData({ loading: false, canReview: true });
    }).catch(error => {
      if (this.validContext(context)) this.setData({ loading: false, loadError: true, errorMessage: error?.message || '服务信息加载失败，请重试' });
    });
  },
  updateTags(selected = this.data.selectedTags) {
    const labels = tagsFor(this.data.rating);
    const selectedTags = selected.filter(tag => labels.includes(tag));
    this.setData({ selectedTags, tagOptions: labels.map(label => ({ label, selected: selectedTags.includes(label) })) });
  },
  setRating(e) {
    if (this.data.submitting || this._submitted) return;
    const rating = Number(e.currentTarget.dataset.rating);
    if (!Number.isInteger(rating) || rating < 1 || rating > 5) return;
    this.setData({ rating, ...(rating > 2 ? { followUp: false, issueType: '' } : {}) });
    this.updateTags();
    try { lightTap(); } catch (_) {}
    this.queueDraft();
  },
  toggleTag(e) {
    if (this.data.submitting || this._submitted) return;
    const label = e.currentTarget.dataset.label;
    if (!tagsFor(this.data.rating).includes(label)) return;
    this.updateTags(this.data.selectedTags.includes(label) ? this.data.selectedTags.filter(tag => tag !== label) : [...this.data.selectedTags, label]);
    this.queueDraft();
  },
  onInputContent(e) { this.setData({ content: String(e.detail.value || '').slice(0, 200) }); this.queueDraft(); },
  onFollowUp(e) { this.setData({ followUp: Boolean(e.detail.value) && this.data.rating > 0 && this.data.rating <= 2 }); this.queueDraft(); },
  selectIssue(e) { if (ISSUE_TAGS.includes(e.currentTarget.dataset.label)) { this.setData({ issueType: e.currentTarget.dataset.label }); this.queueDraft(); } },
  queueDraft() { clearTimeout(this._draftTimer); this._draftTimer = setTimeout(() => this.saveDraft(), 400); },
  saveDraft() {
    clearTimeout(this._draftTimer);
    if (this._submitted || this.data.submitSuccess || (this._context && !this.validContext(this._context))) return;
    const key = draftKey(getAppSafe(), this.data.orderId);
    if (!key || typeof wx.setStorageSync !== 'function') return;
    try { wx.setStorageSync(key, { rating: this.data.rating, selectedTags: this.data.selectedTags, content: this.data.content, followUp: this.data.followUp, issueType: this.data.issueType, savedAt: Date.now() }); } catch (_) {}
  },
  restoreDraft() {
    const key = draftKey(getAppSafe(), this.data.orderId);
    if (!key || key === this._restoredKey || typeof wx.getStorageSync !== 'function') { this.updateTags(); return; }
    this._restoredKey = key;
    try {
      const draft = wx.getStorageSync(key);
      if (draft && Date.now() - draft.savedAt < 7 * 86400000) {
        const rating = Number.isInteger(draft.rating) && draft.rating >= 1 && draft.rating <= 5 ? draft.rating : 0;
        this.setData({ rating, content: String(draft.content || '').slice(0, 200), followUp: rating > 0 && rating <= 2 && Boolean(draft.followUp), issueType: ISSUE_TAGS.includes(draft.issueType) ? draft.issueType : '', draftRestored: true });
        this.updateTags(Array.isArray(draft.selectedTags) ? draft.selectedTags : []);
        return;
      }
    } catch (_) {}
    this.updateTags();
  },
  clearDraft() {
    clearTimeout(this._draftTimer);
    const key = draftKey(getAppSafe(), this.data.orderId);
    if (key && typeof wx.removeStorageSync === 'function') { try { wx.removeStorageSync(key); } catch (_) {} }
  },
  submitReview() {
    if (this.data.submitting || this._submitted) return;
    if (this._context && !this.validContext(this._context)) return this.loadContext();
    const { orderId, rating, content, followUp, issueType, selectedTags } = this.data;
    const tip = !orderId ? '请从订单详情进入评价' : !rating ? '请先选择本次服务的评分' : !this.data.canReview ? '请先加载并确认服务信息' : followUp && !issueType ? '请选择需要社区跟进的问题类型' : '';
    if (tip) { wx.showToast({ title: tip, icon: 'none' }); return; }
    const context = sessionKey(getAppSafe());
    this.saveDraft();
    this.setData({ submitting: true });
    return request.post('/fulfillment/review', { orderId, rating, content: content.trim(), tags: selectedTags.join(','), followUp, issueType: followUp ? issueType : '' }).then(() => {
      if (!this.validContext(context)) return;
      markOrdersChanged();
      this._submitted = true;
      this.clearDraft();
      this.setData({ submitSuccess: true, submitting: false });
      try { lightTap(); } catch (_) {}
    }).catch(error => {
      if (!this.validContext(context)) return;
      this.setData({ submitting: false });
      if (String(error?.message || '').includes('已完成评价')) return this.loadContext();
    });
  },
  returnToOrder() { navigation.redirectTo({ url: `/pages/order/detail?id=${this.data.orderId || ''}` }); },
  viewFeedback() { navigation.navigateTo({ url: `/pages/user/feedback?tab=1&orderId=${this.data.orderId}` }); }
});
