// 订单状态文案的唯一来源。列表/首页/工作台用短文案，订单详情用稍长的说明文案。
const ORDER_STATUS_TEXT = Object.freeze({
  WAIT_DISPATCH: '待派单',
  WAIT_ACCEPT: '待接单',
  WAIT_VISIT: '待上门',
  IN_SERVICE: '服务中',
  WAIT_CONFIRM: '待确认',
  COMPLETED: '已完成',
  WAIT_MANUAL: '待人工处理',
  CANCELLED: '已取消'
});

const ORDER_STATUS_TEXT_DETAIL = Object.freeze({
  ...ORDER_STATUS_TEXT,
  WAIT_CONFIRM: '待确认完成',
  WAIT_MANUAL: '待人工协调'
});

function orderStatusText(status, detail) {
  return (detail ? ORDER_STATUS_TEXT_DETAIL : ORDER_STATUS_TEXT)[status] || '';
}

module.exports = { ORDER_STATUS_TEXT, ORDER_STATUS_TEXT_DETAIL, orderStatusText };
