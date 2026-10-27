// ECharts 的 HTML tooltip 不转义数据，名称需手动转义防止注入
const MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }
export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch => MAP[ch])
}
