// 统一的空状态 / 加载失败状态：适老字号，操作按钮 ≥88rpx
Component({
  options: { virtualHost: true, styleIsolation: 'apply-shared' },
  properties: {
    type: { type: String, value: 'empty' }, // empty | error
    title: String,
    description: String,
    icon: String,
    actionText: String
  },
  methods: {
    onAction() { this.triggerEvent('action'); }
  }
});
