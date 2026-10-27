const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { getAppSafe } = require('../../utils/app-state.js');
const { sessionKey } = require('../../utils/list-state.js');
const { applySession } = require('../../utils/session.js');
Page({
  data: { isElderMode: false, loading: true, failed: false, application: null, statusText: '', isStaff: false, busy: false, form: { revision: 0, realName: '', phone: '', serviceArea: '阳光花园社区', introduction: '' } },
  onShow() {
    if (!navigation.requireRole(['RESIDENT', 'STAFF'], '/pages/staff/apply')) return;
    const state = getAppSafe().globalData;
    this.setData({ isElderMode: Boolean(state.isElderMode), isStaff: state.role === 'STAFF' });
    return this.load();
  },
  onUnload() { this._disposed = true; },
  valid(key) { return !this._disposed && key === sessionKey(getAppSafe()); },
  async load() {
    const key = sessionKey(getAppSafe());
    const seq = this._loadSeq = (this._loadSeq || 0) + 1;
    if (this._context !== key) {
      this._context = key;
      const user = getAppSafe().globalData.userInfo || {};
      this.setData({ application: null, form: { revision: 0, realName: user.realName || '', phone: user.phone || '', serviceArea: '阳光花园社区', introduction: '' } });
    }
    this.setData({ loading: true, failed: false });
    try {
      const row = await request.get('/staff/applications/mine');
      if (!this.valid(key) || seq !== this._loadSeq) return;
      const patch = { application: row || null, statusText: { PENDING: '等待社区审核', REJECTED: '请补充入驻资料', APPROVED: '入驻申请已通过' }[row?.status] || '', loading: false };
      if (row) patch.form = { revision: row.revision, realName: row.realName, phone: row.phone, serviceArea: row.serviceArea, introduction: row.introduction };
      this.setData(patch);
    } catch (_) { if (this.valid(key) && seq === this._loadSeq) this.setData({ loading: false, failed: true }); }
  },
  onInput(e) {
    const key = e.currentTarget.dataset.key;
    if (!this.data.busy && ['realName', 'phone', 'serviceArea', 'introduction'].includes(key)) this.setData({ [`form.${key}`]: e.detail.value });
  },
  goToProfile() { if (!this.data.busy) navigation.afterLogin('/pages/user/profile'); },
  async submit() {
    if (this.data.busy || !this.valid(this._context) || this.data.isStaff || (this.data.application && this.data.application.status !== 'REJECTED')) return;
    const form = Object.fromEntries(Object.entries(this.data.form).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]));
    if (!form.realName || !form.serviceArea || !form.introduction || !/^1[3-9]\d{9}$/.test(form.phone)) { wx.showToast({ title: '请完整填写资料及有效手机号码', icon: 'none' }); return; }
    const key = this._context;
    this.setData({ busy: true });
    try {
      await request.post('/staff/applications/submit', form);
      if (!this.valid(key)) return;
      wx.showToast({ title: '已提交，等待社区审核', icon: 'success' });
      await this.load();
    } catch (_) {} finally { if (this.valid(key)) this.setData({ busy: false }); }
  },
  async activate() {
    if (this.data.busy || !this.valid(this._context)) return;
    const key = this._context;
    this.setData({ busy: true });
    try {
      const result = await request.post('/auth/refresh');
      if (!this.valid(key)) return;
      if (result?.userInfo?.role !== 'STAFF') { wx.showToast({ title: '身份尚未开通，请刷新审核结果', icon: 'none' }); return; }
      applySession(getAppSafe(), result);
      navigation.afterLogin('/pages/staff/workbench');
    } catch (_) {} finally { if (!this._disposed) this.setData({ busy: false }); }
  }
});
