const test = require('node:test');
const assert = require('node:assert/strict');
const t = require('../utils/time-format.js');
const now = new Date(2026, 9, 22, 10, 23); // 2026-10-22 周四 10:23

test('friendlyTime uses 今天/明天 and weekday for other days', () => {
  assert.equal(t.friendlyTime('2026-10-22 09:00:00', now), '今天 09:00');
  assert.equal(t.friendlyTime('2026-10-23 14:00:00', now), '明天 14:00');
  assert.equal(t.friendlyTime('2026-10-25 09:00:00', now), '10月25日 周日 09:00');
  assert.equal(t.friendlyTime('2026-10-24T09:00:00', now), '10月24日 周六 09:00');
  assert.equal(t.friendlyTime('2026-10-21 18:30:00', now), '昨天 18:30');
  assert.equal(t.friendlyTime('2027-01-02 08:00:00', now), '2027年1月2日 周六 08:00');
});
test('friendlyTime tolerates empty and unparseable values', () => {
  assert.equal(t.friendlyTime(null, now), '');
  assert.equal(t.friendlyTime('', now), '');
  assert.equal(t.friendlyTime('稍后', now), '稍后');
});
test('friendlyRange collapses same-day ranges', () => {
  assert.equal(t.friendlyRange('2026-10-22 09:00:00', '2026-10-22 10:00:00', now), '今天 09:00–10:00');
  assert.equal(t.friendlyRange('2026-10-23 23:00:00', '2026-10-24 01:00:00', now), '明天 23:00 ~ 10月24日 周六 01:00');
  assert.equal(t.friendlyRange('2026-10-22 09:00:00', null, now), '今天 09:00');
});
test('bookingDays returns 今天/明天/后天 with backend date keys', () => {
  const days = t.bookingDays(new Date(2026, 9, 31, 9));
  assert.deepEqual(days.map(d => d.label), ['今天', '明天', '后天']);
  assert.deepEqual(days.map(d => d.date), ['2026-10-31', '2026-11-01', '2026-11-02']);
  assert.equal(days[1].sub, '11月1日 周日');
});
test('hourSlots disables past and too-soon slots', () => {
  const today = t.hourSlots('2026-10-22', now);
  assert.equal(today[0].time, '08:00');
  assert.equal(today.at(-1).time, '18:00');
  assert.equal(today.length, 11);
  assert.deepEqual(today.filter(s => !s.disabled).map(s => s.time)[0], '11:00');
  assert.ok(today.find(s => s.time === '10:00').disabled);
  assert.ok(t.hourSlots('2026-10-23', now).every(s => !s.disabled));
  assert.ok(t.hourSlots('2026-10-21', now).every(s => s.disabled));
});
