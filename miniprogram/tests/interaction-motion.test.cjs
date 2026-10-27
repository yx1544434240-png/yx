const test = require('node:test');
const assert = require('node:assert/strict');
const { sliderShift } = require('../utils/slider-shift.js');
const { sheetDelta, sheetShouldClose } = require('../utils/sheet-gesture.js');

test('category indicator follows the finger and rubber-bands past the ends', () => {
  assert.equal(sliderShift(1, -187.5, 375, 5), 1.5);
  assert.equal(sliderShift(0, 100, 375, 5), -100 / 375 * 0.35);
  const end = sliderShift(4, -375, 375, 5);
  assert.ok(end > 4 && end < 5);
});

test('a sheet closes only after distance or a fast downward flick', () => {
  assert.equal(sheetDelta(100, 80), 0);
  assert.equal(sheetDelta(100, 160), 60);
  assert.equal(sheetShouldClose(60, 400, 400), false);
  assert.equal(sheetShouldClose(130, 400, 400), true);
  assert.equal(sheetShouldClose(40, 40, 400), true);
});
