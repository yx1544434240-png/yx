import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { ElLoading } from 'element-plus'
import { registerIcons } from './icons.js'
import { permission } from './directives/permission.js'
// 按需组件样式由 unplugin 自动引入；函数式 API 与指令需显式补齐样式
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/notification/style/css'
import './styles/theme.css'

const app = createApp(App)

registerIcons(app)

// v-loading 指令在去掉 app.use(ElementPlus) 后需单独注册
app.directive('loading', ElLoading.directive)
app.directive('permission', permission)

app.use(router)
app.mount('#app')
