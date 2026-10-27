// 只注册实际用到的图标（含侧栏/工作台以字符串名动态渲染的图标），替代全量注册
import {
  ArrowRight,
  Calendar,
  ChatLineRound,
  CircleCheck,
  Connection,
  Document,
  House,
  Location,
  Menu,
  Odometer,
  Operation,
  Plus,
  Refresh,
  Search,
  Service,
  ShoppingBag,
  SwitchButton,
  Tickets,
  Timer,
  TrendCharts,
  User,
  UserFilled
} from '@element-plus/icons-vue'

export const icons = {
  ArrowRight,
  Calendar,
  ChatLineRound,
  CircleCheck,
  Connection,
  Document,
  House,
  Location,
  Menu,
  Odometer,
  Operation,
  Plus,
  Refresh,
  Search,
  Service,
  ShoppingBag,
  SwitchButton,
  Tickets,
  Timer,
  TrendCharts,
  User,
  UserFilled
}

export function registerIcons(app) {
  for (const [name, component] of Object.entries(icons)) app.component(name, component)
}
