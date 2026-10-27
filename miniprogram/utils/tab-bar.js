function syncTabBar(page, index) {
  const bar = typeof page.getTabBar === 'function' ? page.getTabBar() : null;
  let isElderMode = false;
  try {
    const app = getApp();
    if (app && app.globalData) {
      isElderMode = Boolean(app.globalData.isElderMode);
    }
  } catch (_) {
    isElderMode = false;
  }
  if (bar && typeof bar.syncState === 'function') {
    bar.syncState(index, isElderMode);
  }
}

module.exports = { syncTabBar };
