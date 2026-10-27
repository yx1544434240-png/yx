Component({
  options: {
    virtualHost: true,
    styleIsolation: 'apply-shared'
  },
  properties: { src: String, fallback: String },
  data: { displaySrc: '' },
  observers: {
    'src,fallback'(src, fallback) { this.setData({ displaySrc: src || fallback }); }
  },
  methods: {
    onError() {
      if (this.properties.fallback && this.data.displaySrc !== this.properties.fallback) {
        this.setData({ displaySrc: this.properties.fallback });
      }
    }
  }
});
