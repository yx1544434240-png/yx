import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElLoading } from 'element-plus'
import StatusTag from '../StatusTag.vue'
import MaskedText from '../MaskedText.vue'
import PageHeader from '../PageHeader.vue'
import DataTable from '../DataTable.vue'
import StaffSelect from '../StaffSelect.vue'

const global = { directives: { loading: ElLoading.directive } }

describe('StatusTag', () => {
  it('renders the shared label for a status', () => {
    const wrapper = mount(StatusTag, { props: { kind: 'complaint', value: 'RESOLVED' } })
    expect(wrapper.text()).toBe('已处理')
  })
})

describe('MaskedText', () => {
  it('masks by default and reveals on click', async () => {
    const wrapper = mount(MaskedText, { props: { value: '13812345678' } })
    expect(wrapper.find('.masked-value').text()).toBe('138****5678')
    await wrapper.find('button').trigger('click')
    expect(wrapper.find('.masked-value').text()).toBe('13812345678')
  })
  it('shows a placeholder for empty values', () => {
    expect(mount(MaskedText, { props: { value: '' } }).text()).toBe('—')
  })
})

describe('PageHeader', () => {
  it('supports a title slot and actions', () => {
    const wrapper = mount(PageHeader, {
      props: { eyebrow: '社区' },
      slots: { title: '<em>你好</em>', actions: '<button>刷新</button>' }
    })
    expect(wrapper.find('h1 em').text()).toBe('你好')
    expect(wrapper.find('.intro-actions button').exists()).toBe(true)
  })
})

describe('DataTable', () => {
  const base = { rows: [], total: 0, pageNum: 1, pageSize: 10 }
  it('distinguishes load errors from empty data and emits retry', async () => {
    const wrapper = mount(DataTable, { props: { ...base, error: true, errorText: '加载失败' }, global })
    await flushPromises()
    expect(wrapper.text()).toContain('加载失败')
    await wrapper.find('[role="alert"] button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
  it('shows the custom empty text when there is no data', async () => {
    const wrapper = mount(DataTable, { props: { ...base, emptyText: '暂无反馈' }, global })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无反馈')
    expect(wrapper.find('.el-pagination').exists()).toBe(false)
  })
})

vi.mock('../../utils/request.js', () => ({ default: { get: vi.fn() } }))
describe('PrivatePhoto', () => {
  it('rejects paths outside the private photo pattern without requesting', async () => {
    const { default: request } = await import('../../utils/request.js')
    const { default: PrivatePhoto } = await import('../PrivatePhoto.vue')
    const wrapper = mount(PrivatePhoto, { props: { source: '/etc/passwd' } })
    await flushPromises()
    expect(request.get).not.toHaveBeenCalled()
    expect(wrapper.text()).toBe('照片暂不可用')
  })
})

describe('StaffSelect', () => {
  it('merges asynchronously updated seed options and deduplicates by staff profile id', async () => {
    const wrapper = mount(StaffSelect, { props: { seed: [] } })
    expect(wrapper.vm.options).toHaveLength(0)
    await wrapper.setProps({
      seed: [
        { profile: { id: 1, serviceArea: '东区', currentStatus: 'AVAILABLE' }, user: { realName: '张阿姨' } },
        { profile: { id: 1, serviceArea: '东区', currentStatus: 'AVAILABLE' }, user: { realName: '张阿姨' } }
      ]
    })
    expect(wrapper.vm.options).toHaveLength(1)
    expect(wrapper.vm.options[0].user.realName).toBe('张阿姨')
    wrapper.unmount()
  })
})
