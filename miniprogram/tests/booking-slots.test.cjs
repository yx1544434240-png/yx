const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

function pageAt(iso) {
  class Clock extends Date { constructor(...a) { super(...(a.length ? a : [iso])); } static now() { return new Date(iso).getTime(); } }
  const page = createRuntime(undefined, {}, undefined, { Date: Clock }).page('order/create');
  page.initDates();
  return page;
}

test('booking shows 今天/明天/后天 and hourly slots', () => {
  const page = pageAt('2026-10-22T10:23:00');
  assert.deepEqual(page.data.bookingDays.map(d => d.label), ['今天', '明天', '后天']);
  assert.equal(page.data.selectedDate, '2026-10-22');
  assert.equal(page.data.selectedTime, '11:00');
  assert.ok(page.data.timeSlots.find(s => s.time === '10:00').disabled);
});

test('past slots cannot be selected', () => {
  const page = pageAt('2026-10-22T10:23:00');
  page.onSlotTap({ currentTarget: { dataset: { time: '09:00', disabled: true } } });
  assert.equal(page.data.selectedTime, '11:00');
  page.onSlotTap({ currentTarget: { dataset: { time: '15:00', disabled: false } } });
  assert.equal(page.data.selectedTime, '15:00');
});

test('evening rolls over to tomorrow; switching day keeps a valid slot', () => {
  const page = pageAt('2026-10-22T17:50:00');
  assert.equal(page.data.selectedDate, '2026-10-23');
  assert.equal(page.data.selectedTime, '08:00');
  page.onSlotTap({ currentTarget: { dataset: { time: '09:00', disabled: false } } });
  page.onDayTap({ currentTarget: { dataset: { date: '2026-10-24' } } });
  assert.equal(page.data.selectedTime, '09:00');
  page.onDayTap({ currentTarget: { dataset: { date: '2026-10-22' } } });
  assert.equal(page.data.selectedTime, '', 'no slot left today, so nothing is preselected');
  page.onDateChange({ detail: { value: '2026-11-05' } });
  assert.equal(page.data.selectedDate, '2026-11-05');
  assert.equal(page.data.selectedTime, '08:00');
});
