import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import StaffApplications from '../StaffApplications.vue'
import request from '../../utils/request.js'
vi.mock('../../utils/request.js', () => ({ default: { get: vi.fn(), post: vi.fn() } }))
const row = {
  id: '22',
  revision: 2,
  realName: '入驻新人',
  phone: '13812345678',
  serviceArea: '阳光花园社区',
  introduction: '社区服务经历',
  status: 'PENDING'
}
const global = {
  stubs: {
    ElDialog: { props: ['modelValue'], template: '<div v-if="modelValue"><slot/><slot name="footer"/></div>' },
    ElPagination: true
  }
}
const button = (wrapper, label) => wrapper.findAll('button').find(node => node.text().includes(label))
describe('staff admission review', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    request.get.mockResolvedValue({ records: [{ ...row }], total: 1 })
    request.post.mockResolvedValue(null)
  })
  it('requires a rejection reason and submits the selected application revision', async () => {
    const wrapper = mount(StaffApplications, { props: { visible: true }, global })
    await flushPromises()
    await button(wrapper, '查看申请').trigger('click')
    await flushPromises()
    await button(wrapper, '退回补充').trigger('click')
    expect(request.post).not.toHaveBeenCalled()
    await wrapper.find('textarea').setValue('请补充服务经历')
    await button(wrapper, '退回补充').trigger('click')
    await flushPromises()
    expect(request.post).toHaveBeenCalledWith('/staff/applications/audit', {
      id: '22',
      revision: 2,
      approve: false,
      reason: '请补充服务经历'
    })
    expect(wrapper.emitted('reviewed')).toHaveLength(1)
    wrapper.unmount()
  })
  it('approval includes a reviewed service station and location', async () => {
    const wrapper = mount(StaffApplications, { props: { visible: true }, global })
    await flushPromises()
    await button(wrapper, '查看申请').trigger('click')
    await flushPromises()
    await button(wrapper, '审核通过并开通').trigger('click')
    await flushPromises()
    expect(request.post.mock.calls[0][1]).toMatchObject({
      id: '22',
      revision: 2,
      approve: true,
      stationName: '阳光社区居家养老服务站',
      longitude: 116.407396,
      latitude: 39.904199
    })
    wrapper.unmount()
  })
  it('failed audit preserves the form for retry', async () => {
    request.post.mockRejectedValue(Error('offline'))
    const wrapper = mount(StaffApplications, { props: { visible: true }, global })
    await flushPromises()
    await button(wrapper, '查看申请').trigger('click')
    await flushPromises()
    await wrapper.find('textarea').setValue('补充材料')
    await button(wrapper, '退回补充').trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').element.value).toBe('补充材料')
    expect(wrapper.emitted('reviewed')).toBeUndefined()
    wrapper.unmount()
  })
})
