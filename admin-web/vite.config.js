import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 第三方依赖拆成独立 chunk，便于长期缓存
function vendorChunk(id) {
  if (!id.includes('node_modules')) return undefined
  if (id.includes('@element-plus/icons-vue')) return 'vendor-element-icons'
  if (id.includes('element-plus')) return 'vendor-element-plus'
  if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
  if (/[\\/]node_modules[\\/](@vue|vue|vue-router)[\\/]/.test(id)) return 'vendor-vue'
  return undefined
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 生产构建禁止打开演示登录入口（S5）
  if (mode === 'production' && env.VITE_SHOW_DEMO === 'true')
    throw new Error('VITE_SHOW_DEMO=true is not allowed in production builds')
  return {
    plugins: [
      vue(),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        dts: 'src/auto-imports.d.ts'
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts'
      })
    ],
    build: {
      // 按需引入后 element-plus ≈543kB、echarts≈530kB（dashboard 图表已 tree-shake）；
      // 二者仍略超 500，阈值设 550 避免误报，新增超大依赖仍会告警。
      chunkSizeWarningLimit: 550,
      rollupOptions: {
        output: { manualChunks: vendorChunk }
      }
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    test: {
      environment: 'happy-dom',
      include: ['src/**/*.spec.js'],
      server: { deps: { inline: ['element-plus'] } }
    }
  }
})
