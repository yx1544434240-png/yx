const navigation = require('../../utils/navigation.js');
const request = require('../../utils/request.js');
const { getAppSafe } = require('../../utils/app-state.js');
const { sessionKey } = require('../../utils/list-state.js');
const { loadMedia } = require('../../utils/media-url.js');
const { uploadCredential } = require('../../utils/credential-upload.js');
const { friendlyTime } = require('../../utils/time-format.js');
const OPTIONS = [ { code: 'COOKING', label: '助餐烹饪' }, { code: 'CLEANING', label: '居家保洁' }, { code: 'ERRAND', label: '跑腿代办' }, { code: 'ESCORT', label: '助行陪办' }, { code: 'NURSING', label: '基础照护护理' } ];
const emptyForm = () => ({ id: null, revision: 0, skillIndex: 0, skillName: '', certificateNo: '' });
Page({
  data: { isElderMode: false, options: OPTIONS, form: emptyForm(), proofs: [], records: [], loading: true, loadError: false, uploading: false, submitting: false },
  onShow() {
    const app = getAppSafe();
    this.setData({ isElderMode: Boolean(app.globalData?.isElderMode) });
    if (!navigation.requireRole(['STAFF'], '/pages/staff/credentials')) return;
    return this.load();
  },
  onUnload() { this._disposed = true; },
  valid(context) { return !this._disposed && sessionKey(getAppSafe()) === context; },
  load() {
    const sequence = this._loadSeq = (this._loadSeq || 0) + 1;
    const context = sessionKey(getAppSafe());
    if (context !== this._context) { this._context = context; this.setData({ form: emptyForm(), proofs: [], records: [], uploading: false, submitting: false }); }
    this.setData({ loading: true, loadError: false });
    return request.get('/staff/my-profile').then(data => {
      if (!this.valid(context) || sequence !== this._loadSeq) return;
      const records = (data.skills || []).map(row => {
        let urls = []; try { const values = JSON.parse(row.proofUrlsJson || '[]'); if (Array.isArray(values)) urls = values; } catch (_) {}
        return { ...row, proofUrls: urls, statusText: { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已退回' }[row.auditStatus] || '待核实', submittedText: friendlyTime(row.submittedAt), auditText: friendlyTime(row.auditTime) };
      });
      this.setData({ records, loading: false });
    }).catch(() => { if (this.valid(context) && sequence === this._loadSeq) this.setData({ loading: false, loadError: true }); });
  },
  onSkillChange(e) { if (this.data.form.id) return; const index = Number(e.detail.value); if (OPTIONS[index]) this.setData({ 'form.skillIndex': index, 'form.skillName': `${OPTIONS[index].label}资质` }); },
  onInput(e) { const key = e.currentTarget.dataset.key; if (['skillName', 'certificateNo'].includes(key)) this.setData({ [`form.${key}`]: e.detail.value }); },
  resetForm() { if (!this.data.submitting && !this.data.uploading) this.setData({ form: emptyForm(), proofs: [] }); },
  async editRejected(e) {
    if (this.data.submitting || this.data.uploading) return;
    const row = this.data.records.find(item => String(item.id) === String(e.currentTarget.dataset.id));
    if (!row || row.auditStatus !== 'REJECTED') return;
    const context = this._context;
    const edit = this._editSeq = (this._editSeq || 0) + 1;
    this.setData({ form: { id: row.id, revision: row.revision || 0, skillIndex: Math.max(0, OPTIONS.findIndex(option => option.code === row.skillCode)), skillName: row.skillName, certificateNo: row.certificateNo }, proofs: row.proofUrls.map(url => ({ url, path: '' })) });
    const results = await Promise.allSettled(row.proofUrls.map(loadMedia));
    if (this.valid(context) && this._editSeq === edit && this.data.form.id === row.id) this.setData({ proofs: row.proofUrls.map((url, i) => ({ url, path: results[i].status === 'fulfilled' ? results[i].value : '' })) });
    if (typeof wx.pageScrollTo === 'function') wx.pageScrollTo({ scrollTop: 0, duration: 200 });
  },
  chooseProofs() {
    if (this.data.uploading || this.data.submitting || this.data.proofs.length >= 3) return;
    const context = this._context;
    wx.chooseMedia({ count: 3 - this.data.proofs.length, mediaType: ['image'], sourceType: ['album', 'camera'], sizeType: ['compressed'], success: async res => {
      if (!this.valid(context) || this.data.uploading || this.data.submitting) return;
      const files = (res.tempFiles || []).slice(0, 3 - this.data.proofs.length);
      if (files.some(file => file.size > 5 * 1024 * 1024)) { wx.showToast({ title: '每张证明图片不能超过5MB', icon: 'none' }); return; }
      this.setData({ uploading: true });
      for (const file of files) {
        try {
          const url = await uploadCredential(file.tempFilePath);
          if (!this.valid(context)) return;
          this.setData({ proofs: [...this.data.proofs, { url, path: file.tempFilePath }] });
        } catch (error) { if (this.valid(context)) wx.showToast({ title: error.message || '上传失败，请重试', icon: 'none' }); }
      }
      if (this.valid(context)) this.setData({ uploading: false });
    } });
  },
  removeProof(e) { if (!this.data.uploading && !this.data.submitting) this.setData({ proofs: this.data.proofs.filter((_, i) => i !== Number(e.currentTarget.dataset.index)) }); },
  previewProof(e) { const path = this.data.proofs[Number(e.currentTarget.dataset.index)]?.path; if (path) wx.previewImage({ current: path, urls: this.data.proofs.map(item => item.path).filter(Boolean) }); },
  async previewRecord(e) {
    const row = this.data.records.find(item => String(item.id) === String(e.currentTarget.dataset.id));
    if (!row?.proofUrls.length) return;
    const context = this._context;
    const results = await Promise.allSettled(row.proofUrls.map(loadMedia));
    if (!this.valid(context)) return;
    const urls = results.filter(result => result.status === 'fulfilled').map(result => result.value);
    if (urls.length) wx.previewImage({ current: urls[0], urls });
    else wx.showToast({ title: '材料暂时无法查看，请稍后重试', icon: 'none' });
  },
  submit() {
    if (this.data.submitting || this.data.uploading) return;
    if (!this.valid(this._context)) return this.load();
    const { form, proofs } = this.data;
    if (!form.skillName.trim() || !form.certificateNo.trim() || !proofs.length) { wx.showToast({ title: '请填写资质名称、证书编号并上传证明', icon: 'none' }); return; }
    const code = OPTIONS[form.skillIndex].code;
    if (!form.id && this.data.records.some(row => row.skillCode === code)) { wx.showToast({ title: '该技能已有记录，请查看或补充退回的申请', icon: 'none' }); return; }
    const context = this._context;
    this.setData({ submitting: true });
    return request.post('/staff/credentials/submit', { id: form.id || undefined, revision: form.revision, skillCode: code, skillName: form.skillName.trim(), certificateNo: form.certificateNo.trim(), proofUrls: proofs.map(item => item.url) }).then(() => {
      if (!this.valid(context)) return;
      this.setData({ form: emptyForm(), proofs: [] });
      wx.showToast({ title: '材料已提交，等待社区审核', icon: 'success' });
      return this.load();
    }).catch(() => {}).finally(() => { if (this.valid(context)) this.setData({ submitting: false }); });
  }
});
