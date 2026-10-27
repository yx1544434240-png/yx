// 老人档案保存白名单（S1）：只提交表单可编辑字段，idCard 等不回传，避免把脱敏值或旧数据写回
export const ELDER_FIELDS = [
  'id',
  'name',
  'gender',
  'age',
  'phone',
  'address',
  'community',
  'emergencyContact',
  'emergencyPhone',
  'healthNote',
  'careLevel',
  'longitude',
  'latitude'
]

export function pickElderPayload(source = {}) {
  const payload = {}
  for (const key of ELDER_FIELDS) if (source[key] !== undefined) payload[key] = source[key]
  for (const key of ['phone', 'emergencyPhone', 'name', 'address', 'emergencyContact']) {
    if (typeof payload[key] === 'string') payload[key] = payload[key].trim()
  }
  return payload
}
