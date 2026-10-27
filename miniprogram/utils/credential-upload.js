const navigation = require('./navigation.js');
const { clearSession } = require('./session.js');
function uploadCredential(filePath) {
  const app = getApp();
  const { token, baseUrl } = app.globalData;
  if (!token) return Promise.reject(new Error('请先登录'));
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${baseUrl}/staff/credentials/upload`, filePath, name: 'file', timeout: 15000,
      header: { Authorization: `Bearer ${token}` },
      success(res) {
        if (app.globalData.token !== token || app.globalData.baseUrl !== baseUrl) return reject(new Error('登录或服务地址已变化，请重试'));
        let body = {};
        try { body = JSON.parse(res.data); } catch (_) { if (res.statusCode !== 401) return reject(new Error('上传响应异常，请重试')); }
        if (res.statusCode === 401 || body.code === 401) {
          clearSession(app);
          navigation.toLogin('/pages/staff/credentials');
          return reject(new Error('登录已过期，请重新登录'));
        }
        if (res.statusCode !== 200 || body.code !== 200 || !/^\/media\/private\/credentials\/\d+\/[a-f0-9-]{36}\.(jpg|jpeg|png|webp)$/.test(body.data?.url || ''))
          return reject(new Error(body.message || '图片上传失败'));
        resolve(body.data.url);
      },
      fail() { reject(new Error('图片上传失败，请检查网络后重试')); }
    });
  });
}
module.exports = { uploadCredential };
