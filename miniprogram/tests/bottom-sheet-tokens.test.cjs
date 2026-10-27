const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const dir = path.join(__dirname, '..', 'components', 'bottom-sheet');

test('bottom-sheet styles use design tokens instead of fixed sizes and raw colours', () => {
  const css = fs.readFileSync(path.join(dir, 'index.wxss'), 'utf8');
  assert.equal(css.match(/(?<!, )#[0-9a-fA-F]{6}\b/g), null, 'every hex colour sits behind a var() token');
  const phone = css.split('@media')[0];
  assert.equal(phone.match(/font-size:\s*\d+rpx/g), null, 'phone font sizes follow --font-* tokens (large-text mode)');
  assert.match(css, /\.elder-mode \.sheet-btn/);
});

test('scrollable sheet scrolls content and only drags from the handle area', () => {
  const wxml = fs.readFileSync(path.join(dir, 'index.wxml'), 'utf8');
  const scrollable = wxml.split('wx:else')[0];
  assert.match(scrollable, /<scroll-view class="sheet-scroll" scroll-y/);
  const panelTag = scrollable.slice(scrollable.indexOf('is-scrollable'), scrollable.indexOf('sheet-drag-area'));
  assert.doesNotMatch(panelTag, /touchmove/, 'panel does not swallow content touchmove');
  assert.match(scrollable, /sheet-drag-area" catchtouchmove="onMove"/);
  const css = fs.readFileSync(path.join(dir, 'index.wxss'), 'utf8');
  assert.match(css, /\.sheet-scroll \{\s*max-height: 60vh;/);
});
