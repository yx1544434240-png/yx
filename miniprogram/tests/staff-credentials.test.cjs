const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
const proof = '/media/private/credentials/2/00000000-0000-0000-0000-000000000001.png';
function setup({ get = async () => ({ skills: [] }), post = async () => {}, upload = async () => proof } = {}) {
  const calls = [], toasts = [];
  const app = { globalData: { token: 'staff-token', role: 'STAFF', userInfo: { id: 2, role: 'STAFF' }, baseUrl: 'http://localhost:8080/api' } };
  const wx = { showToast: o => toasts.push(o.title), chooseMedia: o => { wx.choose = o; } };
  const runtime = createRuntime(app, wx, { get, post: (url, payload) => { calls.push({ url, payload }); return post(url, payload); } }, { __stubModules: { 'utils/credential-upload.js': { uploadCredential: upload } } });
  return { app, wx, calls, toasts, runtime, page: runtime.page('staff/credentials') };
}
function fill(page) { page.setData({ 'form.skillName': '护理培训', 'form.certificateNo': 'C-01', proofs: [{ url: proof, path: '/tmp/proof.png' }] }); }
test('credential form requires material and sends one pending application despite double tap', async () => {
  const pending = deferred(); const { page, calls, toasts } = setup({ post: () => pending.promise }); await page.onShow();
  page.submit(); assert.equal(calls.length, 0); assert.match(toasts.at(-1), /上传证明/);
  fill(page); const sent = page.submit(); page.submit(); assert.equal(calls.length, 1);
  assert.equal(calls[0].url, '/staff/credentials/submit');
  assert.equal(calls[0].payload.proofUrls[0], proof); assert.equal(Object.hasOwn(calls[0].payload, 'auditStatus'), false);
  pending.resolve(); await sent; assert.equal(page.data.submitting, false); assert.equal(page.data.proofs.length, 0);
});
test('failed submit keeps material and unlocks for retry', async () => {
  const { page, calls } = setup({ post: async () => { throw Error('offline'); } }); await page.onShow(); fill(page);
  await page.submit(); assert.equal(page.data.submitting, false); assert.equal(page.data.proofs.length, 1);
  assert.equal(page.data.form.certificateNo, 'C-01'); await page.submit(); assert.equal(calls.length, 2);
});
test('only rejected credentials are editable and resubmit includes current revision', async () => {
  const skills = [ { id: '11', skillCode: 'NURSING', skillName: '护理培训', certificateNo: 'C', revision: 3, auditStatus: 'REJECTED', proofUrlsJson: '[]' }, { id: '12', skillCode: 'COOKING', auditStatus: 'APPROVED' } ];
  const { page, calls } = setup({ get: async () => ({ skills }) }); await page.onShow();
  await page.editRejected({ currentTarget: { dataset: { id: '12' } } }); assert.equal(page.data.form.id, null);
  await page.editRejected({ currentTarget: { dataset: { id: '11' } } }); fill(page); await page.submit();
  assert.equal(calls[0].payload.id, '11'); assert.equal(calls[0].payload.revision, 3); assert.equal(calls[0].payload.skillCode, 'NURSING');
});
test('upload failure keeps successful photos and permits another upload', async () => {
  let count = 0; const { page, wx } = setup({ upload: async () => { if (++count === 2) throw Error('上传失败'); return proof; } }); await page.onShow();
  page.chooseProofs(); await wx.choose.success({ tempFiles: [{ tempFilePath: '/tmp/a.png', size: 10 }, { tempFilePath: '/tmp/b.png', size: 10 }] });
  assert.equal(page.data.proofs.length, 1); assert.equal(page.data.uploading, false);
});
test('late response from old account cannot populate credentials', async () => {
  const response = deferred(); const { page, app } = setup({ get: () => response.promise }); const shown = page.onShow();
  app.globalData.token = 'new-token'; response.resolve({ skills: [{ id: 'private' }] }); await shown;
  assert.equal(page.data.records.length, 0);
});
test('newest credential load wins over an earlier response', async () => {
  const first = deferred(), second = deferred(); let count = 0;
  const { page } = setup({ get: () => (++count === 1 ? first.promise : second.promise) }); const one = page.onShow(), two = page.load();
  second.resolve({ skills: [{ id: 'new' }] }); await two; first.resolve({ skills: [{ id: 'old' }] }); await one;
  assert.equal(page.data.records[0].id, 'new');
});
test('private upload rejects previous-session response without navigating or accepting URL', async () => {
  const app = { globalData: { token: 'one', baseUrl: 'http://localhost/api' } }; let options;
  const runtime = createRuntime(app, { uploadFile: o => { options = o; } });
  const upload = runtime.require('utils/credential-upload.js').uploadCredential('/tmp/a.png');
  app.globalData.token = 'two'; options.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: { url: proof } }) });
  await assert.rejects(upload, /已变化/);
});
