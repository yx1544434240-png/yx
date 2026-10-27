const test = require('node:test');
const assert = require('node:assert/strict');
const { orderCard } = require('../utils/list-state.js');
const { createRuntime } = require('./page-harness.cjs');

test('orderCard correctly formats SELF vs FAMILY requester types and labels', () => {
  const selfOrder = {
    order: {
      id: 101,
      orderNo: 'ORD202610210001',
      status: 'WAIT_VISIT',
      serviceNameSnapshot: '助餐送餐',
      priceSnapshot: 15.00,
      plannedStartAt: '2026-10-22 11:30:00',
      requesterType: 'SELF'
    },
    requesterType: 'SELF',
    requesterTypeDesc: '老人自约',
    requesterName: '张建国',
    elder: { name: '张建国' }
  };

  const familyOrder = {
    order: {
      id: 102,
      orderNo: 'ORD202610210002',
      status: 'WAIT_DISPATCH',
      serviceNameSnapshot: '居室保洁',
      priceSnapshot: 60.00,
      plannedStartAt: '2026-10-22 14:00:00',
      requesterType: 'FAMILY'
    },
    requesterType: 'FAMILY',
    requesterTypeDesc: '家属代约',
    requesterName: '张小明',
    elder: { name: '张建国' }
  };

  const selfCard = orderCard(selfOrder);
  assert.equal(selfCard.order.requesterType, 'SELF');
  assert.equal(selfCard.requesterType, 'SELF');
  assert.equal(selfCard.requesterTypeDesc, '老人自约');
  assert.equal(selfCard.requesterName, '张建国');

  const familyCard = orderCard(familyOrder);
  assert.equal(familyCard.order.requesterType, 'FAMILY');
  assert.equal(familyCard.requesterType, 'FAMILY');
  assert.equal(familyCard.requesterTypeDesc, '家属代约');
  assert.equal(familyCard.requesterName, '张小明');
});

test('order/create prepares elder options with relationDesc and isSelf badge', async () => {
  const mockElders = [
    { id: 1, name: '李大爷', isSelf: true, relationDesc: '本人', community: '阳光花园', age: 78, address: '1号楼101' },
    { id: 2, name: '王奶奶', isSelf: false, relationDesc: '家属代约', community: '福邻小区', age: 82, address: '2号楼202' }
  ];

  const app = { globalData: { token: 'mock-token', role: 'RESIDENT', baseUrl: 'http://localhost:8080/api' } };
  const rt = createRuntime(app, {}, {
    get(url) {
      if (url === '/elders/my-list') return Promise.resolve(mockElders);
      return Promise.resolve({});
    }
  });

  const page = rt.page('order/create');
  await page.loadElders();

  assert.equal(page.data.elders.length, 2);
  assert.equal(page.data.elders[0].isSelf, true);
  assert.equal(page.data.elders[0].pickerLabel, '李大爷 · 本人 (阳光花园)');
  assert.equal(page.data.elders[1].isSelf, false);
  assert.equal(page.data.elders[1].pickerLabel, '王奶奶 · 家属代约 (福邻小区)');
  assert.equal(page.data.selectedElder.name, '李大爷');
});
