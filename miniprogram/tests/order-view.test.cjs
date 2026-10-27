const test = require('node:test');
const assert = require('node:assert/strict');
const { detailView } = require('../utils/order-view.js');

test('order detail view drops elder idCard and unknown fields', () => {
  const view = detailView({
    order: { id: 1, status: 'PAID' }, canPay: false,
    elder: { id: 2, name: '钱桂芳', idCard: '110101194611152345', phone: '139' },
    currentStaff: { user: { realName: '张护工', password: 'x', phone: '138' }, profile: { totalOrders: 3 } },
    secret: 'nope'
  });
  const json = JSON.stringify(view);
  assert.ok(!json.includes('idCard') && !json.includes('110101'));
  assert.ok(!json.includes('password') && !json.includes('secret'));
  assert.equal(view.elder.name, '钱桂芳');
  assert.equal(view.currentStaff.user.realName, '张护工');
  assert.equal(view.order.status, 'PAID');
});

test('missing nested parts stay null', () => {
  const view = detailView({ order: { id: 1 } });
  assert.equal(view.elder, null);
  assert.equal(view.currentStaff, null);
});

test('order status labels come from one shared module', () => {
  const { ORDER_STATUS_TEXT, ORDER_STATUS_TEXT_DETAIL, orderStatusText } = require('../utils/order-status.js');
  assert.equal(ORDER_STATUS_TEXT.WAIT_CONFIRM, '待确认');
  assert.equal(ORDER_STATUS_TEXT_DETAIL.WAIT_CONFIRM, '待确认完成');
  assert.equal(orderStatusText('CANCELLED'), '已取消');
  assert.equal(orderStatusText('NOPE'), '');
  const fs = require('node:fs'); const path = require('node:path');
  for (const page of ['index/index', 'order/list', 'order/detail', 'staff/workbench']) {
    const src = fs.readFileSync(path.join(__dirname, '..', 'pages', page + '.js'), 'utf8');
    assert.doesNotMatch(src, /WAIT_DISPATCH'?: '待派单'/, page + ' must not redefine status labels');
  }
});
