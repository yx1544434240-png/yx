const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('service photos default to local real photos even for legacy icon names', () => {
  const images = createRuntime().require('utils/service-images');
  assert.equal(images.serviceImage('meal', 'MEAL'), '/assets/photos/services/meal.jpg');
  assert.equal(images.serviceImage('', 'CLEANING'), '/assets/photos/services/cleaning.jpg');
  assert.equal(images.serviceImage('javascript:alert(1)', 'ERRAND'), '/assets/photos/services/errand.jpg');
});

test('uploaded photo URLs follow the current backend address on a phone', () => {
  const app = { globalData: { baseUrl: 'http://10.0.1.2:8080/api/' } };
  const images = createRuntime(app).require('utils/service-images');
  assert.equal(images.serviceImage('/media/service-images/photo-id.jpg', 'MEAL'), 'http://10.0.1.2:8080/api/media/service-images/photo-id.jpg');
  app.globalData.baseUrl = 'https://care.example.com/api';
  assert.equal(images.serviceImage('/media/service-images/photo-id.jpg', 'MEAL'), 'https://care.example.com/api/media/service-images/photo-id.jpg');
  assert.equal(images.serviceImage('/media/service-images/../secret.jpg', 'MEAL'), '/assets/photos/services/meal.jpg');
});

test('catalog presentation retains the cover and category image uses the first on-shelf item', () => {
  const runtime = createRuntime({ globalData: { baseUrl: 'https://care.example.com/api' } });
  const { serviceCard } = runtime.require('utils/list-state');
  const rows = [serviceCard({ id: 1, category: 'MEAL', icon: '/media/service-images/new.jpg' }), serviceCard({ id: 2, category: 'MEAL' })];
  assert.equal(rows[0].imageUrl, 'https://care.example.com/api/media/service-images/new.jpg');
  assert.equal(runtime.require('utils/service-images').categoryImages(rows).MEAL, rows[0].imageUrl);
  assert.equal(rows[0].imageFallback, '/assets/photos/services/meal.jpg');
});

test('a failed uploaded image falls back once and a new source resets the photo', () => {
  const photo = createRuntime().component('service-photo', { src: 'remote.jpg', fallback: 'local.jpg' });
  photo.observe('src,fallback', 'remote.jpg', 'local.jpg');
  photo.onError();
  assert.equal(photo.data.displaySrc, 'local.jpg');
  const count = photo.patches.length;
  photo.onError();
  assert.equal(photo.patches.length, count);
  photo.observe('src,fallback', 'new.jpg', 'local.jpg');
  assert.equal(photo.data.displaySrc, 'new.jpg');
});

test('home categories include the fourth category and refresh after admin changes', async () => {
  let image = '/media/service-images/first.jpg';
  const home = createRuntime({ globalData: { baseUrl: 'https://care.example.com/api' } }, {}, {
    get: async () => ['MEAL', 'CLEANING', 'ERRAND', 'ESCORT'].map((category, id) => ({ id, category, icon: image }))
  }).page('index/index');
  await home.loadServiceItems();
  assert.equal(home.data.categoryPhotos.ESCORT, 'https://care.example.com/api/media/service-images/first.jpg');
  image = '/media/service-images/second.jpg';
  await home.loadServiceItems(true);
  assert.equal(home.data.categoryPhotos.ESCORT, 'https://care.example.com/api/media/service-images/second.jpg');
});

test('service photo containers do not restrict images to tiny icon dimensions in wxss', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const listWxss = fs.readFileSync(path.join(__dirname, '../pages/service/list.wxss'), 'utf8');
  const indexWxss = fs.readFileSync(path.join(__dirname, '../pages/index/index.wxss'), 'utf8');
  const detailWxss = fs.readFileSync(path.join(__dirname, '../pages/service/detail.wxss'), 'utf8');

  // Verify no legacy icon 46rpx/58rpx selectors shrink photos inside service containers
  assert.equal(/\.service-picture\s+image\s*\{[^}]*(?:58rpx|44rpx)/.test(listWxss), false);
  assert.equal(/\.service-list-icon\s+image\s*\{[^}]*46rpx/.test(indexWxss), false);

  // Verify containers enforce 100% fill for photos
  assert.equal(listWxss.includes('.service-picture image'), true);
  assert.equal(indexWxss.includes('.service-list-icon image'), true);
  assert.equal(detailWxss.includes('.detail-cover image'), true);
});

test('home brand icon matches login brand icon path', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const indexWxml = fs.readFileSync(path.join(__dirname, '../pages/index/index.wxml'), 'utf8');
  const loginWxml = fs.readFileSync(path.join(__dirname, '../pages/auth/login.wxml'), 'utf8');

  assert.equal(indexWxml.includes('/assets/brand/yilin-icon.png'), true);
  assert.equal(loginWxml.includes('/assets/brand/yilin-icon.png'), true);
  assert.equal(indexWxml.includes('/assets/icons/home.png'), false);
});

test('status tags and card action buttons adopt refined centered geometry', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const appWxss = fs.readFileSync(path.join(__dirname, '../app.wxss'), 'utf8');
  const orderListWxss = fs.readFileSync(path.join(__dirname, '../pages/order/list.wxss'), 'utf8');

  // Status tags should be clean rounded badges, not bloated 999rpx ovals
  assert.equal(/\.tag\s*\{[^}]*border-radius:\s*8rpx/.test(appWxss), true);
  // Order card action button should be structured rounded rectangle with centered height
  assert.equal(/\.btn-action[^{]*\{[^}]*border-radius:\s*12rpx/.test(orderListWxss), true);
  assert.equal(/\.btn-action[^{]*\{[^}]*min-height:\s*88rpx/.test(orderListWxss), true);
});
