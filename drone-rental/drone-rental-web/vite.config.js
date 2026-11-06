import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ['vue', 'vue-router', 'pinia'],
      dts: false
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'sass' })],
      dts: false
    })
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['legacy-js-api', 'import'],
        additionalData: (source, filename) => {
          // 跳过 node_modules 和 variables.scss 自身
          if (filename.includes('node_modules') || filename.includes('variables.scss')) {
            return source
          }
          return `@import "@/assets/styles/variables.scss";\n${source}`
        }
      }
    }
  },
  preview: {
    host: '127.0.0.1', port: 15174, strictPort: true,
    proxy: { '/api': { target: 'http://127.0.0.1:18084', changeOrigin: true } }
  },
  server: {
    host: '127.0.0.1',
    port: 15174,
    strictPort: true,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18084',
        changeOrigin: true
      }
    }
  }
})
