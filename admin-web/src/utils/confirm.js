import { ElMessageBox } from 'element-plus'

// 危险操作二次确认（I2）：确认按钮为危险色，文案写清影响；取消时 resolve false
export async function confirmDanger(message, title, confirmText = '确认') {
  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: '再想想',
      confirmButtonClass: 'el-button--danger',
      autofocus: false
    })
    return true
  } catch {
    return false
  }
}

// 需要填写理由的危险操作；返回理由或 null（取消）
export async function promptReason(
  message,
  title,
  { confirmText = '确认', min = 4, max = 200, placeholder = '', danger = false } = {}
) {
  try {
    const { value } = await ElMessageBox.prompt(message, title, {
      confirmButtonText: confirmText,
      cancelButtonText: '再想想',
      confirmButtonClass: danger ? 'el-button--danger' : '',
      inputValue: '',
      inputPlaceholder: placeholder,
      inputType: 'textarea',
      inputValidator: text => {
        const length = (text || '').trim().length
        return (length >= min && length <= max) || `请填写${min}至${max}字的说明`
      }
    })
    return value.trim()
  } catch {
    return null
  }
}
