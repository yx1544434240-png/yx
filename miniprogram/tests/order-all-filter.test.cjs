const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');
const rows = ['WAIT_MANUAL', 'COMPLETED', 'CANCELLED'].map((status, i) => ({ order: { id: String(i + 1), status, serviceNameSnapshot: '送餐' } }));
function setup(response = () => ({ records: rows })) {
  const queries = [];
  const app = { globalData: { token: 't', role: 'RESIDENT', baseUrl: 'http://localhost:8080/api' } };
  const page = createRuntime(app, {}, { get: async (_, query) => { queries.push(query); return response(query); } }).page('order/list');
  page.onLoad();
  return { page, queries };
}
test('all orders omits status and keeps active, completed and cancelled orders', async () => {
  const { page, queries } = setup(); await page.onShow();
  assert.equal(Object.hasOwn(queries[0], 'status'), false);
  assert.deepEqual(Array.from(page.data.tabData[0].list, row => row.order.status), ['WAIT_MANUAL', 'COMPLETED', 'CANCELLED']);
});
test('returning from a filtered empty page requests all orders without retaining status', async () => {
  const { page, queries } = setup(query => ({ records: query.status ? [] : rows }));
  await page.onShow();
  await page.onTabClick({ currentTarget: { dataset: { index: 2 } } });
  page.onAnimationFinish({ detail: { current: 2 } });
  assert.equal(queries.at(-1).status, 'WAIT_VISIT');
  await page.showAllOrders();
  page.onAnimationFinish({ detail: { current: 0 } });
  assert.equal(Object.hasOwn(queries.at(-1), 'status'), false);
  assert.equal(page.data.tabData[0].list.length, 3);
});
test('malformed order response is an error rather than a cached empty order list', async () => {
  const { page } = setup(() => ({ records: null }));
  await page.onShow();
  assert.equal(page.data.tabData[0].error, true);
  assert.equal(page.data.tabData[0].loaded, false);
});
