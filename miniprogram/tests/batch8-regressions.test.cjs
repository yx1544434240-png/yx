const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = f => fs.readFileSync(path.join(__dirname, '..', f), 'utf8');

test('server address entry is dev-only, like the role switch', () => {
  assert.match(read('pages/user/profile.wxml'), /wx:if="\{\{isDevMode\}\}"[^>]*bindtap="changeServerUrl"/);
});
test('confirm bottom bar keeps the safe-area inset', () => {
  assert.match(read('pages/order/confirm.wxss'), /\.confirm-bottom-bar \{[^}]*env\(safe-area-inset-bottom\)/);
});
test('no raw "null分" rating, mismatched button name or undefined age', () => {
  const detail = read('pages/order/detail.wxml');
  assert.doesNotMatch(detail, /\{\{orderVO\.currentStaff\.profile\.priorRating\}\}分/);
  assert.doesNotMatch(detail, /拨打电话/);
  assert.match(detail, /联系服务人员”/);
  assert.match(read('pages/staff/workbench.wxml'), /wx:if="\{\{pendingAttempt\.elder && pendingAttempt\.elder\.age != null\}\}"/);
});
test('raw timestamps are no longer rendered on listed pages', () => {
  for (const f of ['pages/index/index.wxml', 'pages/order/list.wxml', 'pages/order/detail.wxml', 'pages/staff/workbench.wxml', 'pages/user/family.wxml']) {
    assert.doesNotMatch(read(f).replace(/wx:if="[^"]*"/g, ""), /\{\{[^}]*\.(plannedStartAt|plannedEndAt|createdAt|actualStartAt|actualEndAt)\}\}/, f);
  }
});
test('hotline hours come from one place and no page claims 24 hours', () => {
  assert.equal(require('../utils/contact.js').HOTLINE_HOURS, '08:00–20:00');
  for (const f of ['pages/user/profile.wxml', 'pages/user/profile.js', 'pages/index/index.wxml', 'pages/index/index.js']) {
    assert.doesNotMatch(read(f), /24小时|88886666|08:00—20:00/, f);
  }
});
