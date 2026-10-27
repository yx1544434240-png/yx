function lightTap() {
  if (typeof wx !== 'undefined' && typeof wx.vibrateShort === 'function') wx.vibrateShort({ type: 'light' });
}

module.exports = { lightTap };
