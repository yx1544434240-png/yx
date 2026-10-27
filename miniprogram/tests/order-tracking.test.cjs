const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('tracking distance is formatted before reaching the template, including zero and kilometer boundaries', () => {
  const page = createRuntime().page('order/detail');
  for (const [distanceMeters, expected] of [[0, '0m'], [999, '999m'], [1000, '1.0km'], [1540, '1.5km'], ['2300', '2.3km']]) {
    const tracking = { distanceMeters, destLatitude: 39.9, destLongitude: 116.4, staffLatitude: 39.91, staffLongitude: 116.41 };
    page.updateMapState(tracking);
    assert.equal(page.data.trackingDistanceText, expected);
    assert.ok(page.data.markers[1].callout.content.includes(expected), 'map callout uses the same distance label');
    assert.equal(Object.hasOwn(tracking, 'distanceText'), false, 'API payload is not mutated');
  }
});

test('distance formatting also works without coordinates and clears when the next update has no distance', () => {
  const page = createRuntime().page('order/detail');
  page.updateMapState({ distanceMeters: 1500 });
  assert.equal(page.data.trackingDistanceText, '1.5km');
  page.updateMapState({});
  assert.equal(page.data.trackingDistanceText, '');
  page.updateMapState({ distanceMeters: null });
  assert.equal(page.data.trackingDistanceText, '');
});
