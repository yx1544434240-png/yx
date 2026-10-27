// 存储相对路径，展示时跟随当前 API 地址，避免更换局域网 IP 后图片失效。
function mediaUrl(value) {
  if (typeof value !== 'string') return '';
  if (!value.startsWith('/media/')) return value;
  let base = '';
  try { base = getApp()?.globalData?.baseUrl || ''; } catch (_) {}
  return base.replace(/\/$/, '') + value;
}
const privateFiles = new Set();
const mediaCache = new Map();
function removePrivateFile(path) {
  try { wx.getFileSystemManager?.().unlink({ filePath: path, fail() {} }); } catch (_) {}
}
function clearPrivateMedia() {
  for (const path of privateFiles) removePrivateFile(path);
  privateFiles.clear();
  mediaCache.clear();
}
function loadMedia(value) {
  if (typeof value !== 'string') return Promise.reject(new Error('图片地址无效'));
  if (!value.startsWith('/media/private/')) return Promise.resolve(mediaUrl(value));
  let app;
  try { app = getApp(); } catch (_) {}
  const token = app?.globalData?.token;
  const base = app?.globalData?.baseUrl;
  if (!token || typeof wx.downloadFile !== 'function') return Promise.reject(new Error('请登录后查看照片'));
  const cacheKey = `${base || ''}|${token}|${value}`;
  if (mediaCache.has(cacheKey)) return mediaCache.get(cacheKey);
  const pending = new Promise((resolve, reject) => {
    let receivedType = '';
    const task = wx.downloadFile({
      url: mediaUrl(value), timeout: 15000, header: { Authorization: `Bearer ${token}` },
      success(res) {
        const type = receivedType || res.header?.['Content-Type'] || res.header?.['content-type'] || '';
        if (app.globalData.token !== token || app.globalData.baseUrl !== base) {
          if (res.tempFilePath) removePrivateFile(res.tempFilePath);
          mediaCache.delete(cacheKey);
          return reject(new Error('登录状态已变化'));
        }
        if (res.statusCode !== 200 || !type.startsWith('image/') || !res.tempFilePath) {
          mediaCache.delete(cacheKey);
          return reject(new Error('无权查看照片或照片加载失败'));
        }
        privateFiles.add(res.tempFilePath);
        resolve(res.tempFilePath);
      },
      fail(err) {
        mediaCache.delete(cacheKey);
        reject(err);
      }
    });
    task?.onHeadersReceived?.(({ header }) => {
      receivedType = header?.['Content-Type'] || header?.['content-type'] || '';
    });
  });
  mediaCache.set(cacheKey, pending);
  return pending;
}
module.exports = { mediaUrl, loadMedia, clearPrivateMedia };
