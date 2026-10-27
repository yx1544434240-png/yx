// 订单详情视图白名单：只把页面需要的字段放进 setData，避免身份证号等敏感数据进入视图层。
function pick(source, keys) {
  if (!source || typeof source !== 'object') return null;
  const out = {};
  keys.forEach(key => { if (source[key] !== undefined) out[key] = source[key]; });
  return out;
}

const TOP_KEYS = ['order', 'serviceRecord', 'serviceReview', 'logs', 'tracking', 'canPay', 'paid', 'payerName', 'requesterName', 'requesterTypeDesc', 'settlementNote'];
const ELDER_KEYS = ['id', 'name', 'age', 'community'];

function detailView(data) {
  const view = pick(data, TOP_KEYS) || {};
  view.feedbackRecords = Array.isArray(data?.feedbackRecords) ? data.feedbackRecords.map(record => pick(record, ['id', 'complaintTitle', 'status', 'statusText', 'createdAtText', 'handleResult', 'handledAtText'])) : [];
  view.elder = pick(data && data.elder, ELDER_KEYS);
  const staff = data && data.currentStaff;
  view.currentStaff = staff ? {
    user: pick(staff.user, ['id', 'realName', 'phone']),
    profile: pick(staff.profile, ['priorRating', 'totalOrders'])
  } : null;
  return view;
}

module.exports = { detailView, pick };
