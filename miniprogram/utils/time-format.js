// Elder-friendly time text. Backend sends "yyyy-MM-dd HH:mm:ss" (local time).
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const DAY_NAMES = ['今天', '明天', '后天'];
const pad = n => (n < 10 ? '0' + n : String(n));

function parseTime(value) {
  if (!value) return null;
  if (value instanceof Date) return Number.isFinite(value.getTime()) ? value : null;
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})(?:[ T](\d{1,2}):(\d{2})(?::(\d{2}))?)?/.exec(String(value));
  if (!m) return null;
  const date = new Date(+m[1], m[2] - 1, +m[3], +(m[4] || 0), +(m[5] || 0), +(m[6] || 0));
  return Number.isFinite(date.getTime()) ? date : null;
}
function dateKey(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
function dayOffset(date, now) {
  const a = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const b = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  return Math.round((a - b) / 86400000);
}
function clock(date) { return `${pad(date.getHours())}:${pad(date.getMinutes())}`; }
function dayText(date, now = new Date()) {
  const offset = dayOffset(date, now);
  if (offset === 0) return '今天';
  if (offset === 1) return '明天';
  if (offset === -1) return '昨天';
  const year = date.getFullYear() !== now.getFullYear() ? `${date.getFullYear()}年` : '';
  return `${year}${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAYS[date.getDay()]}`;
}
// "今天 09:00" / "明天 14:00" / "10月25日 周六 09:00"; unparseable input is returned unchanged.
function friendlyTime(value, now = new Date()) {
  const date = parseTime(value);
  if (!date) return value ? String(value) : '';
  return `${dayText(date, now)} ${clock(date)}`;
}
// "今天 09:00–10:00"; different days fall back to two full texts.
function friendlyRange(start, end, now = new Date()) {
  const a = parseTime(start); const b = parseTime(end);
  if (!a) return friendlyTime(start, now);
  if (!b) return friendlyTime(a, now);
  if (dateKey(a) === dateKey(b)) return `${friendlyTime(a, now)}–${clock(b)}`;
  return `${friendlyTime(a, now)} ~ ${friendlyTime(b, now)}`;
}
// Booking day buttons: 今天/明天/后天.
function bookingDays(now = new Date(), count = 3) {
  return Array.from({ length: count }, (_, i) => {
    const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + i);
    return { date: dateKey(date), label: DAY_NAMES[i] || dayText(date, now), sub: `${date.getMonth() + 1}月${date.getDate()}日 ${WEEKDAYS[date.getDay()]}` };
  });
}
// Hourly slots startHour..endHour inclusive; slots earlier than now + leadMinutes are disabled.
function hourSlots(dateStr, now = new Date(), { startHour = 8, endHour = 18, leadMinutes = 30 } = {}) {
  const day = parseTime(dateStr);
  const slots = [];
  for (let h = startHour; h <= endHour; h++) {
    const at = day ? new Date(day.getFullYear(), day.getMonth(), day.getDate(), h) : null;
    slots.push({ time: `${pad(h)}:00`, disabled: !at || at.getTime() < now.getTime() + leadMinutes * 60000 });
  }
  return slots;
}
module.exports = { parseTime, dateKey, friendlyTime, friendlyRange, bookingDays, hourSlots };
