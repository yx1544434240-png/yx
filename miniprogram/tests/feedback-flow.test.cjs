const test = require('node:test');
const assert = require('node:assert/strict');
const { createRuntime } = require('./page-harness.cjs');

function setupApp(token = 'valid-token', role = 'RESIDENT') {
  return {
    globalData: {
      baseUrl: 'http://localhost:8080/api',
      token,
      role,
      userInfo: token ? { id: 101, username: 'testuser', role } : null,
      isElderMode: false
    }
  };
}

test('profile page provides feedback entry that guards unauthenticated users and navigates when logged in', () => {
  let navTarget = null;
  const wx = {
    navigateTo: ({ url }) => { navTarget = url; },
    getStorageSync: () => null,
    showToast: () => {}
  };

  // 1. Unauthenticated user
  const unauthApp = setupApp('');
  const unauthPage = createRuntime(unauthApp, wx).page('user/profile');
  unauthPage.goToFeedback();
  assert.equal(navTarget, '/pages/auth/login?redirect=' + encodeURIComponent('/pages/user/feedback'));

  // 2. Logged in user
  const authApp = setupApp('token-123');
  const authPage = createRuntime(authApp, wx).page('user/profile');
  authPage.onShow();
  authPage.goToFeedback();
  assert.equal(navTarget, '/pages/user/feedback');
});

test('order detail provides feedback entry with pre-filled order context', () => {
  let navTarget = null;
  const wx = {
    navigateTo: ({ url }) => { navTarget = url; },
    getStorageSync: () => null
  };
  const app = setupApp('token-123');
  const page = createRuntime(app, wx).page('order/detail');
  page.setData({
    orderVO: {
      order: { id: 8899, serviceNameSnapshot: '助餐送餐' }
    }
  });

  page.goToOrderFeedback();
  assert.equal(navTarget, `/pages/user/feedback?orderId=8899&serviceName=${encodeURIComponent('助餐送餐')}`);
});

test('feedback page validates input length and submits complaint/suggestion to backend', async () => {
  const posts = [];
  const gets = [];
  let toastMsg = null;
  const wx = {
    showToast: ({ title }) => { toastMsg = title; },
    getStorageSync: () => null
  };
  const request = {
    get: (url, params) => {
      gets.push({ url, params });
      if (url === '/orders/page') {
        return Promise.resolve({ records: [{ id: 8899, serviceNameSnapshot: '助餐送餐' }] });
      }
      if (url === '/fulfillment/complaints/my') {
        return Promise.resolve({
          records: [
            {
              id: 1,
              complaintTitle: '[服务态度] 送餐员态度亲切',
              complaintContent: '提前电话确认很周到',
              status: 'RESOLVED',
              handleResult: '已向当值员工传达表扬',
              createdAt: '2026-10-22 10:00:00',
              handledAt: '2026-10-22 11:00:00'
            }
          ]
        });
      }
      return Promise.resolve({ records: [] });
    },
    post: (url, payload) => {
      posts.push({ url, payload });
      return Promise.resolve();
    }
  };

  const app = setupApp('token-123');
  const page = createRuntime(app, wx, request).page('user/feedback');

  // Load with order context
  page.onLoad({ orderId: '8899', serviceName: encodeURIComponent('助餐送餐') });
  assert.equal(page.data.orderId, 8899);

  // Validation: empty title
  page.setData({ title: ' ', content: '服务非常耐心' });
  page.submitFeedback();
  assert.equal(toastMsg.includes('请填写反馈主题'), true);
  assert.equal(posts.length, 0);

  // Validation: short content
  page.setData({ title: '准时送达', content: '好' });
  page.submitFeedback();
  assert.equal(toastMsg.includes('请详细描述意见'), true);
  assert.equal(posts.length, 0);

  // Successful submission
  page.setData({ title: '助餐时间十分准时', content: '饭菜温热可口，老人很满意' });
  page.submitFeedback();
  assert.equal(posts.length, 1);
  assert.equal(posts[0].url, '/fulfillment/complaint');
  assert.equal(posts[0].payload.orderId, 8899);
  assert.equal(posts[0].payload.complaintTitle, '[服务态度] 助餐时间十分准时');
  assert.equal(posts[0].payload.complaintContent, '饭菜温热可口，老人很满意');

  // Switch tab and load history
  page.switchTab({ currentTarget: { dataset: { tab: '1' } } });
  assert.equal(page.data.activeTab, 1);
  await new Promise(r => setTimeout(r, 10));
  assert.equal(page.data.historyList.length, 1);
  assert.equal(page.data.historyList[0].status, 'RESOLVED');
  assert.equal(page.data.historyList[0].handleResult, '已向当值员工传达表扬');
});
