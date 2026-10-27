const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

test('user profile avatar upload triggers chooseMedia, wx.uploadFile and updates userInfo', async () => {
  let uploadedUrl = null;
  let uploadHeader = null;
  let postedAvatar = null;
  let showedToast = null;

  const mockApp = {
    globalData: {
      baseUrl: 'http://localhost:8080/api',
      token: 'jwt-token-123',
      userInfo: { id: 101, username: 'testuser', realName: '测试老人', role: 'RESIDENT', avatar: '' }
    }
  };

  const mockWx = {
    chooseMedia(opts) {
      opts.success({
        tempFiles: [{ tempFilePath: 'wxfile://tmp_avatar.png' }]
      });
    },
    uploadFile(opts) {
      uploadHeader = opts.header;
      // 模拟后台返回腾讯云 COS 上传成功响应
      const cosUrl = 'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/avatars/avatar_123.png';
      uploadedUrl = cosUrl;
      opts.success({
        statusCode: 200,
        data: JSON.stringify({
          code: 200,
          data: { url: cosUrl }
        })
      });
    },
    showLoading() {},
    hideLoading() {},
    showToast(opts) {
      showedToast = opts.title;
    },
    getStorageSync(key) {
      if (key === 'token') return 'jwt-token-123';
      return null;
    },
    setStorageSync() {}
  };

  const mockRequest = {
    get() { return Promise.resolve({}); },
    post(url, data) {
      if (url === '/users/avatar') {
        postedAvatar = data.avatar;
        return Promise.resolve(data.avatar);
      }
      return Promise.resolve({});
    }
  };

  const runtime = createRuntime(mockApp, mockWx, mockRequest);
  const page = runtime.page('user/profile');
  page.setData({ userInfo: mockApp.globalData.userInfo });

  page.chooseAndUploadAvatar();

  assert.equal(uploadHeader?.Authorization, 'Bearer jwt-token-123');
  assert.equal(uploadedUrl, 'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/avatars/avatar_123.png');
  assert.equal(postedAvatar, uploadedUrl);
  // 等待 microtask 触发 promise 回调
  await new Promise(r => setTimeout(r, 10));
  assert.equal(page.data.userInfo.avatar, uploadedUrl);
  assert.equal(mockApp.globalData.userInfo.avatar, uploadedUrl);
  assert.equal(showedToast, '头像更新成功');
});

test('staff workbench fulfillment photos upload, preview, delete and submit attachmentsJson', async () => {
  let submittedPayload = null;
  let previewUrls = null;

  const mockApp = {
    globalData: {
      baseUrl: 'http://localhost:8080/api',
      token: 'jwt-staff-token',
      userInfo: { id: 201, username: 'staff01', realName: '李师傅', role: 'STAFF' }
    }
  };

  const mockWx = {
    chooseMedia(opts) {
      opts.success({
        tempFiles: [
          { tempFilePath: 'wxfile://photo1.jpg' },
          { tempFilePath: 'wxfile://photo2.jpg' }
        ]
      });
    },
    uploadFile(opts) {
      const cosUrl = `https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/fulfillment/${opts.filePath.replace('wxfile://', '')}`;
      opts.success({
        statusCode: 200,
        data: JSON.stringify({
          code: 200,
          data: { url: cosUrl }
        })
      });
    },
    previewImage(opts) {
      previewUrls = opts.urls;
    },
    showLoading() {},
    hideLoading() {},
    showToast() {},
    getStorageSync(key) {
      if (key === 'token') return 'jwt-staff-token';
      return null;
    }
  };

  const mockRequest = {
    get() { return Promise.resolve({}); },
    post(url, data) {
      if (url === '/fulfillment/submit-record') {
        submittedPayload = data;
        return Promise.resolve();
      }
      return Promise.resolve();
    }
  };

  const runtime = createRuntime(mockApp, mockWx, mockRequest);
  const page = runtime.page('staff/workbench');

  // 打开提交记录抽屉
  page.openSubmitRecordModal({ currentTarget: { dataset: { id: 888 } } });
  assert.equal(page.data.showRecordModal, true);
  assert.equal(page.data.currentOrderId, 888);
  assert.deepEqual(page.data.recordPhotos, []);

  // 模拟拍摄/上传现场照片
  page.chooseRecordPhoto();
  await new Promise(r => setTimeout(r, 20));

  assert.equal(page.data.recordPhotos.length, 2);
  assert.ok(page.data.recordPhotos[0].includes('photo1.jpg'));
  assert.ok(page.data.recordPhotos[1].includes('photo2.jpg'));

  // 预览照片
  page.previewRecordPhoto({ currentTarget: { dataset: { url: page.data.recordPhotos[0] } } });
  assert.equal(previewUrls.length, 2);

  // 删除第二张照片
  page.deleteRecordPhoto({ currentTarget: { dataset: { index: 1 } } });
  assert.equal(page.data.recordPhotos.length, 1);

  // 填写工作总结并提交
  page.onInputSummary({ detail: { value: '今日按时为老人完成日常居室保洁与血压测量' } });
  await page.submitRecord();

  assert.ok(submittedPayload);
  assert.equal(submittedPayload.orderId, 888);
  assert.equal(submittedPayload.serviceSummary, '今日按时为老人完成日常居室保洁与血压测量');
  assert.ok(submittedPayload.attachmentsJson);
  const parsed = JSON.parse(submittedPayload.attachmentsJson);
  assert.equal(parsed.length, 1);
  assert.ok(parsed[0].includes('photo1.jpg'));
});

test('order detail correctly parses attachmentsJson and binds preview for fulfillment photos', async () => {
  let previewCurrent = null;

  const orderData = {
    order: { id: 666, status: 'WAIT_CONFIRM' },
    serviceRecord: {
      actualStartAt: '2026-10-21 14:00:00',
      actualEndAt: '2026-10-21 15:00:00',
      serviceSummary: '助餐照护完成',
      attachmentsJson: JSON.stringify([
        'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/fulfillment/p1.jpg',
        'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/fulfillment/p2.jpg'
      ])
    }
  };

  const mockWx = {
    previewImage(opts) {
      previewCurrent = opts.current;
    },
    showToast() {}
  };

  const mockRequest = {
    get(url) {
      if (url === '/orders/666') {
        return Promise.resolve(orderData);
      }
      return Promise.resolve({});
    }
  };

  const runtime = createRuntime({ globalData: { token: 't' } }, mockWx, mockRequest);
  const page = runtime.page('order/detail');

  await page.loadDetail(666, true);

  assert.equal(page.data.fulfillmentPhotos.length, 2);
  assert.equal(page.data.fulfillmentPhotos[0], 'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/fulfillment/p1.jpg');

  // 点击查看大图
  page.previewFulfillmentPhoto({ currentTarget: { dataset: { url: page.data.fulfillmentPhotos[1] } } });
  assert.equal(previewCurrent, 'https://test-bucket-1234567890.cos.ap-guangzhou.myqcloud.com/fulfillment/p2.jpg');
});
