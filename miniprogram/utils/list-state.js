// Only presentation fields cross the logic/view bridge.
const { DEFAULT_IMAGES, serviceImage } = require('./service-images.js');
const { friendlyTime, friendlyRange } = require('./time-format.js');
function serviceCard(item) {
  const { id, name, category, description, durationMinutes, serviceArea, price } = item;
  return { id, name, category, description, durationMinutes, serviceArea, price,
    imageUrl: serviceImage(item.icon, category), imageFallback: DEFAULT_IMAGES[category] || DEFAULT_IMAGES.MEAL };
}
function orderCard(item) {
  const order = item.order;
  const requesterType = order.requesterType || item.requesterType || 'SELF';
  return {
    viewId: String(order.id),
    order: {
      id: order.id, orderNo: order.orderNo, status: order.status,
      serviceNameSnapshot: order.serviceNameSnapshot,
      priceSnapshot: order.priceSnapshot, plannedStartAt: order.plannedStartAt,
      plannedStartText: friendlyTime(order.plannedStartAt),
      requesterType
    },
    requesterType,
    requesterTypeDesc: item.requesterTypeDesc || (requesterType === 'FAMILY' ? '家属代约' : '老人自约'),
    requesterName: item.requesterName || '',
    elder: item.elder ? { name: item.elder.name } : null,
    currentStaff: item.currentStaff ? { user: {
      realName: item.currentStaff.user.realName, phone: item.currentStaff.user.phone
    } } : null
  };
}
function taskCard(item) {
  const card = orderCard(item);
  const order = item.order;
  Object.assign(card.order, {
    addressSnapshot: order.addressSnapshot, plannedEndAt: order.plannedEndAt,
    latitude: order.latitude, longitude: order.longitude,
    contactPhoneSnapshot: order.contactPhoneSnapshot,
    durationMinutesSnapshot: order.durationMinutesSnapshot,
    plannedRangeText: friendlyRange(order.plannedStartAt, order.plannedEndAt)
  });
  if (item.elder) card.elder.age = item.elder.age;
  card.currentDispatch = item.currentDispatch ? {
    id: item.currentDispatch.id, status: item.currentDispatch.status,
    deadline: item.currentDispatch.deadline
  } : null;
  return card;
}
function visibleTabs(index, count, retain = []) {
  return Array.from({ length: count }, (_, i) => Math.abs(i - index) <= 1 || Boolean(retain[i]));
}
function sessionKey(app) {
  return `${app.globalData.baseUrl || ''}|${app.globalData.token || ''}`;
}
function markOrdersChanged() {
  const app = getApp();
  app.globalData.ordersVersion = (app.globalData.ordersVersion || 0) + 1;
}
module.exports = { serviceCard, orderCard, taskCard, visibleTabs, sessionKey, markOrdersChanged };
