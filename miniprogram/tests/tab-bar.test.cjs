const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('tab state follows the real page route and synchronizes large text without layout queries', () => {
  const app = { globalData: { isElderMode: false } };
  let route = 'pages/service/list';
  const rt = createRuntime(app, {}, {}, { getCurrentPages: () => [{ route }] });
  const bar = rt.component('../custom-tab-bar', {});
  bar.lifecycle('lifetimes', 'attached');
  assert.equal(bar.data.selected, 1);
  route = 'pages/order/list'; app.globalData.isElderMode = true;
  bar.lifecycle('pageLifetimes', 'show');
  assert.equal(bar.data.selected, 2);
  assert.equal(bar.data.isElderMode, true);
  const count = bar.patches.length;
  bar.syncFromRoute();
  assert.equal(bar.patches.length, count, 'unchanged state should not cause another update');
});

test('tab navigation ignores repeat taps and restores selection when navigation fails', () => {
  const pending = [];
  const rt = createRuntime({ globalData: {} }, { switchTab: options => pending.push(options) });
  const bar = rt.component('../custom-tab-bar', {});
  const tap = index => ({ currentTarget: { dataset: { index } } });
  bar.switchTab(tap(0)); assert.equal(pending.length, 0);
  bar.switchTab(tap(1)); bar.switchTab(tap(2));
  assert.equal(pending.length, 1);
  assert.equal(pending[0].url, '/pages/service/list');
  pending[0].fail(); pending[0].complete();
  assert.equal(bar.data.selected, 0);
  bar.switchTab(tap(3));
  assert.equal(pending[1].url, '/pages/user/profile');
});

test('switching large text on the home updates the visible tab bar immediately', () => {
  const calls = [];
  const app = { globalData: { isElderMode: false }, toggleElderMode(value) { this.globalData.isElderMode = value; } };
  const page = createRuntime(app).page('index/index');
  page.getTabBar = () => ({ syncState: (index, enabled) => calls.push([index, enabled]) });
  page.toggleMode();
  assert.deepEqual(calls, [[0, true]]);
  assert.equal(page.data.isElderMode, true);
});
