const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

function runtime() {
  let bar;
  const timers = new Map();
  let timerId = 0;
  const rt = createRuntime({ globalData: {} }, {}, undefined, {
    getCurrentPages: () => [{ getTabBar: () => bar }],
    setTimeout(fn) { timers.set(++timerId, fn); return timerId; },
    clearTimeout(id) { timers.delete(id); },
  });
  bar = rt.component('../custom-tab-bar', {});
  return { rt, bar, finish() { const pending = [...timers.values()]; timers.clear(); pending.forEach(fn => fn()); } };
}

test('sheet hides the custom tab bar until its closing animation finishes', () => {
  const { rt, bar, finish } = runtime();
  const sheet = rt.component('bottom-sheet', { show: true });
  sheet.observe('show', true);
  assert.equal(bar.data.overlayHidden, true);
  sheet.onDismiss();
  assert.deepEqual(sheet.events, ['close']);
  sheet.observe('show', false);
  assert.equal(bar.data.overlayHidden, true);
  finish();
  assert.equal(sheet.data.rendered, false);
  assert.equal(bar.data.overlayHidden, false);
});

test('reopening or closing another sheet cannot reveal the tab bar early', () => {
  const { rt, bar, finish } = runtime();
  const first = rt.component('bottom-sheet', {});
  const second = rt.component('bottom-sheet', {});
  first.observe('show', true);
  second.observe('show', true);
  first.observe('show', false);
  finish();
  assert.equal(bar.data.overlayHidden, true);
  second.observe('show', false);
  second.observe('show', true);
  finish();
  assert.equal(bar.data.overlayHidden, true);
  second.lifecycle('lifetimes', 'detached');
  assert.equal(bar.data.overlayHidden, false);
});

test('hiding a tab page releases its overlay and showing it reapplies an open sheet', () => {
  const { rt, bar } = runtime();
  const sheet = rt.component('bottom-sheet', { show: true });
  sheet.observe('show', true);
  sheet.lifecycle('pageLifetimes', 'hide');
  assert.equal(bar.data.overlayHidden, false);
  sheet.lifecycle('pageLifetimes', 'show');
  assert.equal(bar.data.overlayHidden, true);
  sheet.onConfirm();
  assert.deepEqual(sheet.events, ['confirm']);
  sheet.lifecycle('lifetimes', 'detached');
  assert.equal(bar.data.overlayHidden, false);
});
