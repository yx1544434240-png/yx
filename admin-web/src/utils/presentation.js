// 后台所有状态/分类/技能文案与标签颜色的唯一来源（C2）
const ORDER = {
  WAIT_DISPATCH: ['等待安排', 'warning'],
  WAIT_ACCEPT: ['等待接单', 'warning'],
  WAIT_VISIT: ['待上门', 'primary'],
  IN_SERVICE: ['服务中', 'primary'],
  WAIT_CONFIRM: ['待确认', 'warning'],
  COMPLETED: ['已完成', 'success'],
  WAIT_MANUAL: ['需要协调', 'danger'],
  CANCELLED: ['已取消', 'info']
}
const BINDING = {
  PENDING: ['待审核', 'warning'],
  APPROVED: ['已授权', 'success'],
  REJECTED: ['已驳回', 'danger'],
  REVOKED: ['已撤销', 'info']
}
const COMPLAINT = {
  PENDING: ['待受理', 'warning'],
  PROCESSING: ['处理中', 'primary'],
  RESOLVED: ['已处理', 'success'],
  REJECTED: ['已关闭', 'info']
}
const STAFF_WORK = { AVAILABLE: ['接单中', 'success'], BUSY: ['服务中', 'warning'], RESTING: ['休息中', 'info'] }
const STAFF_AUDIT = { APPROVED: ['已通过', 'success'], PENDING: ['待审核', 'warning'], REJECTED: ['未通过', 'danger'] }
const SERVICE_STATUS = { ON_SHELF: ['上架中', 'success'], OFF_SHELF: ['已下架', 'info'] }
const SHIFT = { ACTIVE: ['有效', 'success'], CANCELLED: ['已取消', 'info'] }
const CATEGORY = {
  MEAL: ['助餐送餐', 'warning'],
  CLEANING: ['居家保洁', 'primary'],
  ERRAND: ['代买代办', 'success'],
  ESCORT: ['陪同办事', 'info']
}

export const STATUS_KINDS = {
  order: ORDER,
  binding: BINDING,
  complaint: COMPLAINT,
  staffWork: STAFF_WORK,
  staffAudit: STAFF_AUDIT,
  service: SERVICE_STATUS,
  shift: SHIFT,
  category: CATEGORY
}

export const SKILLS = {
  COOKING: '助餐烹饪',
  CLEANING: '居家保洁',
  ERRAND: '跑腿代办',
  ESCORT: '助行陪办',
  NURSING: '基础照护护理'
}

export function statusText(kind, value) {
  return STATUS_KINDS[kind]?.[value]?.[0] || value || ''
}

export function statusTag(kind, value) {
  return STATUS_KINDS[kind]?.[value]?.[1] || 'info'
}

// 下拉选项：[{ label, value }]
export function statusOptions(kind) {
  return Object.entries(STATUS_KINDS[kind] || {}).map(([value, [label]]) => ({ label, value }))
}

export function skillText(codes) {
  return String(codes || '')
    .split(',')
    .map(code => code.trim())
    .filter(Boolean)
    .map(code => SKILLS[code] || code)
    .join('、')
}

// 兼容已有调用（dispatch / dashboard）
export const orderStatusText = Object.fromEntries(Object.entries(ORDER).map(([key, [text]]) => [key, text]))

export function shortDate(value) {
  if (!value) return '时间待确认'
  return String(value).replace('T', ' ').slice(5, 16).replace('-', '/')
}

export function latestCandidateScores(scores = []) {
  const round = Math.max(0, ...scores.map(score => Number(score.dispatchRound) || 0))
  const candidates = new Map()
  for (const score of scores) {
    if (Number(score.dispatchRound) !== round) continue
    const key = String(score.staffId)
    const previous = candidates.get(key)
    const stamp = `${String(score.createdAt || '').replace('T', ' ')}|${String(score.id || '').padStart(24, '0')}`
    const previousStamp = previous
      ? `${String(previous.createdAt || '').replace('T', ' ')}|${String(previous.id || '').padStart(24, '0')}`
      : ''
    if (!previous || stamp > previousStamp) candidates.set(key, score)
  }
  return [...candidates.values()].sort(
    (a, b) =>
      Number(b.isQualified) - Number(a.isQualified) ||
      Number(b.totalScore) - Number(a.totalScore) ||
      Number(a.travelMinutes || 0) - Number(b.travelMinutes || 0) ||
      String(a.staffId).localeCompare(String(b.staffId), 'en', { numeric: true })
  )
}

export const OPERATION_ACTIONS = {
  // 服务项目
  'ServiceItemController.saveOrUpdate': '保存服务项目',
  'ServiceItemController.uploadImage': '上传服务封面图',
  'ServiceItemController.toggleStatus': '上架/下架服务项目',
  'ServiceItemController.updateStatus': '调整服务状态',
  'ServiceItemController.delete': '删除服务项目',
  // 调度派单
  'DispatchController.assign': '人工指派调度',
  'DispatchController.reassign': '重新指派服务人员',
  'DispatchController.accept': '服务人员接单',
  'DispatchController.reject': '服务人员拒单',
  // 老人档案
  'ElderProfileController.saveOrUpdate': '维护老人健康档案',
  'ElderProfileController.save': '保存老人健康档案',
  // 家属绑定
  'FamilyBindingController.apply': '提交家属代约申请',
  'FamilyBindingController.audit': '审核家属绑定申请',
  'FamilyBindingController.revoke': '解除家属绑定关系',
  // 履约服务
  'FulfillmentController.startService': '服务人员开始上门',
  'FulfillmentController.submitRecord': '提交服务核销凭证',
  'FulfillmentController.confirm': '确认完成服务订单',
  'FulfillmentController.submitReview': '提交服务评价',
  'FulfillmentController.submitComplaint': '提交服务投诉建议',
  'FulfillmentController.startComplaint': '受理服务投诉建议',
  'FulfillmentController.handleComplaint': '处理办结投诉建议',
  // 订单
  'OrderController.createOrder': '创建预约订单',
  'OrderController.create': '创建预约订单',
  'OrderController.cancelOrder': '取消预约订单',
  'OrderController.cancel': '取消预约订单',
  'OrderController.payOrder': '支付结算订单',
  'OrderController.pay': '支付结算订单',
  // 服务人员
  'StaffController.saveOrUpdate': '维护服务人员档案',
  'StaffController.audit': '审核人员执业资质',
  'StaffController.updateStatus': '调整人员在岗状态',
  'StaffController.addSkill': '认证添加专业技能',
  'StaffController.removeSkill': '移除人员专业技能',
  'StaffController.addShift': '安排服务人员排班',
  'StaffController.reportLocation': '上报人员实时坐标',
  'StaffController.mockMoveLocation': '模拟人员位置移动',
  // 用户与权限
  'UserController.updateStatus': '启用/冻结系统账号',
  'UserController.updatePassword': '修改系统登录密码',
  'UserController.updateAvatar': '更换用户个人头像',
  'UserController.updateProfile': '修改用户个人资料',
  // 附件与消息
  'FileUploadController.uploadImage': '上传系统图片文件',
  'NotificationController.markAsRead': '标记系统通知已读',
  'NotificationController.read': '标记系统通知已读',
  // 认证
  'AuthController.login': '账号密码登录',
  'AuthController.wechatLogin': '微信授权登录',
  'MockAuthController.mockLogin': '模拟快速登录',
  // 系统清理与迁移
  LEGACY_PHOTO_PENDING: '历史私有图片待清理',
  LEGACY_PHOTO_CLEANED: '历史私有图片已清理归档'
}

export const OPERATION_TARGETS = {
  ServiceItemController: '服务项目',
  SERVICE_ITEM: '服务项目',
  DispatchController: '调度派单',
  DISPATCH: '调度派单',
  ElderProfileController: '老人档案',
  ELDER: '老人档案',
  FamilyBindingController: '家属绑定',
  FAMILY_BINDING: '家属绑定',
  FulfillmentController: '履约服务',
  FULFILLMENT: '履约服务',
  OrderController: '服务订单',
  ORDER: '服务订单',
  StaffController: '服务人员',
  STAFF: '服务人员',
  UserController: '系统用户',
  USER: '系统用户',
  FileUploadController: '文件附件',
  UPLOAD: '文件附件',
  NotificationController: '消息通知',
  NOTIFICATION: '消息通知',
  SERVICE_RECORD: '服务核销记录',
  SYSTEM: '系统任务'
}

export function formatAction(action) {
  if (!action) return '-'
  if (OPERATION_ACTIONS[action]) return OPERATION_ACTIONS[action]
  const parts = String(action).split('.')
  if (parts.length === 2) {
    const [controller, method] = parts
    const target = OPERATION_TARGETS[controller] || controller.replace(/Controller$/, '')
    const verbMap = {
      saveOrUpdate: '保存/维护',
      save: '保存',
      update: '修改',
      delete: '删除',
      remove: '移除',
      audit: '审核',
      uploadImage: '上传图片',
      toggleStatus: '切换状态',
      updateStatus: '修改状态',
      assign: '指派调度',
      cancel: '取消',
      confirm: '确认',
      create: '创建'
    }
    const verb = verbMap[method] || method
    return `${target} - ${verb}`
  }
  return String(action)
}

export function formatTargetType(targetType) {
  if (!targetType) return '通用对象'
  return OPERATION_TARGETS[targetType] || targetType.replace(/Controller$/, '')
}

export function formatDetails(details) {
  if (!details) return '-'
  if (typeof details === 'object') {
    if (details.method && details.path) return `${details.method} ${details.path}`
    return JSON.stringify(details)
  }
  const raw = String(details).trim()
  if (raw.startsWith('{') && raw.endsWith('}')) {
    try {
      const parsed = JSON.parse(raw)
      if (parsed.method && parsed.path) {
        return `${parsed.method} ${parsed.path}`
      }
      if (parsed.message) return String(parsed.message)
      if (parsed.reason) return String(parsed.reason)
    } catch {
      // ignore JSON parse failure
    }
  }
  return raw
}

export function actionTagType(action) {
  const str = String(action || '').toLowerCase()
  if (str.includes('audit') || str.includes('confirm') || str.includes('accept') || str.includes('cleaned')) {
    return 'success'
  }
  if (
    str.includes('cancel') ||
    str.includes('reject') ||
    str.includes('delete') ||
    str.includes('remove') ||
    str.includes('revoke')
  ) {
    return 'danger'
  }
  if (str.includes('reassign') || str.includes('status') || str.includes('complaint')) {
    return 'warning'
  }
  return 'primary'
}
