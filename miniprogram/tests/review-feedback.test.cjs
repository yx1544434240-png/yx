const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime, deferred } = require('./page-harness.cjs');
const { draftKey } = require('../utils/review-feedback.js');
const done = { order: { status: 'COMPLETED', serviceNameSnapshot: '助餐送餐' } };
const event = (key, value) => ({ currentTarget: { dataset: { [key]: value } } });
function setup(request = { get: async () => structuredClone(done) }) {
  const app = { globalData: { token: 't', userInfo: { id: '11' }, baseUrl: 'http://localhost:8080/api' } };
  const storage = {};
  const wx = { showToast() {}, setStorageSync(k, v) { storage[k] = structuredClone(v); }, getStorageSync(k) { return storage[k]; }, removeStorageSync(k) { delete storage[k]; } };
  const page = createRuntime(app, wx, request).page('order/confirm');
  page.onLoad({ orderId: '9007199254740993123', service: '伪造服务名' });
  return { app, storage, page };
}

test('review requires an explicit rating and authoritative completed order context', async () => {
  let posts = 0;
  const { page } = setup({ get: async () => structuredClone(done), post: () => posts++ });
  await page.onShow();
  assert.equal(page.data.rating, 0);
  assert.equal(page.data.reviewContext.service, '助餐送餐');
  page.submitReview();
  assert.equal(posts, 0);
  page.onUnload();
});

test('low rating does not request follow-up without consent and high rating clears low-score choices', async () => {
  const { page } = setup(); await page.onShow();
  page.setRating(event('rating', 1));
  assert.equal(page.data.followUp, false);
  page.toggleTag(event('label', '上门迟到'));
  page.onFollowUp({ detail: { value: true } });
  page.selectIssue(event('label', '上门迟到'));
  page.setRating(event('rating', 5));
  assert.equal(page.data.followUp, false);
  assert.equal(page.data.issueType, '');
  assert.equal(page.data.selectedTags.length, 0);
  page.onUnload();
});

test('failure preserves feedback, retry submits once, success stays visible and clears draft', async () => {
  const pending = deferred(); let posts = 0; let payload;
  const { page, app, storage } = setup({ get: async () => structuredClone(done), post: (url, data) => { posts++; payload = data; return posts === 1 ? Promise.reject(new Error('offline')) : pending.promise; } });
  await page.onShow(); page.setRating(event('rating', 2));
  page.toggleTag(event('label', '上门迟到')); page.onInputContent({ detail: { value: '希望以后准时上门' } });
  page.onFollowUp({ detail: { value: true } }); page.selectIssue(event('label', '上门迟到'));
  await page.submitReview();
  assert.equal(page.data.submitting, false);
  assert.equal(page.data.content, '希望以后准时上门');
  assert(storage[draftKey(app, page.data.orderId)]);
  const second = page.submitReview(); page.submitReview();
  assert.equal(posts, 2);
  assert.equal(payload.followUp, true);
  assert.equal(payload.tags, '上门迟到');
  pending.resolve({}); await second;
  assert.equal(page.data.submitSuccess, true);
  assert.equal(storage[draftKey(app, page.data.orderId)], undefined);
  page.onUnload();
});

test('drafts restore within a session and never cross users, servers, or orders', async () => {
  const { page, app, storage } = setup(); await page.onShow();
  page.setRating(event('rating', 4)); page.onInputContent({ detail: { value: '未提交草稿' } }); page.onHide();
  const originalKey = draftKey(app, page.data.orderId);
  assert(storage[originalKey]);
  page.data.content = ''; page._restoredKey = null; page.restoreDraft();
  assert.equal(page.data.content, '未提交草稿');
  assert.notEqual(originalKey, draftKey(app, 'other'));
  app.globalData.userInfo.id = '22'; assert.notEqual(originalKey, draftKey(app, page.data.orderId));
  app.globalData.baseUrl = 'https://other/api'; assert.notEqual(originalKey, draftKey(app, page.data.orderId));
  page.onUnload();
});

test('late submit responses cannot clear another session draft or show a success state', async () => {
  const pending = deferred();
  const { page, app } = setup({ get: async () => structuredClone(done), post: () => pending.promise });
  await page.onShow(); page.setRating(event('rating', 5));
  const task = page.submitReview(); app.globalData.token = 'other';
  pending.resolve({}); await task;
  assert.equal(page.data.submitSuccess, false);
  page.onUnload();
});
