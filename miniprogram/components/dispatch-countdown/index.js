Component({
  properties: { deadline: { type: String, value: '', observer: 'syncDeadline' } },
  data: { seconds: 0 },
  lifetimes: {
    attached() { this._visible = true; this.syncDeadline(); },
    detached() { this._visible = false; this.stopTimer(); }
  },
  pageLifetimes: {
    show() { this._visible = true; this.syncDeadline(); },
    hide() { this._visible = false; this.stopTimer(); }
  },
  methods: {
    stopTimer() { if (this._timer) { clearInterval(this._timer); this._timer = null; } },
    syncDeadline() {
      this.stopTimer();
      const value = this.properties.deadline;
      const deadline = new Date(String(value).replace('T', ' ').replace(/-/g, '/')).getTime();
      if (!this._visible || !Number.isFinite(deadline)) return;
      const tick = () => {
        const seconds = Math.max(0, Math.ceil((deadline - Date.now()) / 1000));
        if (seconds !== this.data.seconds) this.setData({ seconds });
        if (!seconds) {
          this.stopTimer();
          if (this._expiredAt !== value) { this._expiredAt = value; this.triggerEvent('expire'); }
        }
      };
      tick();
      if (this.data.seconds > 0) this._timer = setInterval(tick, 1000);
    }
  }
});
