const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRuntime, deferred } = require('./page-harness.cjs');
const read = f => fs.readFileSync(path.join(__dirname, '..', f), 'utf8');

test('S1: service card and its button both open the detail page; no direct booking', () => {
  const routes = [];
  const list = createRuntime({ globalData: {} }, { navigateTo: o => { routes.push(o.url); o.complete?.(); } }).page('service/list');
  assert.equal(list.quickBook, undefined);
  list.goToDetail({ currentTarget: { dataset: { id: 8 } } });
  assert.deepEqual(routes, ['/pages/service/detail?id=8']);
  const wxml = read('pages/service/list.wxml');
  assert.doesNotMatch(wxml, /quickBook|order\/create/);
  assert.match(wxml, /class="btn-primary book-button"[^>]*catchtap="goToDetail"/);
});

test('F1: family binding is two steps and keeps the existing apply payload', async () => {
  const toasts = []; const posts = [];
  const app = { globalData: { token: 't', baseUrl: 'b' } };
  const page = createRuntime(app, { showToast: o => toasts.push(o.title) }, {
    get: async url => (url.includes('search-elder') ? { id: 7, name: '钱桂芳', phone: '138****0007', community: '阳光花园社区' } : []),
    post: async (url, body) => { posts.push([url, body]); }
  }).page('user/family');
  assert.equal(page.data.bindStep, 1);
  page.confirmElder();
  assert.equal(page.data.bindStep, 1, 'cannot continue without an elder');
  page.onInputSearchPhone({ detail: { value: '13800000007' } });
  await page.searchElder();
  assert.equal(page.data.targetElder.name, '钱桂芳');
  page.confirmElder();
  assert.equal(page.data.bindStep, 2);
  page.backToFindElder();
  assert.equal(page.data.bindStep, 1);
  page.confirmElder();
  page.onInputRelation({ detail: { value: '母女' } });
  await page.submitApply();
  assert.deepEqual(JSON.parse(JSON.stringify(posts)), [['/family-bindings/apply', { elderId: 7, elderPhone: '13800000007', relation: '母女', proofMaterials: '' }]]);
  assert.equal(page.data.bindStep, 1);
  page.setData({ targetElder: { id: 7 }, bindStep: 2 });
  page.onInputSearchPhone({ detail: { value: '139' } });
  assert.equal(page.data.bindStep, 1, 'editing the phone sends you back to step 1');
  const wxml = read('pages/user/family.wxml');
  assert.match(wxml, /wx:if="\{\{bindStep === 1\}\}"[\s\S]*elder-confirm-card[\s\S]*bindtap="confirmElder"[\s\S]*<block wx:else>[\s\S]*bindtap="submitApply"/);
});

test('payment: detail shows pay button when canPay, pays once and reloads', async () => {
  const posts = []; let reloads = 0;
  const pending = deferred();
  const page = createRuntime({ globalData: {} }, { showToast() {} }, { post: url => { posts.push(url); return pending.promise; } }).page('order/detail');
  page.data.orderId = 5;
  page.data.orderVO = { canPay: true, order: { priceSnapshot: 30 } };
  page.loadDetail = async () => { reloads++; };
  page.openPaySheet();
  assert.equal(page.data.paySheet, true);
  const task = page.submitPay();
  await page.submitPay();
  assert.deepEqual([...posts], ['/orders/5/pay'], 'single flight');
  pending.resolve({});
  await task;
  assert.equal(page.data.paySheet, false);
  assert.equal(page.data.paying, false);
  assert.equal(reloads, 1);
  page.data.orderVO = { canPay: false, order: {} };
  page.openPaySheet();
  assert.equal(page.data.paySheet, false);
  const wxml = read('pages/order/detail.wxml');
  assert.match(wxml, /wx:if="\{\{orderVO\.canPay\}\}" bindtap="openPaySheet"/);
  assert.doesNotMatch(wxml + read('pages/order/create.wxml'), /线下结算|平台不扣款/);
});

test('payment: a rejected payment (403/409) closes the sheet and refreshes state', async () => {
  let reloads = 0;
  const page = createRuntime({ globalData: {} }, { showToast() {} }, { post: async () => { throw Error('409'); } }).page('order/detail');
  page.data.orderId = 5;
  page.data.orderVO = { canPay: true, order: {} };
  page.loadDetail = async () => { reloads++; };
  page.openPaySheet();
  await page.submitPay();
  assert.equal(page.data.paySheet, false);
  assert.equal(page.data.paying, false);
  assert.equal(reloads, 1);
});
