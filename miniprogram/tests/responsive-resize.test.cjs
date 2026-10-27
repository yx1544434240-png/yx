const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

for (const [pageName, indexKey] of [['service/list', 'currentCatIndex'], ['order/list', 'currentTabIndex']]) {
  test(`${pageName} recalculates swipe distance after tablet rotation or split screen`, () => {
    let width = 768;
    const page = createRuntime({ globalData: {} }, { getWindowInfo: () => ({ windowWidth: width }) }).page(pageName);
    page.data[indexKey] = 2;
    page.measurePage();
    page._gesturing = true;
    page._gestureIndex = 1;
    width = 1024;
    page.onResize();
    assert.equal(page._pageWidth, 1024);
    assert.equal(page._gesturing, false);
    assert.equal(page.data.sliderShift, 200);
    page.onSwiperTouchStart();
    page.onSwiperTransition({ detail: { dx: 512 } });
    assert.equal(page.data.sliderShift, 150);
  });
}
