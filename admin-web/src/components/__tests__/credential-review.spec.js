import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CredentialReview from '../CredentialReview.vue'
import request from '../../utils/request.js'

vi.mock('../../utils/request.js', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const row = {
  id: '21',
  revision: 3,
  staffName: '张玉兰',
  skillName: '护理培训',
  certificateNo: 'C-01',
  auditStatus: 'PENDING',
  proofUrlsJson: '["/media/private/credentials/2/00000000-0000-0000-0000-000000000001.png"]'
}
const global = {
  stubs: {
    ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>' },
    PrivatePhoto: true
  }
}
const button = (wrapper, text) => wrapper.findAll('button').find(node => node.text().includes(text))
describe('credential review', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.get.mockResolvedValue({ ...row })
    request.post.mockResolvedValue(null)
  })
  it('requires a rejection reason and sends the material revision for review', async () => {
    const wrapper = mount(CredentialReview, { props: { skillId: '21' }, global })
    await flushPromises()
    expect(wrapper.text()).toContain('张玉兰')
    await button(wrapper, '退回补充').trigger('click')
    expect(request.post).not.toHaveBeenCalled()
    await wrapper.find('textarea').setValue('请补充清晰编号')
    await button(wrapper, '退回补充').trigger('click')
    await flushPromises()
    expect(request.post).toHaveBeenCalledWith('/staff/credentials/audit', {
      id: '21',
      revision: 3,
      approve: false,
      reason: '请补充清晰编号'
    })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
    wrapper.unmount()
  })
  it('failed audit keeps the materials open for retry', async () => {
    request.post.mockRejectedValue(Error('offline'))
    const wrapper = mount(CredentialReview, { props: { skillId: '21' }, global })
    await flushPromises()
    await button(wrapper, '审核通过').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(button(wrapper, '审核通过').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
  it('processed credentials have no audit buttons', async () => {
    request.get.mockResolvedValue({ ...row, auditStatus: 'APPROVED' })
    const wrapper = mount(CredentialReview, { props: { skillId: '21' }, global })
    await flushPromises()
    expect(button(wrapper, '审核通过')).toBeUndefined()
    expect(wrapper.find('textarea').exists()).toBe(false)
    wrapper.unmount()
  })
  it('switching applications ignores the late response from the previous one', async () => {
    let finish
    request.get.mockImplementationOnce(
      () =>
        new Promise(resolve => {
          finish = resolve
        })
    )
    const wrapper = mount(CredentialReview, { props: { skillId: '21' }, global })
    await wrapper.setProps({ skillId: '22' })
    await flushPromises()
    finish({ ...row, staffName: '旧申请' })
    await flushPromises()
    expect(wrapper.text()).not.toContain('旧申请')
    expect(wrapper.text()).toContain('张玉兰')
    wrapper.unmount()
  })
})
