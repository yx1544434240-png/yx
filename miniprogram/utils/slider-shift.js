function sliderShift(index, dx, pageWidth, count) {
  const width = pageWidth > 0 ? pageWidth : 1;
  const raw = index - dx / width;
  const max = Math.max(0, count - 1);
  if (raw < 0) return raw * 0.35;
  if (raw > max) return max + (raw - max) * 0.35;
  return raw;
}

// 提取订单与服务列表共享的手势与屏幕旋转测量逻辑，并对微小位移去抖以降低 setData 频率
function createSwipeMethods({ indexKey, count }) {
  return {
    measurePage() {
      try {
        const info = typeof wx.getWindowInfo === 'function' ? wx.getWindowInfo() : wx.getSystemInfoSync();
        this._pageWidth = info.windowWidth || 375;
      } catch (_) {
        this._pageWidth = 375;
      }
    },

    onResize() {
      this.measurePage();
      this._gesturing = false;
      this._gestureIndex = null;
      this.setData({ sliderShift: this.data[indexKey] * 100, sliderLive: false });
    },

    onSwiperTouchStart() {
      this._gesturing = true;
      this._gestureIndex = this.data[indexKey];
    },

    onSwiperTransition(e) {
      const index = this._gestureIndex == null ? this.data[indexKey] : this._gestureIndex;
      const shift = Math.round(sliderShift(index, e.detail.dx || 0, this._pageWidth || 375, count) * 1000) / 10;
      if (Math.abs(shift - this.data.sliderShift) < 0.5 && this.data.sliderLive) return;
      this.setData({ sliderShift: shift, sliderLive: true });
    }
  };
}

module.exports = { sliderShift, createSwipeMethods };
