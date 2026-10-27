// 敏感信息脱敏：列表默认只显示脱敏值，需要时由用户手动展开（S2）
export function maskPhone(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''
  const digits = text.replace(/\D/g, '')
  if (digits.length < 7) return text.replace(/\d/g, '*')
  if (digits.length === 11 && /^\d{11}$/.test(text)) return `${text.slice(0, 3)}****${text.slice(7)}`
  return text.slice(0, Math.max(0, text.length - 4)).replace(/\d/g, '*') + text.slice(-4)
}

export function maskIdCard(value) {
  const text = String(value ?? '').trim()
  if (!text) return ''
  if (text.length < 8) return '*'.repeat(text.length)
  return text.slice(0, 3) + '*'.repeat(text.length - 7) + text.slice(-4)
}
