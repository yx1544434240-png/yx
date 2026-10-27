const navigation = require('../../utils/navigation.js');
const { syncTabBar } = require('../../utils/tab-bar.js');
const request = require('../../utils/request.js');
const { HOTLINE_NUMBER, HOTLINE_HOURS } = require('../../utils/contact.js');
const { getAppSafe, app } = require('../../utils/app-state.js');
// 订单看板角标：与订单页分类使用同样的状态组合
const HUB_STATUSES = {
  WAIT_DISPATCH: 'WAIT_DISPATCH,WAIT_ACCEPT,WAIT_MANUAL',
  WAIT_VISIT: 'WAIT_VISIT',
  IN_SERVICE: 'IN_SERVICE',
  WAIT_CONFIRM: 'WAIT_CONFIRM'
};

Page({
  data: {
    hotlineHours: HOTLINE_HOURS,
    hubCounts: {},
    userInfo: null,
    staffProfile: null,
    staffSkills: [],
    staffSkillsDesc: '',
    isElderMode: false,
    isDevMode: false,
    demoEnabled: false,
    currentBaseUrl: 'http://localhost:8080/api',
    logoutSheet: false,
    roleTextMap: {
      'ADMIN': '社区系统管理员',
      'STAFF': '认证上门服务人员',
      'RESIDENT': '社区居家居民'
    }
  },

  onShow() {
    syncTabBar(this, 3);
    const app = getAppSafe();
    const patch = {};
    const isDev = /^https?:\/\/(localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(1[6-9]|2\d|3[01]))/i.test(app.globalData?.baseUrl || '');
    const state = {
      userInfo: app.globalData?.userInfo || null,
      isElderMode: Boolean(app.globalData?.isElderMode),
      isDevMode: isDev,
      currentBaseUrl: app.globalData?.baseUrl || 'http://localhost:8080/api'
    };
    for (const key of Object.keys(state)) {
      if (this._shownState?.[key] !== state[key]) patch[key] = state[key];
    }
    this._shownState = state;
    if (Object.keys(patch).length) this.setData(patch);
    this.loadDemoCapability();
    if (state.userInfo?.role === 'STAFF') {
      this.loadStaffProfile();
    } else if (state.userInfo && app.globalData?.token) {
      this.loadHubCounts();
    }
  },

  loadDemoCapability() {
    const state = getAppSafe().globalData;
    const key = `${state.baseUrl}|${state.token}|${state.envVersion || 'develop'}`;
    const token = state.token, baseUrl = state.baseUrl;
    if (key === this._demoKey) return;
    this._demoKey = key;
    this.setData({ demoEnabled: false });
    if (!state.token || ['trial', 'release'].includes(state.envVersion)) return;
    return request.get('/auth/demo-capabilities', {}, { silent: true }).then(result => {
      if (this._demoKey === key && getAppSafe().globalData.token === token && getAppSafe().globalData.baseUrl === baseUrl) this.setData({ demoEnabled: result?.enabled === true });
    }).catch(() => { if (this._demoKey === key) this._demoKey = null; });
  },

  goToStaffApplication() { navigation.navigateTo({ url: '/pages/staff/apply' }); },

  // 每个状态只取 total；失败时不显示角标，不打扰页面
  loadHubCounts() {
    const app = getAppSafe();
    const key = `${app.globalData?.baseUrl || ''}|${app.globalData?.token || ''}|${app.globalData?.ordersVersion || 0}`;
    if (this._hubKey === key && Date.now() - (this._hubAt || 0) < 30000) return Promise.resolve();
    this._hubKey = key; this._hubAt = Date.now();
    const entries = Object.keys(HUB_STATUSES);
    return Promise.all(entries.map(name => request.get('/orders/page', { pageNum: 1, pageSize: 1, status: HUB_STATUSES[name] }, { silent: true })
      .then(data => Number(data?.total) || 0).catch(() => 0))).then(totals => {
      if (this._hubKey !== key) return;
      const hubCounts = {};
      entries.forEach((name, i) => { hubCounts[name] = totals[i] > 99 ? '99+' : totals[i]; });
      this.setData({ hubCounts });
    });
  },

  loadStaffProfile() {
    return request.get('/staff/my-profile').then(res => {
      if (!res) return;
      const profile = res.profile || null;
        const skills = (res.skills || []).filter(skill => skill.auditStatus === 'APPROVED');
      const skillsDesc = skills.map(s => s.skillName).join(' · ');
      this.setData({
        staffProfile: profile,
        staffSkills: skills,
          staffSkillsDesc: skillsDesc || '上传证明材料，等待社区审核'
      });
    }).catch(() => {});
  },

  chooseAndUploadAvatar() {
    const app = getAppSafe();
    if (!this.data.userInfo) {
      this.goToLogin();
      return;
    }

    const that = this;
    const uploadFile = (filePath) => {
      wx.showLoading({ title: '正在上传头像...', mask: true });
      const baseUrl = app.globalData?.baseUrl || 'http://localhost:8080/api';
      const token = app.globalData?.token || (typeof wx.getStorageSync === 'function' ? wx.getStorageSync('token') : null);

      wx.uploadFile({
        url: `${baseUrl}/upload/image?type=avatar`,
        filePath: filePath,
        name: 'file',
        header: {
          'Authorization': token ? `Bearer ${token}` : ''
        },
        success(res) {
          wx.hideLoading();
          let data;
          try {
            data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data;
          } catch (e) {
            wx.showToast({ title: '上传解析异常', icon: 'none' });
            return;
          }

          if (data && (data.code === 200 || data.code === 0) && data.data && data.data.url) {
            const avatarUrl = data.data.url;
            request.post('/users/avatar', { avatar: avatarUrl }).then(() => {
              const updatedUser = { ...(that.data.userInfo || {}), avatar: avatarUrl };
              if (app.globalData) {
                app.globalData.userInfo = updatedUser;
              }
              if (typeof wx.setStorageSync === 'function') {
                wx.setStorageSync('userInfo', updatedUser);
              }
              that.setData({ userInfo: updatedUser });
              wx.showToast({ title: '头像更新成功', icon: 'success' });
            }).catch(err => {
              wx.showToast({ title: err?.message || '更新头像失败', icon: 'none' });
            });
          } else {
            wx.showToast({ title: data?.message || '图片上传失败', icon: 'none' });
          }
        },
        fail() {
          wx.hideLoading();
          wx.showToast({ title: '网络连接失败，请重试', icon: 'none' });
        }
      });
    };

    if (typeof wx.chooseMedia === 'function') {
      wx.chooseMedia({
        count: 1,
        mediaType: ['image'],
        sourceType: ['album', 'camera'],
        sizeType: ['compressed'],
        success(res) {
          if (res.tempFiles && res.tempFiles.length > 0) {
            uploadFile(res.tempFiles[0].tempFilePath);
          }
        }
      });
    } else if (typeof wx.chooseImage === 'function') {
      wx.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success(res) {
          if (res.tempFilePaths && res.tempFilePaths.length > 0) {
            uploadFile(res.tempFilePaths[0]);
          }
        }
      });
    }
  },

  // 整行可点：切换适老模式（原生 switch 仅作状态展示）
  toggleElderRow() {
    this.onModeChange({ detail: { value: !this.data.isElderMode } });
  },

  onModeChange(e) {
    const isElder = e.detail.value;
    app.toggleElderMode(isElder);
    syncTabBar(this, 3);
    this.setData({ isElderMode: isElder });
  },

  /**
   * ActionSheet 1: 演示账号角色一键切换 (答辩与评测利器)
   */
  openRoleSwitchSheet() {
    if (this.data.demoEnabled) navigation.navigateTo({ url: '/pages/auth/demo' });
  },

  /**
   * ActionSheet 2: 社区养老便民与应急服务热线
   */
  contactSupportSheet() {
    const isStaff = this.data.userInfo?.role === 'STAFF';
    const contacts = isStaff ? [
      { label: '呼叫 阳光社区助老调度中心 (010-88886699)', phone: '010-88886699' },
      { label: '呼叫 责任网格长与站长 (13800000000)', phone: '13800000000' },
      { label: '拨打 120 医疗急救热线 (120)', phone: '120' }
    ] : [
      { label: `拨打 社区养老服务热线 ${HOTLINE_NUMBER}（${HOTLINE_HOURS}）`, phone: HOTLINE_NUMBER },
      { label: '拨打 社区家庭医生健康直通车 (010-88886688)', phone: '010-88886688' },
      { label: '呼叫 阳光社区责任网格员小王 (13800000000)', phone: '13800000000' }
    ];

    wx.showActionSheet({
      itemList: contacts.map(c => c.label),
      success(res) {
        const target = contacts[res.tapIndex];
        if (target) {
          wx.makePhoneCall({
            phoneNumber: target.phone
          });
        }
      }
    });
  },

  /**
   * ActionSheet 3: 退出登录优雅二次确认
   */
  confirmLogout() {
    this.setData({ logoutSheet: true });
  },

  closeLogoutSheet() {
    this.setData({ logoutSheet: false });
  },

  /**
   * ActionSheet 4: 切换后端服务接口地址 (模拟器/真机调试)
   */
  changeServerUrl() {
    const defaultUrls = app.getServerUrls();
    const that = this;
    wx.showActionSheet({
      itemList: [
        ...defaultUrls.map(url => `${/localhost|127\.0\.0\.1/.test(url) ? '电脑模拟器' : '手机真机/局域网'}: ${url}`),
        '手动输入自定义服务器地址'
      ],
      success(res) {
        if (res.tapIndex < defaultUrls.length) {
          const selected = defaultUrls[res.tapIndex];
          app.globalData.baseUrl = selected;
          wx.setStorageSync('customBaseUrl', selected);
          that.setData({ currentBaseUrl: selected });
          wx.showToast({ title: '已切换服务地址', icon: 'success' });
        } else if (res.tapIndex === defaultUrls.length) {
          wx.showModal({
            title: '自定义后端地址',
            editable: true,
            placeholderText: '例如: ' + (app.getServerUrls().find(url => !/localhost|127\.0\.0\.1/.test(url)) || 'http://电脑IP:8080/api'),
            success(modalRes) {
              if (modalRes.confirm && modalRes.content.trim()) {
                const url = app.resolveBaseUrl(modalRes.content);
                app.globalData.baseUrl = url;
                wx.setStorageSync('customBaseUrl', url);
                that.setData({ currentBaseUrl: url });
                wx.showToast({ title: '已更新地址', icon: 'success' });
              }
            }
          });
        }
      }
    });
  },

  wechatLogin() {
    wx.showLoading({ title: '正在登录...' });
    wx.login({
      success: (loginRes) => {
        if (loginRes.code) {
          request.post('/auth/wechat-login', {
            code: loginRes.code,
            nickName: '微信居民用户',
            avatarUrl: ''
          }).then(res => {
            wx.hideLoading();
            app.globalData.token = res.token;
            app.globalData.userInfo = res.userInfo;
            app.globalData.role = res.userInfo.role;
            wx.setStorageSync('token', res.token);
            wx.setStorageSync('userInfo', res.userInfo);

            this.setData({ userInfo: res.userInfo });
            wx.showToast({ title: '微信登录成功', icon: 'success' });
          }).catch(() => {
            wx.hideLoading();
          });
        } else {
          wx.hideLoading();
          wx.showToast({ title: '获取微信登录码失败: ' + loginRes.errMsg, icon: 'none' });
        }
      },
      fail: () => {
        wx.hideLoading();
        wx.showToast({ title: '调用微信登录失败', icon: 'none' });
      }
    });
  },

  logout() {
    require('../../utils/media-url.js').clearPrivateMedia();
    this.setData({ logoutSheet: false, staffProfile: null, staffSkills: [], staffSkillsDesc: '' });
    require('../../utils/session.js').clearSession(app);
    this.setData({ userInfo: null });
    wx.showToast({ title: '已安全退出登录', icon: 'none' });
    navigation.toLogin('/pages/user/profile');
  },

  goToLogin() {
    navigation.toLogin('/pages/user/profile');
  },

  goToOrderList(e) {
    const status = (e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.status) || '';
    app.globalData.orderStatusFilter = status;
    navigation.switchTab({ url: '/pages/order/list' });
  },

  goToElderList() {
    navigation.navigateTo({ url: '/pages/user/elder-list' });
  },

  goToFamily() {
    navigation.navigateTo({ url: '/pages/user/family' });
  },

  goToFeedback() {
    if (!this.data.userInfo) {
      navigation.toLogin('/pages/user/feedback');
      return;
    }
    navigation.navigateTo({ url: '/pages/user/feedback' });
  },

  goToWorkbench() {
    navigation.switchTab({
      url: '/pages/staff/workbench',
      fail: () => navigation.navigateTo({ url: '/pages/staff/workbench' })
    });
  },

  showSkillsModal() {
    navigation.navigateTo({ url: '/pages/staff/credentials' });
  }
});
