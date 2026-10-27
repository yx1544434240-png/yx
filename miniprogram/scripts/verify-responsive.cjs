// CSS layout regression fixtures; this does not replace WeChat native-device QA.
// Run with Playwright available through NODE_PATH or the local Node installation.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '..');
const css = name => fs.readFileSync(path.join(root, name), 'utf8')
  .replace(/(?<![-\w])page(?=\s*[{,])/g, 'body')
  .replace(/(-?[\d.]+)rpx/g, (_, value) => `${Number(value) / 7.5}vw`);
const fixtures = {
  registration: { file: 'styles/onboarding.wxss', html: `<view class="container onboarding-page MODE"><view class="onboarding-title">一部手机，演示完整服务</view><view class="onboarding-note">开发环境专用。注册、申请和审核会真实保存到当前数据库。</view><view class="onboarding-layout"><view class="card"><view class="onboarding-heading">从新用户开始演示</view><view class="onboarding-label">演示用户姓名</view><input class="onboarding-input" value="答辩演示用户"><button class="btn-primary onboarding-action">创建演示新用户</button></view><view><view class="onboarding-heading">继续已有演示</view><view class="card onboarding-account"><view><view class="onboarding-heading">答辩工作人员演示账户</view><view class="onboarding-note">工作人员</view></view><button class="btn-secondary">进入</button></view></view></view></view>`, check: '.onboarding-layout' },
  'staff-application': { file: 'styles/onboarding.wxss', html: `<view class="container onboarding-page MODE"><view class="onboarding-title">加入社区服务团队</view><view class="onboarding-note">先申请入驻，再提交专业资质。</view><view class="card"><view class="onboarding-reason">退回原因：请补充社区照护服务经历和可服务时段。</view><view class="onboarding-label">姓名</view><input class="onboarding-input" value="张三"><view class="onboarding-label">手机号码</view><input class="onboarding-input" value="13812345678"><view class="onboarding-label">服务区域</view><input class="onboarding-input" value="阳光花园社区"><view class="onboarding-label">服务经历与申请说明</view><textarea class="onboarding-input"></textarea><button class="btn-primary onboarding-action">补充资料并重新申请</button></view></view>`, check: '.onboarding-page' },
  credentials: { file: 'pages/staff/credentials.wxss', html: `<view class="container credential-page MODE"><view class="credential-layout"><view class="card credential-form"><view class="credential-title">提交新的资质</view><view class="credential-note">请上传清晰的证明图片，仅本人和管理员可查看。</view><view class="credential-label">证书编号</view><input class="credential-input" value="CERT-2026-0001"><view class="credential-proofs"><view class="credential-proof"><image></image><button class="credential-remove">移除</button></view><button class="credential-add">＋ 添加图片</button></view><button class="btn-primary credential-submit">提交审核</button></view><view class="credential-history"><view class="credential-title">已提交的资质</view><view class="card credential-record"><view class="credential-record-heading"><text class="credential-title">社区护理培训合格证明</text><text class="tag tag-warning">已退回</text></view><view class="credential-note">证书编号：ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789012345678901234567890</view><view class="credential-reason">请上传清晰的证书编号，以便核验材料。</view><view class="credential-actions"><button class="btn-secondary">查看材料</button><button class="btn-primary">补充材料</button></view></view></view></view></view>`, check: '.credential-layout' },
  review: { file: 'pages/order/confirm.wxss', html: `<view class="container review-page scroll-content-with-bottom MODE"><view class="review-layout"><view class="card review-context"><view class="review-context-title">助餐送餐</view><view class="review-note">服务人员：张玉兰<br>请根据实际体验评价</view></view><view class="review-form"><view class="card rating-card"><view class="rating-title">这次服务，您满意吗？</view><view class="stars-box flex-row">${[1,2,3,4,5].map(() => '<view class="star-item">★</view>').join('')}</view><view class="score-desc">不太满意</view><view class="review-tags">${['上门迟到','服务未完成','沟通不清','服务态度需改进','其他问题'].map(label => `<button class="review-tag">${label}</button>`).join('')}</view></view><view class="card"><view class="rating-title">补充意见</view><textarea class="comment-input"></textarea><view class="review-counter">0 / 200字</view></view><view class="card"><view class="rating-title">需要社区协助处理吗？</view><view class="review-note">仅在您开启后创建跟进记录</view></view></view></view><view class="confirm-bottom-bar"><button class="btn-primary btn-submit">提交评价并申请跟进</button></view></view>`, check: '.review-layout' },
  navigation: { file: 'custom-tab-bar/index.wxss', html: `<view class="custom-tab-bar NAVMODE"><view class="tab-bar-content">${['首页', '服务', '订单', '我的'].map(text => `<view class="tab-bar-item"><view class="icon-wrapper"><image class="tab-icon"></image></view><text class="tab-label">${text}</text></view>`).join('')}</view><view class="tab-safe-area"></view></view>`, check: '.custom-tab-bar' },
  profile: { file: 'pages/user/profile.wxss', html: `<view class="container profile-page MODE"><view class="profile-hero"><view class="profile-avatar"><image class="avatar-default-icon"></image><view class="avatar-edit-badge"></view></view><view class="profile-identity"><view class="user-name">赵德柱 · 社区居民</view><view class="user-sub"><text class="role-badge">社区居家居民</text></view></view></view></view>`, check: '.profile-hero' },
  home: { file: 'pages/index/index.wxss', html: `<view class="container home-page MODE"><view class="home-header"><view class="home-brand"><image class="home-brand-icon"></image><view class="home-brand-copy"><view class="brand-name">颐邻.</view><view class="brand-community">阳光花园社区</view></view></view><view class="home-header-deco"></view><button class="care-mode-control"><image></image><text>大字版</text></button></view><view class="service-grid-2x2"><view class="service-entry-card"><view class="service-entry-photo"></view><view class="service-card-info"><view class="grid-name">助餐送餐</view></view></view><view class="service-entry-card"><view class="service-entry-photo"></view><view class="service-card-info"><view class="grid-name">居家保洁</view></view></view></view></view>`, check: '.home-header' },
  service: { file: 'pages/service/list.wxss', html: `<view class="service-page MODE"><view class="listing-header-area"><view class="listing-title">家门口的贴心服务</view><view class="listing-caption">左右滑动挑选适合的居家照护服务</view><view class="category-bar"><view class="cat-nav-item">助餐送餐</view><view class="cat-nav-item">居家保洁</view></view></view><swiper class="service-swiper"><view class="service-scroll"><view class="service-list-content"><view class="card service-card"><view class="service-card-main"><view class="service-picture"></view><view class="service-copy"><view class="item-name">社区居家保洁</view><view class="service-meta">专业照护人员上门服务</view></view></view><view class="item-footer"><text class="item-price">¥120</text><button class="book-button">立即预约</button></view></view></view></view></swiper></view>`, check: '.service-swiper' },
  orders: { file: 'pages/order/list.wxss', html: `<view class="page-shell MODE"><view class="order-header-area"><view class="page-title">我的服务订单</view><view class="page-subtitle">查看服务安排与上门进度</view><view class="tabs-bar"><view class="tab-nav-item">全部</view><view class="tab-nav-item">待上门</view></view></view><swiper class="order-swiper"><view class="order-list-content"><view class="card">服务订单</view></view></swiper></view>`, check: '.order-swiper' },
};

// Use the actual active-order WXML; a header-only fixture missed oversized tags.
const homeSource = fs.readFileSync(path.join(root, 'pages/index/index.wxml'), 'utf8');
const header = homeSource.slice(homeSource.indexOf('  <view class="home-header">'), homeSource.indexOf('  <!-- 有进行中服务'));
const activeBranch = homeSource.split('<block wx:elif="{{activeOrder}}">')[1].split('</block>')[0];
function activeHome(elder, family = false) {
  const state = {
    isElderMode: elder,
    activeOrder: { order: { id: 1, requesterType: family ? 'FAMILY' : 'SELF', serviceNameSnapshot: '助餐送餐', status: 'WAIT_MANUAL', plannedStartText: '今天 08:00' }, elder: { name: '赵德柱' }, requesterName: '赵志强', currentStaff: null },
    statusTextMap: { WAIT_MANUAL: '待人工处理' }, activeMoreCount: 0, categoryPhotos: {}, photoFallbacks: {},
  };
  const evaluate = expression => Function(...Object.keys(state), `return (${expression})`)(...Object.values(state));
  let html = header + activeBranch;
  html = html.replace(/<text wx:if="{{(.*?)}}">([\s\S]*?)<\/text>/g, (_, condition, content) => evaluate(condition) ? `<text>${content}</text>` : '');
  html = html.replace(/<view wx:if="{{activeMoreCount > 0}}"[\s\S]*?<\/view>/, '');
  html = html.replace(/{{(.*?)}}/g, (_, expression) => evaluate(expression) ?? '');
  return `<view class="container home-page ${elder ? 'elder-mode' : ''}">${html}</view>`;
}
fixtures['home-active'] = { file: 'pages/index/index.wxss', html: activeHome, check: '.home-dashboard' };
fixtures['home-family'] = { file: 'pages/index/index.wxss', html: elder => activeHome(elder, true), check: '.home-dashboard' };

(async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}) });
  let count = 0;
  try {
    for (const [width, height] of [[320, 568], [375, 667], [768, 1024], [1024, 768], [1366, 768]]) {
      for (const elder of [false, true]) {
        for (const [name, fixture] of Object.entries(fixtures)) {
          const page = await browser.newPage({ viewport: { width, height } });
          const html = typeof fixture.html === 'function' ? fixture.html(elder) : fixture.html.replace(/NAVMODE/g, elder ? 'is-elder' : '').replace(/MODE/g, elder ? 'elder-mode' : '');
          await page.setContent(`<style>html,body{margin:0;height:100%}view,swiper,scroll-view{display:block}image{display:inline-block}button{font-family:inherit} ${css('app.wxss')} ${css(fixture.file)}</style>${html}`);
          const metrics = await page.evaluate(selector => {
            const node = document.querySelector(selector);
            const box = node.getBoundingClientRect();
            return { width: innerWidth, height: innerHeight, scrollWidth: document.documentElement.scrollWidth, box: { x: box.x, right: box.right, bottom: box.bottom, height: box.height }, font: parseFloat(getComputedStyle(document.querySelector('.user-name,.brand-name,.listing-title,.page-title,.tab-label,.review-context-title,.credential-title,.onboarding-title')).fontSize), overflow: [...node.querySelectorAll('*')].filter(el => { const b = el.getBoundingClientRect(); return b.width && (b.right > innerWidth + 1 || b.left < -1); }).map(el => el.className) };
          }, fixture.check);
          const label = `${name} ${width}x${height} ${elder ? 'elder' : 'standard'}`;
          assert(metrics.scrollWidth <= width + 1, `${label}: horizontal page overflow`);
          assert.deepEqual(metrics.overflow, [], `${label}: children overflow`);
          if (name === 'service' || name === 'orders') {
            assert(metrics.box.height > 50, `${label}: list collapsed`);
            assert(metrics.box.bottom <= height + 1, `${label}: list extends below viewport`);
          }
          if (width >= 600) assert(metrics.font <= 32, `${label}: tablet typography grows with rpx`);
          if (name === 'navigation' && width >= 600) assert.equal(metrics.box.height, (elder ? 72 : 64) + 1, `${label}: tab bar height differs from page reservation`);
          if (name.startsWith('home-') && width >= 600) {
            const order = await page.evaluate(() => ({
              tag: parseFloat(getComputedStyle(document.querySelector('.active-order-title-group .tag')).fontSize),
              height: document.querySelector('.active-order-hero').getBoundingClientRect().height,
              ctaBottom: document.querySelector('.active-order-cta').getBoundingClientRect().bottom,
              columns: getComputedStyle(document.querySelector('.home-dashboard')).gridTemplateColumns,
            }));
            assert(order.tag <= (elder ? 18 : 15), `${label}: requester tag too large`);
            assert(order.height <= (elder ? 420 : 360), `${label}: active card consumes the screen`);
            assert(order.ctaBottom < height - (elder ? 72 : 64), `${label}: progress action below first screen`);
            if (width >= 900) assert.equal(order.columns.split(' ').length, 2, `${label}: wide tablet should use two columns`);
          }
          if (process.env.RESPONSIVE_SCREENSHOT_DIR && [768, 1024].includes(width) && !elder) {
            fs.mkdirSync(process.env.RESPONSIVE_SCREENSHOT_DIR, { recursive: true });
            await page.screenshot({ path: path.join(process.env.RESPONSIVE_SCREENSHOT_DIR, `${name}-${width}-tablet.png`) });
          }
          await page.close();
          count++;
        }
      }
    }
    console.log(`PASS: ${count} CSS layout fixtures across phone/tablet sizes and standard/elder modes.`);
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
