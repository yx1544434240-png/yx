import test from 'node:test'
import assert from 'node:assert/strict'
import { latestCandidateScores, shortDate } from './presentation.js'

test('only the latest dispatch round is presented and qualified candidates come first', () => {
  const rows = [
    { id: '1', staffId: 'a', dispatchRound: 1, isQualified: 1, totalScore: 0.99 },
    { id: '2', staffId: 'b', dispatchRound: 2, isQualified: 0, totalScore: 0.95 },
    { id: '3', staffId: 'c', dispatchRound: 2, isQualified: 1, totalScore: 0.7 },
    { id: '4', staffId: 'd', dispatchRound: 2, isQualified: 1, totalScore: 0.85 }
  ]
  assert.deepEqual(
    latestCandidateScores(rows).map(row => row.staffId),
    ['d', 'c', 'b']
  )
  assert.equal(rows[0].staffId, 'a')
})

test('recalculated history uses the newest score for each staff member', () => {
  const rows = [
    { id: '90', staffId: 'a', dispatchRound: 2, createdAt: '2026-10-20T10:00:00', totalScore: 0.9 },
    { id: '91', staffId: 'a', dispatchRound: 2, createdAt: '2026-10-20 11:00:00', totalScore: 0.5 },
    { id: '92', staffId: 'b', dispatchRound: 2, createdAt: '2026-10-20 11:00:00', totalScore: 0.7 }
  ]
  assert.equal(latestCandidateScores(rows).find(row => row.staffId === 'a').id, '91')
})

test('string IDs larger than the JavaScript integer range remain distinct', () => {
  const rows = [
    { id: '9223372036854775805', staffId: '9007199254740992', dispatchRound: 1 },
    { id: '9223372036854775806', staffId: '9007199254740993', dispatchRound: 1 },
    { id: '9223372036854775807', staffId: '9007199254740992', dispatchRound: 1 }
  ]
  assert.equal(latestCandidateScores(rows).length, 2)
  assert.equal(latestCandidateScores(rows).find(row => row.staffId === '9007199254740992').id, '9223372036854775807')
})

test('empty scores and missing dates have explicit fallbacks', () => {
  assert.deepEqual(latestCandidateScores([]), [])
  assert.equal(shortDate(null), '时间待确认')
  assert.equal(shortDate('2026-10-20T09:30:00'), '10/20 09:30')
})

test('equal scores use travel time and staff ID to keep recommendations stable', () => {
  const rows = [
    { id: '1', staffId: '12', dispatchRound: 1, isQualified: 1, totalScore: 0.8, travelMinutes: 5 },
    { id: '2', staffId: '2', dispatchRound: 1, isQualified: 1, totalScore: 0.8, travelMinutes: 5 },
    { id: '3', staffId: '1', dispatchRound: 1, isQualified: 1, totalScore: 0.8, travelMinutes: 10 }
  ]
  assert.deepEqual(
    latestCandidateScores(rows).map(row => row.staffId),
    ['2', '12', '1']
  )
})

import { statusOptions, statusTag, statusText, skillText, orderStatusText } from './presentation.js'
test('one status table drives labels, tag colours and filter options', () => {
  assert.equal(statusText('order', 'WAIT_DISPATCH'), '等待安排')
  assert.equal(orderStatusText.WAIT_MANUAL, statusText('order', 'WAIT_MANUAL'))
  assert.equal(statusTag('order', 'WAIT_MANUAL'), 'danger')
  assert.equal(statusText('binding', 'APPROVED'), '已授权')
  assert.equal(statusText('order', 'NEW_STATUS'), 'NEW_STATUS')
  assert.equal(statusTag('order', 'NEW_STATUS'), 'info')
  const options = statusOptions('order')
  assert.equal(options.length, 8)
  assert.ok(options.every(option => option.label === statusText('order', option.value)))
  assert.equal(skillText('COOKING, NURSING,X'), '助餐烹饪、基础照护护理、X')
})

import { formatAction, formatTargetType, formatDetails, actionTagType } from './presentation.js'

test('operation log action formatting translates controller methods to administrator Chinese', () => {
  assert.equal(formatAction('ServiceItemController.saveOrUpdate'), '保存服务项目')
  assert.equal(formatAction('ServiceItemController.uploadImage'), '上传服务封面图')
  assert.equal(formatAction('DispatchController.assign'), '人工指派调度')
  assert.equal(formatAction('DispatchController.reassign'), '重新指派服务人员')
  assert.equal(formatAction('StaffController.audit'), '审核人员执业资质')
  assert.equal(formatAction('OrderController.createOrder'), '创建预约订单')
  assert.equal(formatAction('LEGACY_PHOTO_CLEANED'), '历史私有图片已清理归档')
  // Heuristic fallback for unmapped controller method
  assert.equal(formatAction('ServiceItemController.delete'), '删除服务项目')
  assert.equal(formatAction('UnknownCustomController.save'), 'UnknownCustom - 保存')
  assert.equal(formatAction(null), '-')
})

test('operation log target and details formatting produces clean readable output', () => {
  assert.equal(formatTargetType('ServiceItemController'), '服务项目')
  assert.equal(formatTargetType('OrderController'), '服务订单')
  assert.equal(formatTargetType(null), '通用对象')

  assert.equal(formatDetails('{"method":"POST","path":"/api/service-items"}'), 'POST /api/service-items')
  assert.equal(formatDetails({ method: 'PUT', path: '/api/users/1' }), 'PUT /api/users/1')
  assert.equal(formatDetails('plain text detail'), 'plain text detail')
  assert.equal(formatDetails(null), '-')

  assert.equal(actionTagType('StaffController.audit'), 'success')
  assert.equal(actionTagType('OrderController.cancel'), 'danger')
  assert.equal(actionTagType('DispatchController.reassign'), 'warning')
  assert.equal(actionTagType('ServiceItemController.saveOrUpdate'), 'primary')
})
