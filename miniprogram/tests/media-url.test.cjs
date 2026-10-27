const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('relative uploaded media follows a changed backend without changing stored values', () => {
  const app = { globalData: { baseUrl: 'http://10.0.1.2:8080/api/' } };
  const { mediaUrl } = createRuntime(app).require('utils/media-url');
  const stored = '/media/fallback/fulfillment/20261021/picture.jpg';
  assert.equal(mediaUrl(stored), 'http://10.0.1.2:8080/api' + stored);
  app.globalData.baseUrl = 'https://care.example/api';
  assert.equal(mediaUrl(stored), 'https://care.example/api' + stored);
  assert.equal(mediaUrl('https://images.example/picture.jpg'), 'https://images.example/picture.jpg');
  assert.equal(mediaUrl(null), '');
});

test('workbench preview expands relative photos while retaining raw paths for submission', () => {
  let preview;
  const runtime = createRuntime({ globalData: { baseUrl: 'https://care.example/api' } }, {
    previewImage(value) { preview = value; }
  });
  const page = runtime.page('staff/workbench');
  const raw = '/media/fallback/fulfillment/20261021/picture.jpg';
  page.setData({ recordPhotos: [raw] });
  page.previewRecordPhoto({ currentTarget: { dataset: { url: raw } } });
  assert.equal(preview.current, 'https://care.example/api' + raw);
  assert.deepEqual(Array.from(preview.urls), ['https://care.example/api' + raw]);
  assert.equal(page.data.recordPhotos[0], raw);
});

test('private download uses auth headers, resolves to a local image file and caches repeat loads', async () => {
  const app = { globalData: { token: 'staff-token', baseUrl: 'https://care.example/api' } };
  let headersCallback, success, request, downloads = 0;
  const runtime = createRuntime(app, { downloadFile(options) {
    downloads++;
    request = options; success = options.success;
    return { onHeadersReceived(callback) { headersCallback = callback; } };
  } });
  const { loadMedia } = runtime.require('utils/media-url');
  const pending = loadMedia('/media/private/fulfillment/1/2/photo.jpg');
  const duplicate = loadMedia('/media/private/fulfillment/1/2/photo.jpg');
  assert.equal(downloads, 1);
  assert.equal(request.header.Authorization, 'Bearer staff-token');
  assert.equal(request.url, 'https://care.example/api/media/private/fulfillment/1/2/photo.jpg');
  headersCallback({ header: { 'Content-Type': 'image/jpeg' } });
  success({ statusCode: 200, tempFilePath: 'wxfile://private-photo.jpg' });
  assert.equal(await pending, 'wxfile://private-photo.jpg');
  assert.equal(await duplicate, 'wxfile://private-photo.jpg');
  assert.equal(await loadMedia('/media/private/fulfillment/1/2/photo.jpg'), 'wxfile://private-photo.jpg');
  assert.equal(downloads, 1);
});

test('private download rejects JSON errors and deletes a late previous-account download', async () => {
  const app = { globalData: { token: 'old-token', baseUrl: 'https://care.example/api' } };
  let complete;
  const removed = [];
  const runtime = createRuntime(app, {
    downloadFile(options) { complete = options.success; },
    getFileSystemManager() { return { unlink({ filePath }) { removed.push(filePath); } }; }
  });
  const { loadMedia } = runtime.require('utils/media-url');
  const invalid = loadMedia('/media/private/fulfillment/1/2/a.jpg');
  complete({ statusCode: 200, header: { 'Content-Type': 'application/json' }, tempFilePath: 'wxfile://error' });
  await assert.rejects(invalid);
  const previous = loadMedia('/media/private/fulfillment/1/2/a.jpg');
  app.globalData.token = 'new-token';
  complete({ statusCode: 200, header: { 'Content-Type': 'image/jpeg' }, tempFilePath: 'wxfile://old-account-photo' });
  await assert.rejects(previous);
  assert.ok(removed.includes('wxfile://old-account-photo'));
});

test('logout clears private temporary images and unauthenticated downloads are refused', async () => {
  const app = { globalData: { token: 'token', baseUrl: 'https://care.example/api' } };
  const removed = [];
  const runtime = createRuntime(app, {
    downloadFile(options) { options.success({ statusCode: 200, header: { 'content-type': 'image/png' }, tempFilePath: 'wxfile://private' }); },
    getFileSystemManager() { return { unlink({ filePath }) { removed.push(filePath); } }; }
  });
  const media = runtime.require('utils/media-url');
  await media.loadMedia('/media/private/fulfillment/1/2/a.png');
  media.clearPrivateMedia();
  assert.deepEqual(removed, ['wxfile://private']);
  app.globalData.token = '';
  await assert.rejects(media.loadMedia('/media/private/fulfillment/1/2/a.png'));
});
