const POSITIVE_TAGS = ['准时上门', '态度耐心', '服务细致', '沟通顺畅', '整体满意'];
const ISSUE_TAGS = ['上门迟到', '服务未完成', '沟通不清', '服务态度需改进', '其他问题'];
const FEEDBACK_STATUS = { PENDING: '待受理', PROCESSING: '处理中', RESOLVED: '已处理', REJECTED: '已关闭' };
function tagsFor(rating) { return rating > 0 && rating <= 2 ? ISSUE_TAGS : POSITIVE_TAGS; }
function parseTags(tags) { return typeof tags === 'string' ? tags.split(',').filter(Boolean) : []; }
function draftKey(app, orderId) {
  const state = app.globalData || {};
  const id = state.userInfo?.id;
  return state.token && id && orderId ? `reviewDraft:${encodeURIComponent(state.baseUrl || '')}:${id}:${orderId}` : '';
}
module.exports = { POSITIVE_TAGS, ISSUE_TAGS, FEEDBACK_STATUS, tagsFor, parseTags, draftKey };
