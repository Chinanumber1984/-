import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'
export default defineConfig({
  base: './', plugins: [vue(), {
    name: 'demo-root',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        if (req.url === '/') { res.writeHead(302, { Location: '/demo.html' }); res.end(); return }
        next()
      })
    }
  }],
  server: { host: '127.0.0.1', port: 5174, strictPort: true },
  build: { outDir: '../demo/build', emptyOutDir: true, cssCodeSplit: false, rollupOptions: { input: resolve(process.cwd(), 'demo.html') } }
})
