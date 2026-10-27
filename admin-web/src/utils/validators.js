// 表单校验规则（I1），与后端 DTO 约束保持一致
export const PHONE_PATTERN = /^1\d{10}$/
export const LANDLINE_OR_MOBILE = /^(1\d{10}|0\d{2,3}-?\d{7,8})$/

export const required = (message, trigger = 'blur') => ({ required: true, message, trigger })

export const phoneRule = (label = '手机号', { optional = false } = {}) => ({
  trigger: 'blur',
  validator: (_rule, value, callback) => {
    const text = String(value ?? '').trim()
    if (!text) return optional ? callback() : callback(new Error(`请填写${label}`))
    return LANDLINE_OR_MOBILE.test(text) ? callback() : callback(new Error(`${label}格式不正确`))
  }
})

export const maxLength = (max, label) => ({ max, message: `${label}不超过${max}字`, trigger: 'blur' })

// 时间字符串 HH:mm(:ss) 比较
export function isTimeAfter(end, start) {
  return Boolean(end && start) && String(end) > String(start)
}

// 运行单条规则（供单测与非 el-form 场景使用）
export function check(rule, value) {
  return new Promise(resolve => {
    if (rule.validator) return rule.validator(rule, value, error => resolve(error ? error.message : ''))
    if (rule.required && !String(value ?? '').trim()) return resolve(rule.message)
    if (rule.max && String(value ?? '').length > rule.max) return resolve(rule.message)
    return resolve('')
  })
}
